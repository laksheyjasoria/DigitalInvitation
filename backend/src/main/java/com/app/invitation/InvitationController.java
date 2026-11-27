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
    private final JdbcTemplate jdbc;private final ObjectMapper mapper;private final SecureRandom random=new SecureRandom();private final String publicBaseUrl;private final com.app.config.SystemSettingService settings;private final com.app.audit.AuditLogService audit;private final InvitationExpiryCalculator expiryCalculator;
    public InvitationController(JdbcTemplate jdbc,ObjectMapper mapper,@org.springframework.beans.factory.annotation.Value("${app.public-base-url}")String publicBaseUrl,com.app.config.SystemSettingService settings,com.app.audit.AuditLogService audit,InvitationExpiryCalculator expiryCalculator){this.jdbc=jdbc;this.mapper=mapper;this.publicBaseUrl=publicBaseUrl;this.settings=settings;this.audit=audit;this.expiryCalculator=expiryCalculator;}
    public record CreateRequest(@NotNull UUID templateVersionId,@NotBlank String title,Map<String,Object> content,LocalDate lastFunctionDate,String timezone){}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> create(@Valid @RequestBody CreateRequest r,Authentication auth){
        UUID owner=UUID.fromString(auth.getName());String tz=r.timezone()==null?settings.getString("invitation.default-timezone"):r.timezone();validateTimezone(tz);
        Integer usableTemplate=jdbc.queryForObject("SELECT count(*) FROM invitation_template_version v JOIN invitation_template t ON t.id=v.template_id WHERE v.id=? AND t.status='PUBLISHED' AND v.version_number=t.current_version",Integer.class,r.templateVersionId());
        if(usableTemplate==null||usableTemplate!=1)throw new IllegalArgumentException("Select a currently published invitation template");
        validateTemplateContent(r.templateVersionId(),r.content()==null?Map.of():r.content());
        validateLocationUrl(r.content(),false);
        UUID id=UUID.randomUUID();
        try{jdbc.update("INSERT INTO invitation(id,owner_user_id,template_version_id,title,content,last_function_date,timezone) VALUES (?,?,?,?,?::jsonb,?,?)",id,owner,r.templateVersionId(),r.title().trim(),mapper.writeValueAsString(r.content()==null?Map.of():r.content()),r.lastFunctionDate(),tz);}
        catch(Exception e){throw new IllegalArgumentException("Unable to create invitation. Verify the selected template and event date.");}
        return ApiResponse.success(Map.of("id",id,"status","DRAFT"));
    }
    @GetMapping public ApiResponse<?> list(Authentication auth){return ApiResponse.success(jdbc.queryForList("SELECT id,title,status,last_function_date,expires_at,created_at,updated_at FROM invitation WHERE owner_user_id=? AND status<>'DELETED' ORDER BY updated_at DESC",UUID.fromString(auth.getName())));}
    @GetMapping("/{id}") public ApiResponse<?> get(@PathVariable("id") UUID id,Authentication auth){
        var rows=jdbc.queryForList("SELECT i.id,i.title,i.status,i.content::text AS content,i.template_version_id,i.last_function_date,i.timezone,i.public_slug,i.expires_at,i.created_at,i.updated_at,o.id order_id,o.status order_status FROM invitation i LEFT JOIN LATERAL (SELECT id,status FROM customer_order WHERE invitation_id=i.id ORDER BY created_at DESC LIMIT 1) o ON true WHERE i.id=? AND i.owner_user_id=? AND i.status<>'DELETED'",id,UUID.fromString(auth.getName()));
        if(rows.isEmpty())throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Invitation not found");
        return ApiResponse.success(rows.get(0));
    }
    @PutMapping("/{id}") @org.springframework.transaction.annotation.Transactional
    public ApiResponse<?> update(@PathVariable("id") UUID id,@RequestBody Map<String,Object> body,Authentication auth){
        String title=(String)body.get("title");Object content=body.get("content");if(title==null||title.isBlank()||title.length()>180)throw new IllegalArgumentException("A valid title is required");
        UUID owner=UUID.fromString(auth.getName());
        String requestedTimezone=String.valueOf(body.getOrDefault("timezone",settings.getString("invitation.default-timezone")));
        validateTimezone(requestedTimezone);
        UUID requestedTemplate=UUID.fromString(String.valueOf(body.get("templateVersionId")));
        Boolean sameTemplate=jdbc.query("SELECT template_version_id=? FROM invitation WHERE id=? AND owner_user_id=?",rs->rs.next()?rs.getBoolean(1):null,requestedTemplate,id,owner);
        Integer usableTemplate=jdbc.queryForObject("SELECT count(*) FROM invitation_template_version v JOIN invitation_template t ON t.id=v.template_id WHERE v.id=? AND t.status='PUBLISHED' AND v.version_number=t.current_version",Integer.class,requestedTemplate);
        if(!Boolean.TRUE.equals(sameTemplate)&&(usableTemplate==null||usableTemplate!=1))throw new IllegalArgumentException("Select a currently published invitation template");
        validateTemplateContent(requestedTemplate,content==null?Map.of():content);
        validateLocationUrl(content,false);
        try{int n=jdbc.update("UPDATE invitation SET title=?,content=?::jsonb,template_version_id=?,last_function_date=?,timezone=?,status='DRAFT',updated_at=now(),version=version+1 WHERE id=? AND owner_user_id=? AND (status='DRAFT' OR (status='PENDING_PAYMENT' AND NOT EXISTS (SELECT 1 FROM customer_order o WHERE o.invitation_id=invitation.id AND o.status IN ('CREATED','PAYMENT_PENDING','PAID'))))",title.trim(),mapper.writeValueAsString(content==null?Map.of():content),requestedTemplate,body.get("lastFunctionDate")==null?null:LocalDate.parse(String.valueOf(body.get("lastFunctionDate"))),requestedTimezone,id,owner);if(n==0)throw new IllegalArgumentException("Invitation not found or cannot be edited in its current state");}
        catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Invalid invitation content");}
        return ApiResponse.success(Map.of("id",id,"status","DRAFT"));
    }
    @PostMapping("/{id}/consents") @org.springframework.transaction.annotation.Transactional
    public ApiResponse<?> consent(@PathVariable("id") UUID id,@RequestBody Map<String,Boolean> consents,Authentication auth,
                                  jakarta.servlet.http.HttpServletRequest request){
        UUID user=UUID.fromString(auth.getName());
        var invitation=jdbc.queryForList("SELECT version FROM invitation WHERE id=? AND owner_user_id=? AND status IN ('DRAFT','PENDING_PAYMENT')",id,user);
        if(invitation.isEmpty())throw new IllegalArgumentException("Invitation not found");
        var required=jdbc.query("SELECT substring(setting_key from 18) AS consent_key FROM system_setting WHERE setting_key LIKE 'consent.required.%' AND setting_value='true'::jsonb",
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
            jdbc.update("INSERT INTO invitation_consent(user_id,invitation_id,consent_key,consent_version,accepted,source_ip,user_agent,content_version) VALUES (?,?,?,?,true,?::inet,?,?) ON CONFLICT (invitation_id,content_version,consent_key,consent_version) DO UPDATE SET accepted=true,accepted_at=now(),source_ip=EXCLUDED.source_ip,user_agent=EXCLUDED.user_agent",
                user,id,key,consentVersion,ip,agent==null?null:agent.substring(0,Math.min(agent.length(),500)),version);
        }
        return ApiResponse.success(Map.of("accepted",required.size(),"contentVersion",version));
    }
    @PostMapping("/{id}/publish") @org.springframework.transaction.annotation.Transactional
    public ApiResponse<?> publish(@PathVariable("id") UUID id,Authentication auth){
        UUID user=UUID.fromString(auth.getName());
        var rows=jdbc.query("SELECT i.title,i.content,i.last_function_date,i.timezone,i.template_version_id,i.version,i.status,i.public_slug,i.expires_at,o.status order_status,o.pricing_snapshot::text order_snapshot FROM invitation i LEFT JOIN LATERAL (SELECT status,pricing_snapshot FROM customer_order WHERE invitation_id=i.id ORDER BY created_at DESC LIMIT 1) o ON true WHERE i.id=? AND i.owner_user_id=? FOR UPDATE OF i",
            (rs,n)->{
                Map<String,Object> m=new java.util.HashMap<>();
                m.put("title",rs.getString("title"));m.put("content",rs.getString("content"));
                m.put("lastFunctionDate",rs.getDate("last_function_date")==null?"":rs.getDate("last_function_date").toLocalDate().toString());
                m.put("timezone",rs.getString("timezone"));m.put("templateVersionId",rs.getObject("template_version_id"));
                m.put("version",rs.getLong("version"));m.put("status",rs.getString("status"));
                m.put("publicSlug",rs.getString("public_slug")==null?"":rs.getString("public_slug"));
                m.put("orderStatus",rs.getString("order_status")==null?"":rs.getString("order_status"));
                m.put("orderSnapshot",rs.getString("order_snapshot")==null?"":rs.getString("order_snapshot"));
                m.put("expiresAt",rs.getTimestamp("expires_at")==null?"":rs.getTimestamp("expires_at").toInstant().toString());
                return m;
            },id,user);
        if(rows.isEmpty())throw new IllegalArgumentException("Invitation not found");
        var inv=rows.get(0);
        validateTemplateContent((UUID)inv.get("templateVersionId"),inv.get("content"));
        validateLocationUrl(inv.get("content"),true);
        if("PUBLISHED".equals(inv.get("status"))&&!inv.get("publicSlug").toString().isBlank())
            return ApiResponse.success(Map.of("slug",inv.get("publicSlug"),"url",publicBaseUrl.replaceAll("/+$","")+"/i/"+inv.get("publicSlug"),"expiresAt",inv.get("expiresAt")));
        if("EXPIRED".equals(inv.get("status"))||"SUSPENDED".equals(inv.get("status"))||"DELETED".equals(inv.get("status")))
            throw new IllegalArgumentException("Invitation cannot be published in its current state");
        if(inv.get("lastFunctionDate").toString().isBlank())throw new IllegalArgumentException("At least one function date is required");
        int grace;long expirySettingVersion;String agreedDate=inv.get("lastFunctionDate").toString();String agreedTimezone=inv.get("timezone").toString();
        boolean administrator=auth!=null&&auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")||a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        boolean paymentRequired=settings.getBoolean("invitation.publication.payment-required")&&!administrator;
        java.util.List<String> requiredConsentKeys=new java.util.ArrayList<>();
        java.util.Map<String,String> consentVersions=new java.util.HashMap<>();
        if(!inv.get("orderSnapshot").toString().isBlank()){
            try{
                var snapshot=mapper.readTree(inv.get("orderSnapshot").toString());
                var policy=snapshot.path("expiryPolicy");
                if(policy.isMissingNode())throw new IllegalStateException("Order is missing its expiry policy snapshot");
                paymentRequired=policy.path("paymentRequired").asBoolean(paymentRequired);
                grace=policy.path("graceDays").asInt(-1);
                expirySettingVersion=policy.path("settingVersion").asLong(-1);
                String orderDate=policy.path("lastFunctionDate").asText("");
                String orderTimezone=policy.path("timezone").asText("");
                if(!orderDate.equals(agreedDate)||!orderTimezone.equals(agreedTimezone))
                    throw new IllegalStateException("Invitation event date or timezone differs from the accepted order snapshot");
                for(var node:snapshot.path("requiredConsents"))requiredConsentKeys.add(node.asText());
                var consentFields=snapshot.path("consentVersions").fields();
                while(consentFields.hasNext()){var entry=consentFields.next();consentVersions.put(entry.getKey(),entry.getValue().asText());}
            }catch(IllegalStateException e){throw e;}catch(Exception e){throw new IllegalStateException("Unable to read the agreed order policy snapshot",e);}
        }else{
            grace=settings.getInt("invitation.expiry.grace-days");
            expirySettingVersion=jdbc.queryForObject("SELECT version FROM system_setting WHERE setting_key='invitation.expiry.grace-days'",Long.class);
            requiredConsentKeys=jdbc.query("SELECT substring(setting_key from 18) FROM system_setting WHERE setting_key LIKE 'consent.required.%' AND setting_value='true'::jsonb",(rs,n)->rs.getString(1));
            var accepted=jdbc.query("SELECT consent_key,consent_version FROM invitation_consent WHERE invitation_id=? AND user_id=? AND content_version=? AND accepted=true",
                (rs,n)->Map.of("key",rs.getString("consent_key"),"version",rs.getString("consent_version")),id,user,inv.get("version"));
            accepted.forEach(row->consentVersions.put((String)row.get("key"),(String)row.get("version")));
            for(String key:requiredConsentKeys){
                if(key.equals("terms")||key.equals("privacy")){
                    String type=key.equals("terms")?"TERMS":"PRIVACY";
                    String current=String.valueOf(jdbc.queryForObject("SELECT max(version_number) FROM policy_document WHERE policy_type=? AND status='PUBLISHED'",Integer.class,type));
                    if(!current.equals(consentVersions.get(key)))throw new IllegalArgumentException("Please review and accept the latest "+type+" policy");
                }
            }
        }
        consentVersions.keySet().retainAll(new java.util.HashSet<>(requiredConsentKeys));
        if(requiredConsentKeys.isEmpty()||!consentVersions.keySet().equals(new java.util.HashSet<>(requiredConsentKeys)))
            throw new IllegalArgumentException("All required declarations must be accepted for this purchase");
        long validConsentCount=0;
        for(String key:requiredConsentKeys){
            String acceptedVersion=consentVersions.get(key);
            if(acceptedVersion==null)continue;
            validConsentCount+=jdbc.queryForObject("SELECT count(*) FROM invitation_consent WHERE invitation_id=? AND user_id=? AND content_version=? AND consent_key=? AND consent_version=? AND accepted=true",
                Long.class,id,user,inv.get("version"),key,acceptedVersion);
        }
        if(validConsentCount!=requiredConsentKeys.size())
            throw new IllegalArgumentException("Consent does not match the invitation content and policy versions");
        if(paymentRequired&&!"PAID".equals(inv.get("orderStatus")))throw new IllegalArgumentException("A verified paid order is required before publication");
        if(grace<0||grace>365)throw new IllegalStateException("Agreed expiry grace period is invalid");
        ZoneId zone=ZoneId.of(agreedTimezone);
        var expiry=expiryCalculator.calculate(LocalDate.parse(agreedDate),grace,zone);
        byte[] bytes=new byte[24];random.nextBytes(bytes);String slug=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try{
            String expirySnapshot=mapper.writeValueAsString(Map.of("graceDays",grace,"lastFunctionDate",agreedDate,"timezone",agreedTimezone,"expiresAt",expiry.toString(),"settingVersion",expirySettingVersion));
            jdbc.update("INSERT INTO invitation_publication(invitation_id,publication_number,public_slug,published_snapshot,template_version_id,expires_at) VALUES (?,COALESCE((SELECT max(publication_number)+1 FROM invitation_publication WHERE invitation_id=?),1),?,jsonb_build_object('title',?,'content',?::jsonb,'expiryPolicy',?::jsonb,'design',(SELECT definition->'design' FROM invitation_template_version WHERE id=?),'layout',(SELECT definition->'layout' FROM invitation_template_version WHERE id=?)), ?,?)",
                id,id,slug,inv.get("title"),inv.get("content"),expirySnapshot,inv.get("templateVersionId"),inv.get("templateVersionId"),inv.get("templateVersionId"),java.sql.Timestamp.from(expiry));
            jdbc.update("UPDATE invitation SET status='PUBLISHED',public_slug=?,expires_at=?,expiry_policy_snapshot=?::jsonb,updated_at=now() WHERE id=? AND owner_user_id=?",
                slug,java.sql.Timestamp.from(expiry),expirySnapshot,id,user);
        }catch(Exception e){throw new IllegalArgumentException("Unable to publish invitation");}
        audit.record(user,"INVITATION_PUBLISHED","INVITATION",id.toString(),"SUCCESS",Map.of("publicationSlug",slug,"expiresAt",expiry.toString(),"graceDays",grace));
        return ApiResponse.success(Map.of("slug",slug,"url",publicBaseUrl.replaceAll("/+$","")+"/i/"+slug,"expiresAt",expiry.toString()));
    }

    private void validateLocationUrl(Object rawContent,boolean required){
        try{
            com.fasterxml.jackson.databind.JsonNode content=rawContent instanceof String text?mapper.readTree(text):mapper.valueToTree(rawContent);
            com.app.invitation.InvitationLocationUrl.validate(content.path("locationUrl").asText(""),required);
        }catch(IllegalArgumentException e){throw e;}
        catch(Exception e){throw new IllegalArgumentException("Event location URL is invalid");}
    }

    private void validateTemplateContent(UUID templateVersionId,Object rawContent){
        try{
            String definition=jdbc.queryForObject("SELECT definition::text FROM invitation_template_version WHERE id=?",String.class,templateVersionId);
            com.fasterxml.jackson.databind.JsonNode template=mapper.readTree(definition);
            com.fasterxml.jackson.databind.JsonNode content=rawContent instanceof String value?mapper.readTree(value):mapper.valueToTree(rawContent==null?Map.of():rawContent);
            if(!content.isObject())throw new IllegalArgumentException("Invitation content must be an object");
            com.fasterxml.jackson.databind.JsonNode savedSections=content.path("sections");
            if(!savedSections.isArray())throw new IllegalArgumentException("Invitation sections must be an array");

            java.util.Map<String,com.fasterxml.jackson.databind.JsonNode> savedByKey=new java.util.LinkedHashMap<>();
            for(com.fasterxml.jackson.databind.JsonNode saved:savedSections){
                if(!saved.isObject())throw new IllegalArgumentException("Invalid invitation section");
                String key=saved.path("key").asText("");
                if(key.isBlank()||savedByKey.putIfAbsent(key,saved)!=null)
                    throw new IllegalArgumentException("Invitation contains a missing or duplicate section");
            }

            java.util.Set<String> knownSections=new java.util.HashSet<>();
            for(com.fasterxml.jackson.databind.JsonNode section:template.path("sections")){
                String key=section.path("key").asText("");
                if(key.isBlank())continue;
                knownSections.add(key);
                com.fasterxml.jackson.databind.JsonNode saved=savedByKey.get(key);
                boolean requiredSection=section.path("required").asBoolean(false);
                boolean enabled=saved==null?section.path("enabled").asBoolean(true):saved.path("enabled").asBoolean(section.path("enabled").asBoolean(true));
                if(requiredSection&&!enabled)throw new IllegalArgumentException("This template requires the section: "+section.path("label").asText(key));

                java.util.Set<String> requiredFields=new java.util.LinkedHashSet<>();
                for(com.fasterxml.jackson.databind.JsonNode required:section.path("requiredFields")){
                    if(required.isTextual()&&!required.asText().isBlank())requiredFields.add(required.asText());
                }
                java.util.Map<String,Boolean> allowedFields=new java.util.LinkedHashMap<>();
                for(com.fasterxml.jackson.databind.JsonNode field:section.path("fields")){
                    String fieldKey=field.isTextual()?field.asText():field.path("key").asText("");
                    if(fieldKey.isBlank())continue;
                    boolean required=requiredFields.contains(fieldKey)||(field.isObject()&&field.path("required").asBoolean(false));
                    boolean optional=field.isObject()&&field.has("optional")?field.path("optional").asBoolean(false):!required;
                    if(required&&optional)throw new IllegalArgumentException("Template field '"+fieldKey+"' cannot be both required and optional");
                    allowedFields.put(fieldKey,optional);
                    if(required)requiredFields.add(fieldKey);
                }

                java.util.Set<String> hiddenFields=new java.util.HashSet<>();
                com.fasterxml.jackson.databind.JsonNode hidden=saved==null?null:saved.path("hiddenFields");
                if(hidden!=null&&!hidden.isMissingNode()&&!hidden.isNull()){
                    if(!hidden.isArray())throw new IllegalArgumentException("Hidden fields must be an array");
                    for(com.fasterxml.jackson.databind.JsonNode hiddenField:hidden){
                        String fieldKey=hiddenField.asText("");
                        if(!allowedFields.containsKey(fieldKey))throw new IllegalArgumentException("Unknown field '"+fieldKey+"' in "+section.path("label").asText(key));
                        if(!Boolean.TRUE.equals(allowedFields.get(fieldKey))||requiredFields.contains(fieldKey))
                            throw new IllegalArgumentException("This template does not allow hiding field '"+fieldKey+"'");
                        hiddenFields.add(fieldKey);
                    }
                }

                boolean repeatable=section.path("repeatable").asBoolean(false);
                com.fasterxml.jackson.databind.JsonNode values=saved==null?null:saved.path("values");
                com.fasterxml.jackson.databind.JsonNode items=saved==null?null:saved.path("items");
                if(repeatable){
                    if(items!=null&&!items.isMissingNode()&&!items.isNull()&&!items.isArray())
                        throw new IllegalArgumentException("Repeatable section entries must be an array");
                    int itemCount=items==null||!items.isArray()?0:items.size();
                    if(itemCount>50)throw new IllegalArgumentException("A repeatable section can contain at most 50 entries");
                    boolean hasPopulatedItem=false;
                    for(com.fasterxml.jackson.databind.JsonNode item:items==null||!items.isArray()?mapper.createArrayNode():items){
                        if(!item.isObject())throw new IllegalArgumentException("Each repeatable section entry must be an object");
                        validateAllowedFieldKeys(item,allowedFields,key);
                        boolean populated=item.fields().hasNext();
                        if(!populated)continue;
                        hasPopulatedItem=true;
                        if(enabled)for(String fieldKey:requiredFields){
                            if(!hiddenFields.contains(fieldKey)&&!hasText(item.path(fieldKey)))
                                throw new IllegalArgumentException("Complete required field '"+fieldKey+"' in every "+section.path("label").asText(key)+" entry");
                        }
                    }
                    if(requiredSection&&!hasPopulatedItem)
                        throw new IllegalArgumentException("Add at least one entry to "+section.path("label").asText(key));
                    if(values!=null&&values.isObject()&&values.fields().hasNext())
                        throw new IllegalArgumentException("Repeatable section values must be stored in entries, not section values");
                }else{
                    if(items!=null&&items.isArray()&&!items.isEmpty())
                        throw new IllegalArgumentException("This template section does not allow repeatable entries");
                    if(values!=null&&!values.isMissingNode()&&!values.isNull()){
                        if(!values.isObject())throw new IllegalArgumentException("Section values must be an object");
                        validateAllowedFieldKeys(values,allowedFields,key);
                    }
                    if(enabled)for(String fieldKey:requiredFields){
                        if(!hiddenFields.contains(fieldKey)&&(values==null||!hasText(values.path(fieldKey))))
                            throw new IllegalArgumentException("Complete required field '"+fieldKey+"' in "+section.path("label").asText(key));
                    }
                    if(enabled&&requiredSection&&requiredFields.isEmpty()&&(values==null||!values.fields().hasNext()))
                        throw new IllegalArgumentException("Complete the required section: "+section.path("label").asText(key));
                }
            }
            for(String savedKey:savedByKey.keySet()){
                if(!knownSections.contains(savedKey))
                    throw new IllegalArgumentException("This template does not allow custom sections: "+savedKey);
            }
            com.fasterxml.jackson.databind.JsonNode locales=template.path("locales");
            String locale=content.path("locale").asText("");
            if(!locale.isBlank()&&locales.isArray()&&locales.size()>0){
                boolean supported=false;for(com.fasterxml.jackson.databind.JsonNode supportedLocale:locales)if(locale.equals(supportedLocale.asText()))supported=true;
                if(!supported)throw new IllegalArgumentException("This template does not support the selected language");
            }
        }catch(IllegalArgumentException e){throw e;}
        catch(Exception e){throw new IllegalArgumentException("Unable to validate invitation fields against the selected template");}
    }

    private void validateAllowedFieldKeys(com.fasterxml.jackson.databind.JsonNode values,java.util.Map<String,Boolean> allowedFields,String sectionKey){
        var fields=values.fieldNames();
        while(fields.hasNext()){
            String fieldKey=fields.next();
            if(!allowedFields.containsKey(fieldKey))
                throw new IllegalArgumentException("This template does not allow custom field '"+fieldKey+"' in section '"+sectionKey+"'");
            com.fasterxml.jackson.databind.JsonNode value=values.path(fieldKey);
            if(!value.isValueNode()&&!value.isNull())
                throw new IllegalArgumentException("Template field values must be simple text or numbers");
            if(value.isTextual()&&value.asText().length()>10000)
                throw new IllegalArgumentException("Template field '"+fieldKey+"' is too long");
        }
    }

    private boolean hasText(com.fasterxml.jackson.databind.JsonNode value){
        return value!=null&&!value.isMissingNode()&&!value.isNull()&&!value.asText("").isBlank();
    }

    private void validateTimezone(String timezone){
        if(timezone==null||timezone.isBlank()||!settings.getStringList("invitation.supported-timezones").contains(timezone))
            throw new IllegalArgumentException("Select a supported invitation timezone");
        try{ZoneId.of(timezone);}catch(Exception ex){throw new IllegalArgumentException("Invitation timezone is invalid");}
    }

}
