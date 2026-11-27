package com.app.invitation;

import com.app.common.api.ApiResponse;
import com.app.config.SystemSettingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class InvitationRsvpController {
 private final JdbcTemplate jdbc;private final SystemSettingService settings;private final PublicSubmissionRateLimitService rateLimit;
 public InvitationRsvpController(JdbcTemplate jdbc,SystemSettingService settings,PublicSubmissionRateLimitService rateLimit){this.jdbc=jdbc;this.settings=settings;this.rateLimit=rateLimit;}
 public record RsvpRequest(@NotBlank @jakarta.validation.constraints.Size(max=160)String guestName,
  @Email @jakarta.validation.constraints.Size(max=320)String guestEmail,
  @NotBlank String attendance,@Min(0) @Max(20)Integer guestCount,
  @jakarta.validation.constraints.Size(max=1000)String message){}
 @PostMapping("/api/v1/public/invitations/{slug}/rsvp")
 @ResponseStatus(HttpStatus.CREATED)
 public ApiResponse<?> submit(@PathVariable String slug,@Valid @RequestBody RsvpRequest request,jakarta.servlet.http.HttpServletRequest httpRequest){
  if(!settings.getBoolean("invitation.rsvp.enabled"))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"RSVP is not enabled");
  if(!java.util.Set.of("ATTENDING","NOT_ATTENDING","MAYBE").contains(request.attendance()))throw new IllegalArgumentException("Invalid RSVP response");
  var rows=jdbc.query("SELECT p.id,p.invitation_id FROM invitation_publication p WHERE p.public_slug=? AND p.revoked_at IS NULL AND p.expires_at>now()",
   (rs,n)->Map.of("publicationId",rs.getObject("id",UUID.class),"invitationId",rs.getObject("invitation_id",UUID.class)),slug);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.GONE,"Invitation is expired or unavailable");
  var p=rows.get(0);rateLimit.assertAllowed((UUID)p.get("publicationId"),httpRequest.getRemoteAddr());UUID id=UUID.randomUUID();
  jdbc.update("INSERT INTO invitation_rsvp(id,invitation_id,publication_id,guest_name,guest_email,attendance,guest_count,message) VALUES (?,?,?,?,?,?,?,?)",
   id,p.get("invitationId"),p.get("publicationId"),request.guestName().trim(),request.guestEmail(),request.attendance(),request.guestCount()==null?1:request.guestCount(),request.message());
  return ApiResponse.success(Map.of("rsvpId",id,"received",true));
 }
 @GetMapping("/api/v1/invitations/{id}/rsvps")
 public ApiResponse<?> list(@PathVariable("id") UUID id,Authentication auth){
  jdbc.queryForObject("SELECT id FROM invitation WHERE id=? AND owner_user_id=?",UUID.class,id,UUID.fromString(auth.getName()));
  return ApiResponse.success(jdbc.queryForList("SELECT id,guest_name,guest_email,attendance,guest_count,message,created_at FROM invitation_rsvp WHERE invitation_id=? ORDER BY created_at DESC",id));
 }

 @GetMapping(value="/api/v1/invitations/{id}/rsvps/export",produces="text/csv")
 public org.springframework.http.ResponseEntity<byte[]> export(@PathVariable("id") UUID id,Authentication auth){
  jdbc.queryForObject("SELECT id FROM invitation WHERE id=? AND owner_user_id=?",UUID.class,id,UUID.fromString(auth.getName()));
  var rows=jdbc.queryForList("SELECT guest_name,guest_email,attendance,guest_count,message,created_at FROM invitation_rsvp WHERE invitation_id=? ORDER BY created_at DESC",id);
  StringBuilder csv=new StringBuilder("Guest name,Guest email,Attendance,Guest count,Message,Submitted at\r\n");
  for(var row:rows){
   csv.append(csv(row.get("guest_name"))).append(',')
      .append(csv(row.get("guest_email"))).append(',')
      .append(csv(row.get("attendance"))).append(',')
      .append(csv(row.get("guest_count"))).append(',')
      .append(csv(row.get("message"))).append(',')
      .append(csv(row.get("created_at"))).append("\r\n");
  }
  return org.springframework.http.ResponseEntity.ok()
    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=invitation-rsvps.csv")
    .header(org.springframework.http.HttpHeaders.CACHE_CONTROL,"private, no-store")
    .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
    .body(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }

 private static String csv(Object value){
  String text=value==null?"":String.valueOf(value);
  if(!text.isEmpty()&&text.stripLeading().matches("^[=+@-].*"))text="'"+text;
  return "\"" + text.replace("\"", "\"\"") + "\"";
 }
}
