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
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
 private final JdbcTemplate jdbc;private final PasswordEncoder encoder;private final JwtService jwt;private final ObjectMapper mapper;
 private final SecureRandom random=new SecureRandom();private final String publicBaseUrl;
 public AuthController(JdbcTemplate jdbc,PasswordEncoder encoder,JwtService jwt,ObjectMapper mapper,@Value("${app.public-base-url}")String publicBaseUrl){
  this.jdbc=jdbc;this.encoder=encoder;this.jwt=jwt;this.mapper=mapper;this.publicBaseUrl=publicBaseUrl;
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
 public ApiResponse<?> verifyEmail(@Valid @RequestBody TokenRequest request){
  var row=findToken(request.token(),"EMAIL_VERIFY");
  jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=now(),updated_at=now() WHERE id=? AND status='PENDING_VERIFICATION'",row);
  jdbc.update("UPDATE auth_token SET consumed_at=now() WHERE user_id=? AND token_type='EMAIL_VERIFY' AND consumed_at IS NULL",row);
  return ApiResponse.success(Map.of("verified",true));
 }
 @PostMapping("/login")
 public ApiResponse<?> login(@Valid @RequestBody LoginRequest r){
  var users=jdbc.query("SELECT id,email,password_hash,role,status FROM app_user WHERE email=?",(rs,n)->Map.of("id",rs.getObject("id").toString(),"email",rs.getString("email"),"hash",rs.getString("password_hash"),"role",rs.getString("role"),"status",rs.getString("status")),r.email().trim().toLowerCase());
  if(users.isEmpty()||!encoder.matches(r.password(),(String)users.get(0).get("hash")))throw new IllegalArgumentException("Invalid email or password");
  var u=users.get(0);if(!"ACTIVE".equals(u.get("status")))throw new IllegalArgumentException("Verify your email address before signing in");
  String access=jwt.issue((String)u.get("id"),(String)u.get("email"),(String)u.get("role"));
  return ApiResponse.success(Map.of("accessToken",access,"tokenType","Bearer","user",Map.of("id",u.get("id"),"email",u.get("email"),"role",u.get("role"))));
 }
 @PostMapping("/forgot-password")
 public ApiResponse<?> forgot(@RequestBody Map<String,String> body){
  String email=body.getOrDefault("email","").trim().toLowerCase();
  var users=jdbc.query("SELECT id FROM app_user WHERE email=? AND status='ACTIVE'",(rs,n)->rs.getObject("id",UUID.class),email);
  if(!users.isEmpty()){String raw=token();saveToken(users.get(0),"PASSWORD_RESET",raw,1);enqueue(email,"PASSWORD_RESET",Map.of("token",raw,"baseUrl",publicBaseUrl));}
  return ApiResponse.success(Map.of("message","If the account exists, password reset instructions will be sent."));
 }
 @PostMapping("/reset-password")
 public ApiResponse<?> resetPassword(@Valid @RequestBody ResetRequest request){
  UUID user=findToken(request.token(),"PASSWORD_RESET");
  jdbc.update("UPDATE app_user SET password_hash=?,updated_at=now() WHERE id=? AND status='ACTIVE'",encoder.encode(request.newPassword()),user);
  jdbc.update("UPDATE auth_token SET consumed_at=now() WHERE user_id=? AND token_type='PASSWORD_RESET' AND consumed_at IS NULL",user);
  return ApiResponse.success(Map.of("passwordUpdated",true));
 }
 @GetMapping("/me")
 public ApiResponse<?> me(org.springframework.security.core.Authentication auth){
  return ApiResponse.success(jdbc.queryForMap("SELECT id,email,display_name,role,created_at FROM app_user WHERE id=? AND status='ACTIVE'",UUID.fromString(auth.getName())));
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
  var ids=jdbc.query("SELECT user_id FROM auth_token WHERE token_hash=? AND token_type=? AND consumed_at IS NULL AND expires_at>now()",(rs,n)->rs.getObject("user_id",UUID.class),hash(raw),type);
  if(ids.isEmpty())throw new IllegalArgumentException("Token is invalid or expired");return ids.get(0);
 }
 private void enqueue(String email,String template,Map<String,String> payload){
  try{jdbc.update("INSERT INTO email_outbox(recipient,template_key,payload) VALUES (?,?,?::jsonb)",email,template,mapper.writeValueAsString(payload));}
  catch(Exception e){throw new IllegalStateException("Unable to queue account email");}
 }
}
