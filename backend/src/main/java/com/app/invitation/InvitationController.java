package com.app.invitation;

import com.app.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final SecureRandom random=new SecureRandom();private final String publicBaseUrl;private final com.app.config.SystemSettingService settings;private final com.app.audit.AuditLogService audit;
    public InvitationController(JdbcTemplate jdbc,ObjectMapper mapper,@org.springframework.beans.factory.annotation.Value("${app.public-base-url}")String publicBaseUrl,com.app.config.SystemSettingService settings,com.app.audit.AuditLogService audit){this.jdbc=jdbc;this.mapper=mapper;this.publicBaseUrl=publicBaseUrl;this.settings=settings;this.audit=audit;}
    public record CreateRequest(@NotNull UUID templateVersionId,@NotBlank String title,Map<String,Object> content,LocalDate lastFunctionDate,String timezone){}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> create(@Valid @RequestBody CreateRequest r,Authentication auth){
        UUID owner=UUID.fromString(auth.getName());String tz=r.timezone()==null?settings.getString("invitation.default-timezone"):r.timezone();ZoneId.of(tz);UUID id=UUID.randomUUID();
        try{jdbc.update("INSERT INTO invitation(id,owner_user_id,template_version_id,title,content,last_function_date,timezone) VALUES (?,?,?,?,?::jsonb,?,?)",id,owner,r.templateVersionId(),r.title().trim(),mapper.writeValueAsString(r.content()==null?Map.of():r.content()),r.lastFunctionDate(),tz);}
        catch(Exception e){throw new IllegalArgumentException("Unable to create invitation. Verify the selected template and event date.");}
        return ApiResponse.success(Map.of("id",id,"status","DRAFT"));
    }
    @GetMapping public ApiResponse<?> list(Authentication auth){return ApiResponse.success(jdbc.queryForList("SELECT id,title,status,last_function_date,expires_at,created_at,updated_at FROM invitation WHERE owner_user_id=? AND status<>'DELETED' ORDER BY updated_at DESC",UUID.fromString(auth.getName())));}
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable UUID id,Authentication auth){return ApiResponse.success(jdbc.queryForMap("SELECT id,title,status,content::text AS content,template_version_id,last_function_date,timezone,expires_at,created_at,updated_at FROM invitation WHERE id=? AND owner_user_id=? AND status<>'DELETED'",id,UUID.fromString(auth.getName())));}
    @PutMapping("/{id}") public ApiResponse<?> update(@PathVariable UUID id,@RequestBody Map<String,Object> body,Authentication auth){
        String title=(String)body.get("title");Object content=body.get("content");if(title==null||title.isBlank()||title.length()>180)throw new IllegalArgumentException("A valid title is required");
        try{int n=jdbc.update("UPDATE invitation SET title=?,content=?::jsonb,template_version_id=?,last_function_date=?,timezone=?,updated_at=now(),version=version+1 WHERE id=? AND owner_user_id=? AND status IN ('DRAFT','PENDING_PAYMENT')",title.trim(),mapper.writeValueAsString(content==null?Map.of():content),UUID.fromString(String.valueOf(body.get("templateVersionId"))),body.get("lastFunctionDate")==null?null:LocalDate.parse(String.valueOf(body.get("lastFunctionDate"))),body.getOrDefault("timezone","Asia/Kolkata"),id,UUID.fromString(auth.getName()));if(n==0)throw new IllegalArgumentException("Invitation not found or cannot be edited in its current state");}
        catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Invalid invitation content");}
        return ApiResponse.success(Map.of("id",id,"status","DRAFT"));
    }
    @PostMapping("/{id}/consents")
    public ApiResponse<?> consent(@PathVariable UUID id,@RequestBody Map<String,Boolean> consents,Authentication auth,
                                  jakarta.servlet.http.HttpServletRequest request){
        UUID user=UUID.fromString(auth.getName());
        var invitation=jdbc.queryForList("SELECT version FROM invitation WHERE id=? AND owner_user_id=?",id,user);
        if(invitation.isEmpty())throw new IllegalArgumentException("Invitation not found");
        var required=jdbc.query("SELECT substring(setting_key from 17) AS consent_key FROM system_setting WHERE setting_key LIKE 'consent.required.%' AND setting_value='true'::jsonb",
            (rs,n)->rs.getString("consent_key"));
        if(required.isEmpty()||!consents.keySet().equals(new java.util.HashSet<>(required))||
           consents.values().stream().anyMatch(v->!Boolean.TRUE.equals(v)))
            throw new IllegalArgumentException("All and only the current required declarations must be accepted");
        long version=((Number)invitation.get(0).get("version")).longValue();
        String ip=request.getRemoteAddr();
        String agent=request.getHeader("User-Agent");
        for(String key:required){
            String policyType=key.equals("terms")?"TERMS":key.equals("privacy")?"PRIVACY":null;
            String consentVersion=policyType==null?"1":String.valueOf(jdbc.queryForObject("SELECT max(version_number) FROM policy_document WHERE policy_type=? AND status='PUBLISHED'",Integer.class,policyType));
            jdbc.update("INSERT INTO invitation_consent(user_id,invitation_id,consent_key,consent_version,accepted,source_ip,user_agent,content_version) VALUES (?,?,?,?,true,?,?,?) ON CONFLICT (invitation_id,content_version,consent_key,consent_version) DO UPDATE SET accepted=true,accepted_at=now(),source_ip=EXCLUDED.source_ip,user_agent=EXCLUDED.user_agent",
                user,id,key,consentVersion,ip,agent==null?null:agent.substring(0,Math.min(agent.length(),500)),version);
        }
        return ApiResponse.success(Map.of("accepted",required.size(),"contentVersion",version));
    }
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/{id}/publish") public ApiResponse<?> publish(@PathVariable UUID id,Authentication auth){
        UUID user=UUID.fromString(auth.getName());
        var rows=jdbc.query("SELECT i.title,i.content,i.last_function_date,i.timezone,i.template_version_id,i.version,o.status order_status FROM invitation i LEFT JOIN LATERAL (SELECT status FROM customer_order WHERE invitation_id=i.id ORDER BY created_at DESC LIMIT 1) o ON true WHERE i.id=? AND i.owner_user_id=?",(rs,n)->Map.of("title",rs.getString("title"),"content",rs.getString("content"),"lastFunctionDate",rs.getDate("last_function_date")==null?"":rs.getDate("last_function_date").toLocalDate().toString(),"timezone",rs.getString("timezone"),"templateVersionId",rs.getObject("template_version_id"),"version",rs.getLong("version"),"orderStatus",rs.getString("order_status")==null?"":rs.getString("order_status")),id,user);
        if(rows.isEmpty())throw new IllegalArgumentException("Invitation not found");var inv=rows.get(0);
        if(settings.getBoolean("invitation.publication.payment-required")&&!"PAID".equals(inv.get("orderStatus")))throw new IllegalArgumentException("A verified paid order is required before publication");
        if(inv.get("lastFunctionDate").toString().isBlank())throw new IllegalArgumentException("At least one function date is required");
        long consentCount=jdbc.queryForObject("SELECT count(*) FROM invitation_consent WHERE invitation_id=? AND user_id=? AND accepted=true AND content_version=?",Long.class,id,user,inv.get("version"));
        long required=jdbc.queryForObject("SELECT count(*) FROM system_setting WHERE setting_key LIKE 'consent.required.%'",Long.class);
        if(required==0||consentCount<required)throw new IllegalArgumentException("Please complete all required declarations before publishing");
        int grace=settings.getInt("invitation.expiry.grace-days");
        if(grace<0||grace>365)throw new IllegalStateException("Configured expiry grace period is invalid");
        ZoneId zone=ZoneId.of(inv.get("timezone").toString());var expiry=LocalDate.parse(inv.get("lastFunctionDate").toString()).plusDays(grace+1L).atStartOfDay(zone).toInstant();
        byte[] bytes=new byte[24];random.nextBytes(bytes);String slug=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try{
            jdbc.update("INSERT INTO invitation_publication(invitation_id,publication_number,public_slug,published_snapshot,template_version_id,expires_at) VALUES (?,COALESCE((SELECT max(publication_number)+1 FROM invitation_publication WHERE invitation_id=?),1),?,jsonb_build_object('title',?,'content',?::jsonb),?,?)",id,id,slug,inv.get("title"),inv.get("content"),inv.get("templateVersionId"),java.sql.Timestamp.from(expiry));
            long expirySettingVersion=jdbc.queryForObject("SELECT version FROM system_setting WHERE setting_key='invitation.expiry.grace-days'",Long.class);
            String expirySnapshot=mapper.writeValueAsString(Map.of("graceDays",grace,"lastFunctionDate",inv.get("lastFunctionDate"),"timezone",inv.get("timezone"),"expiresAt",expiry.toString(),"settingVersion",expirySettingVersion));
            jdbc.update("UPDATE invitation SET status='PUBLISHED',public_slug=?,expires_at=?,expiry_policy_snapshot=?::jsonb,updated_at=now() WHERE id=? AND owner_user_id=?",slug,java.sql.Timestamp.from(expiry),expirySnapshot,id,user);
        }catch(Exception e){throw new IllegalArgumentException("Unable to publish invitation");}
        audit.record(user,"INVITATION_PUBLISHED","INVITATION",id.toString(),"SUCCESS",Map.of("publicationSlug",slug,"expiresAt",expiry.toString(),"graceDays",grace));
        return ApiResponse.success(Map.of("slug",slug,"url",publicBaseUrl.replaceAll("/+$","")+"/i/"+slug,"expiresAt",expiry.toString()));
    }
}
