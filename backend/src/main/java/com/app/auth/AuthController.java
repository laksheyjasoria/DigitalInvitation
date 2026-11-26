package com.app.auth;

import com.app.common.api.ApiResponse;
import com.app.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
 private final JdbcTemplate jdbc;private final PasswordEncoder encoder;private final JwtService jwt;private final ObjectMapper mapper;
 private final AuthRateLimitService rateLimit;private final SecureRandom random=new SecureRandom();
 private final String publicBaseUrl;private final long refreshTokenDays;private final boolean secureRefreshCookie;
 public AuthController(JdbcTemplate jdbc,PasswordEncoder encoder,JwtService jwt,ObjectMapper mapper,AuthRateLimitService rateLimit,
   @Value("${app.public-base-url}")String publicBaseUrl,
   @Value("${app.security.jwt.refresh-token-days:30}")long refreshTokenDays,
   @Value("${app.security.refresh-cookie-secure:true}")boolean secureRefreshCookie){
  this.jdbc=jdbc;this.encoder=encoder;this.jwt=jwt;this.mapper=mapper;this.rateLimit=rateLimit;
  this.publicBaseUrl=publicBaseUrl;this.refreshTokenDays=refreshTokenDays;this.secureRefreshCookie=secureRefreshCookie;
 }
 public record RegisterRequest(@Email @NotBlank String email,@NotBlank @Size(min=12,max=72) String password,@NotBlank @Size(max=120) String displayName){}
 public record LoginRequest(@Email @NotBlank String email,@NotBlank String password){}
 public record TokenRequest(@NotBlank String token){}
 public record ResetRequest(@NotBlank String token,@NotBlank @Size(min=12,max=72) String newPassword){}

 @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
 public ApiResponse<?> register(@Valid @RequestBody RegisterRequest r){
  UUID id=UUID.randomUUID();String email=r.email().trim().toLowerCase();
  try{jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name,status) VALUES (?,?,?,?, 'PENDING_VERIFICATION')",id,email,encoder.encode(r.password()),r.displayName().trim());}
  catch(DuplicateKeyException e){throw new IllegalArgumentException("An account with this email already exists");}
  String raw=token();saveToken(id,"EMAIL_VERIFY",raw,24);
  enqueue(email,"EMAIL_VERIFY",Map.of("token",raw,"baseUrl",publicBaseUrl));
  return ApiResponse.success(Map.of("userId",id,"email",email,"status","EMAIL_VERIFICATION_REQUIRED"));
 }

 @PostMapping("/verify-email")
 @Transactional
 public ApiResponse<?> verifyEmail(@Valid @RequestBody TokenRequest request){
  UUID user=findToken(request.token(),"EMAIL_VERIFY");
  int changed=jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=now(),updated_at=now() WHERE id=? AND status='PENDING_VERIFICATION'",user);
  if(changed!=1)throw new IllegalArgumentException("Email verification is no longer valid");
  jdbc.update("UPDATE auth_token SET consumed_at=now() WHERE user_id=? AND token_type='EMAIL_VERIFY' AND consumed_at IS NULL",user);
  return ApiResponse.success(Map.of("verified",true));
 }

 @PostMapping("/login")
 public ResponseEntity<ApiResponse<?>> login(@Valid @RequestBody LoginRequest r,jakarta.servlet.http.HttpServletRequest request){
  String email=r.email().trim().toLowerCase(java.util.Locale.ROOT);String remoteAddress=request.getRemoteAddr();
  rateLimit.assertAllowed(email,remoteAddress);
  var users=jdbc.query("SELECT id,email,password_hash,role,status FROM app_user WHERE email=?",
   (rs,n)->Map.of("id",rs.getObject("id").toString(),"email",rs.getString("email"),"hash",rs.getString("password_hash"),"role",rs.getString("role"),"status",rs.getString("status")),email);
  if(users.isEmpty()||!encoder.matches(r.password(),(String)users.get(0).get("hash"))||!"ACTIVE".equals(users.get(0).get("status"))){
   rateLimit.recordFailure(email,remoteAddress);
   throw new IllegalArgumentException("Invalid email or password, or account is not active");
  }
  rateLimit.clear(email,remoteAddress);
  var user=users.get(0);String access=jwt.issue((String)user.get("id"),(String)user.get("email"),(String)user.get("role"));
  String refresh=token();createRefreshSession(UUID.fromString((String)user.get("id")),refresh,request);
  ApiResponse<?> body=ApiResponse.success(Map.of("accessToken",access,"tokenType","Bearer",
   "user",Map.of("id",user.get("id"),"email",user.get("email"),"role",user.get("role"))));
  return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,refreshCookie(refresh).toString()).body(body);
 }

 @PostMapping("/refresh")
 @Transactional
 public ResponseEntity<ApiResponse<?>> refresh(@CookieValue(name="di_refresh",required=false)String raw,
    jakarta.servlet.http.HttpServletRequest request){
  if(raw==null||raw.isBlank())return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(HttpHeaders.SET_COOKIE,clearRefreshCookie().toString()).body(ApiResponse.failure("UNAUTHORIZED","Refresh session is unavailable"));
  var rows=jdbc.query("SELECT s.id,s.user_id,s.revoked_at,s.expires_at,u.email,u.role,u.status " +
    "FROM auth_refresh_session s JOIN app_user u ON u.id=s.user_id WHERE s.token_hash=? FOR UPDATE OF s",
    (rs,n)->Map.of("sessionId",rs.getObject("id",UUID.class),"userId",rs.getObject("user_id",UUID.class),
      "revoked",rs.getTimestamp("revoked_at")!=null,"expiresAt",rs.getTimestamp("expires_at").toInstant(),
      "email",rs.getString("email"),"role",rs.getString("role"),"status",rs.getString("status")),hash(raw));
  if(rows.isEmpty())return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(HttpHeaders.SET_COOKIE,clearRefreshCookie().toString()).body(ApiResponse.failure("UNAUTHORIZED","Refresh session is invalid"));
  var session=rows.get(0);UUID userId=(UUID)session.get("userId");
  if(Boolean.TRUE.equals(session.get("revoked"))||!((Instant)session.get("expiresAt")).isAfter(Instant.now())||!"ACTIVE".equals(session.get("status"))){
   jdbc.update("UPDATE auth_refresh_session SET revoked_at=COALESCE(revoked_at,now()) WHERE user_id=? AND revoked_at IS NULL",userId);
   return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(HttpHeaders.SET_COOKIE,clearRefreshCookie().toString()).body(ApiResponse.failure("UNAUTHORIZED","Refresh session expired or was already used"));
  }
  String next=token();UUID nextId=UUID.randomUUID();
  jdbc.update("UPDATE auth_refresh_session SET revoked_at=now(),replaced_by=? WHERE id=?",nextId,session.get("sessionId"));
  insertRefreshSession(nextId,userId,next,request);
  String access=jwt.issue(userId.toString(),(String)session.get("email"),(String)session.get("role"));
  return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,refreshCookie(next).toString())
   .body(ApiResponse.success(Map.of("accessToken",access,"tokenType","Bearer")));
 }

 @PostMapping("/logout")
 @Transactional
 public ResponseEntity<ApiResponse<?>> logout(@CookieValue(name="di_refresh",required=false)String raw){
  if(raw!=null&&!raw.isBlank())jdbc.update("UPDATE auth_refresh_session SET revoked_at=COALESCE(revoked_at,now()) WHERE token_hash=?",hash(raw));
  return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,clearRefreshCookie().toString())
   .body(ApiResponse.success(Map.of("loggedOut",true)));
 }

 @PostMapping("/forgot-password")
 public ApiResponse<?> forgot(@RequestBody Map<String,String> body){
  String email=body.getOrDefault("email","").trim().toLowerCase();
  var users=jdbc.query("SELECT id FROM app_user WHERE email=? AND status='ACTIVE'",(rs,n)->rs.getObject("id",UUID.class),email);
  if(!users.isEmpty()){String raw=token();saveToken(users.get(0),"PASSWORD_RESET",raw,1);enqueue(email,"PASSWORD_RESET",Map.of("token",raw,"baseUrl",publicBaseUrl));}
  return ApiResponse.success(Map.of("message","If the account exists, password reset instructions will be sent."));
 }

 @PostMapping("/reset-password")
 @Transactional
 public ApiResponse<?> resetPassword(@Valid @RequestBody ResetRequest request){
  UUID user=findToken(request.token(),"PASSWORD_RESET");
  int changed=jdbc.update("UPDATE app_user SET password_hash=?,updated_at=now() WHERE id=? AND status='ACTIVE'",encoder.encode(request.newPassword()),user);
  if(changed!=1)throw new IllegalArgumentException("Password reset is no longer valid");
  jdbc.update("UPDATE auth_token SET consumed_at=now() WHERE user_id=? AND token_type='PASSWORD_RESET' AND consumed_at IS NULL",user);
  jdbc.update("UPDATE auth_refresh_session SET revoked_at=COALESCE(revoked_at,now()) WHERE user_id=? AND revoked_at IS NULL",user);
  return ApiResponse.success(Map.of("passwordUpdated",true,"sessionsRevoked",true));
 }

 @GetMapping("/me")
 public ApiResponse<?> me(org.springframework.security.core.Authentication auth){
  return ApiResponse.success(jdbc.queryForMap("SELECT id,email,display_name,role,created_at FROM app_user WHERE id=? AND status='ACTIVE'",UUID.fromString(auth.getName())));
 }

 private void createRefreshSession(UUID user,String raw,jakarta.servlet.http.HttpServletRequest request){
  insertRefreshSession(UUID.randomUUID(),user,raw,request);
 }
 private void insertRefreshSession(UUID id,UUID user,String raw,jakarta.servlet.http.HttpServletRequest request){
  jdbc.update("INSERT INTO auth_refresh_session(id,user_id,token_hash,expires_at,user_agent,ip_address) VALUES (?,?,?,?,?,?::inet)",
   id,user,hash(raw),java.sql.Timestamp.from(Instant.now().plus(Duration.ofDays(refreshTokenDays))),
   request.getHeader("User-Agent")==null?null:request.getHeader("User-Agent").substring(0,Math.min(500,request.getHeader("User-Agent").length())),
   request.getRemoteAddr());
 }
 private ResponseCookie refreshCookie(String value){
  return ResponseCookie.from("di_refresh",value).httpOnly(true).secure(secureRefreshCookie).sameSite("Strict")
   .path("/api/v1/auth").maxAge(Duration.ofDays(refreshTokenDays)).build();
 }
 private ResponseCookie clearRefreshCookie(){
  return ResponseCookie.from("di_refresh","").httpOnly(true).secure(secureRefreshCookie).sameSite("Strict")
   .path("/api/v1/auth").maxAge(Duration.ZERO).build();
 }
 private String token(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
 private String hash(String value){
  try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
  catch(Exception e){throw new IllegalStateException("Token hashing unavailable",e);}
 }
 private void saveToken(UUID user,String type,String raw,int hours){
  jdbc.update("INSERT INTO auth_token(user_id,token_hash,token_type,expires_at) VALUES (?,?,?,?)",user,hash(raw),type,java.sql.Timestamp.from(Instant.now().plusSeconds(hours*3600L)));
 }
 private UUID findToken(String raw,String type){
  var ids=jdbc.query("SELECT user_id FROM auth_token WHERE token_hash=? AND token_type=? AND consumed_at IS NULL AND expires_at>now() FOR UPDATE",
   (rs,n)->rs.getObject("user_id",UUID.class),hash(raw),type);
  if(ids.isEmpty())throw new IllegalArgumentException("Token is invalid or expired");return ids.get(0);
 }
 private void enqueue(String email,String template,Map<String,String> payload){
  try{jdbc.update("INSERT INTO email_outbox(recipient,template_key,payload) VALUES (?,?,?::jsonb)",email,template,mapper.writeValueAsString(payload));}
  catch(Exception e){throw new IllegalStateException("Unable to queue account email");}
 }
}
