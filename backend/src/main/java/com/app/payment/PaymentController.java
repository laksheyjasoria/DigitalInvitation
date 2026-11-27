package com.app.payment;

import com.app.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    private final JdbcTemplate jdbc;private final PaymentProvider provider;private final ObjectMapper mapper;private final com.app.config.SystemSettingService settings;private final PaymentSettlementService settlement;
    public PaymentController(JdbcTemplate jdbc,ObjectProvider<PaymentProvider> provider,ObjectMapper mapper,com.app.config.SystemSettingService settings,PaymentSettlementService settlement){this.jdbc=jdbc;this.provider=provider.getIfAvailable();this.mapper=mapper;this.settings=settings;this.settlement=settlement;}
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/invitations/{id}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> createOrder(@PathVariable UUID id,Authentication auth){
        UUID user=UUID.fromString(auth.getName());
        if(isAdministrator(auth))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,"Administrator accounts publish without payment checkout");
        if(!settings.getBoolean("invitation.publication.payment-required"))throw new IllegalArgumentException("Payment is not required for this invitation configuration");
        if(provider==null)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Payment is required but no payment provider is configured. Configure and verify the provider before enabling paid publication.");
        var invitation=jdbc.queryForMap("SELECT id,last_function_date,timezone FROM invitation WHERE id=? AND owner_user_id=? AND status='DRAFT' FOR UPDATE",id,user);
        long baseAmount=settings.getLong("pricing.base-price-minor");
        BigDecimal taxPercent=settings.getDecimal("pricing.tax-percent");
        if(baseAmount<0||taxPercent.compareTo(BigDecimal.ZERO)<0||taxPercent.compareTo(new BigDecimal("100"))>0)throw new IllegalStateException("Configured pricing is invalid");
        long taxAmount=BigDecimal.valueOf(baseAmount).multiply(taxPercent).divide(new BigDecimal("100"),0,RoundingMode.HALF_UP).longValueExact();
        long amount=Math.addExact(baseAmount,taxAmount);
        String currency=settings.getString("pricing.currency");
        int graceDays=settings.getInt("invitation.expiry.grace-days");
        long expiryVersion=jdbc.queryForObject("SELECT version FROM system_setting WHERE setting_key='invitation.expiry.grace-days'",Long.class);
        String lastFunctionDate=invitation.get("last_function_date")==null?"":invitation.get("last_function_date").toString();
        if(lastFunctionDate.isBlank())throw new IllegalArgumentException("Set the last function date before checkout");
        String timezone=String.valueOf(invitation.get("timezone"));
        java.time.ZoneId.of(timezone);
        Map<String,Object> expiryPolicy=Map.of("graceDays",graceDays,"settingVersion",expiryVersion,"lastFunctionDate",lastFunctionDate,"timezone",timezone,"paymentRequired",true);
        var requiredConsents=jdbc.query("SELECT substring(setting_key from 17) consent_key FROM system_setting WHERE setting_key LIKE 'consent.required.%' AND setting_value='true'::jsonb",(rs,n)->rs.getString("consent_key"));
        var acceptedRows=jdbc.query("SELECT DISTINCT ON (consent_key) consent_key,consent_version FROM invitation_consent WHERE invitation_id=? AND user_id=? AND content_version=(SELECT version FROM invitation WHERE id=?) AND accepted=true ORDER BY consent_key,accepted_at DESC",
            (rs,n)->Map.of("key",rs.getString("consent_key"),"version",rs.getString("consent_version")),id,user,id);
        Map<String,String> consentVersions=new java.util.HashMap<>();
        acceptedRows.forEach(row->consentVersions.putIfAbsent((String)row.get("key"),(String)row.get("version")));
        consentVersions.keySet().retainAll(new java.util.HashSet<>(requiredConsents));
        for(String key:requiredConsents){
            if(key.equals("terms")||key.equals("privacy")){
                String policyType=key.equals("terms")?"TERMS":"PRIVACY";
                String currentVersion=String.valueOf(jdbc.queryForObject("SELECT max(version_number) FROM policy_document WHERE policy_type=? AND status='PUBLISHED'",Integer.class,policyType));
                if(!currentVersion.equals(consentVersions.get(key)))throw new IllegalArgumentException("Please review and accept the latest "+policyType+" policy before checkout");
            }
        }
        if(requiredConsents.isEmpty()||!consentVersions.keySet().equals(new java.util.HashSet<>(requiredConsents)))
            throw new IllegalArgumentException("All required declarations and policy versions must be accepted before checkout");

        String pricingSnapshot;
        try{pricingSnapshot=mapper.writeValueAsString(Map.of("baseAmountMinor",baseAmount,"taxPercent",taxPercent,"taxAmountMinor",taxAmount,"amountMinor",amount,"currency",currency,"expiryPolicy",expiryPolicy,"requiredConsents",requiredConsents,"consentVersions",consentVersions));}
        catch(Exception e){throw new IllegalStateException("Unable to snapshot order terms");}
        UUID orderId=UUID.randomUUID();String orderNumber="DI-"+UUID.randomUUID().toString().replace("-","").substring(0,18).toUpperCase();
        jdbc.update("INSERT INTO customer_order(id,user_id,invitation_id,order_number,currency,amount_minor,status,pricing_snapshot) VALUES (?,?,?,?,?,?,'CREATED',?::jsonb)",orderId,user,id,orderNumber,currency,amount,pricingSnapshot);
        jdbc.update("UPDATE invitation SET status='PENDING_PAYMENT',updated_at=now() WHERE id=? AND owner_user_id=?",id,user);
        return ApiResponse.success(Map.of("orderId",orderId,"orderNumber",orderNumber,"baseAmountMinor",baseAmount,"taxPercent",taxPercent,"taxAmountMinor",taxAmount,"amountMinor",amount,"currency",currency));
    }
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/invitations/{id}/extensions/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> createExtensionOrder(@PathVariable UUID id,Authentication auth){
        if(isAdministrator(auth))throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,"Administrator accounts are exempt from invitation charges");
        if(!settings.getBoolean("invitation.expiry.extension-enabled"))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Invitation extensions are not available");
        if(provider==null)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Invitation extensions require a configured payment provider.");
        UUID user=UUID.fromString(auth.getName());
        var invitation=jdbc.query("SELECT i.status,p.id publication_id,p.revoked_at FROM invitation i JOIN LATERAL (SELECT id,revoked_at FROM invitation_publication WHERE invitation_id=i.id ORDER BY published_at DESC LIMIT 1) p ON true WHERE i.id=? AND i.owner_user_id=? FOR UPDATE OF i",
            (rs,n)->Map.of("status",rs.getString("status"),"publicationId",rs.getObject("publication_id",UUID.class),"revoked",rs.getTimestamp("revoked_at")!=null),id,user);
        if(invitation.isEmpty()||Boolean.TRUE.equals(invitation.get(0).get("revoked"))||!java.util.Set.of("PUBLISHED","EXPIRED").contains(invitation.get(0).get("status")))
            throw new IllegalArgumentException("Only published or expired invitations can be extended");
        Integer pendingExtensions=jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE invitation_id=? AND order_type='EXTENSION' AND status IN ('CREATED','PAYMENT_PENDING')",Integer.class,id);
        if(pendingExtensions!=null&&pendingExtensions>0)throw new IllegalArgumentException("An invitation extension order is already pending");
        int days=settings.getInt("invitation.expiry.extension-days");long baseAmount=settings.getLong("invitation.expiry.extension-price-minor");
        BigDecimal taxPercent=settings.getDecimal("pricing.tax-percent");
        if(days<=0||baseAmount<0||taxPercent.compareTo(BigDecimal.ZERO)<0||taxPercent.compareTo(new BigDecimal("100"))>0)throw new IllegalStateException("Configured extension pricing is invalid");
        long taxAmount=BigDecimal.valueOf(baseAmount).multiply(taxPercent).divide(new BigDecimal("100"),0,RoundingMode.HALF_UP).longValueExact();
        long amount=Math.addExact(baseAmount,taxAmount);
        String currency=settings.getString("pricing.currency");UUID orderId=UUID.randomUUID();
        String orderNumber="DI-EXT-"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase();
        String snapshot;
        try{snapshot=mapper.writeValueAsString(Map.of("baseAmountMinor",baseAmount,"taxPercent",taxPercent,"taxAmountMinor",taxAmount,"amountMinor",amount,"currency",currency,"extensionDays",days,"publicationId",invitation.get(0).get("publicationId")));}
        catch(Exception e){throw new IllegalStateException("Unable to snapshot extension terms");}
        jdbc.update("INSERT INTO customer_order(id,user_id,invitation_id,order_number,order_type,currency,amount_minor,status,pricing_snapshot) VALUES (?,?,?,?,?,?,?, 'CREATED',?::jsonb)",
            orderId,user,id,orderNumber,"EXTENSION",currency,amount,snapshot);
        return ApiResponse.success(Map.of("orderId",orderId,"orderNumber",orderNumber,"baseAmountMinor",baseAmount,"taxAmountMinor",taxAmount,"amountMinor",amount,"currency",currency,"extensionDays",days));
    }
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/payments/{orderId}/cancel")
    public ApiResponse<?> cancelOrder(@PathVariable UUID orderId,Authentication auth){
        UUID user=UUID.fromString(auth.getName());
        var rows=jdbc.query("SELECT invitation_id,order_type FROM customer_order WHERE id=? AND user_id=? AND status IN ('CREATED','PAYMENT_PENDING') AND NOT EXISTS (SELECT 1 FROM payment_transaction p WHERE p.order_id=customer_order.id) FOR UPDATE",
            (rs,n)->Map.of("invitationId",rs.getObject("invitation_id",UUID.class),"orderType",rs.getString("order_type")),orderId,user);
        if(rows.isEmpty())throw new IllegalArgumentException("Order cannot be cancelled after a provider checkout has been created");
        jdbc.update("UPDATE customer_order SET status='CANCELLED',updated_at=now() WHERE id=? AND user_id=? AND status IN ('CREATED','PAYMENT_PENDING')",orderId,user);
        if("INVITATION".equals(rows.get(0).get("orderType")))
            jdbc.update("UPDATE invitation SET status='DRAFT',updated_at=now() WHERE id=? AND status='PENDING_PAYMENT'",rows.get(0).get("invitationId"));
        return ApiResponse.success(Map.of("orderId",orderId,"status","CANCELLED"));
    }
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/payments/{orderId}/checkout")
    public ApiResponse<?> checkout(@PathVariable UUID orderId,Authentication auth){
        if(provider==null)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Payment provider is not configured");
        UUID user=UUID.fromString(auth.getName());
        var rows=jdbc.query("SELECT o.id,o.amount_minor,o.currency,o.order_number,o.status FROM customer_order o WHERE o.id=? AND o.user_id=? AND o.status IN ('CREATED','PAYMENT_PENDING') FOR UPDATE",
            (rs,n)->Map.of("id",rs.getObject("id").toString(),"amount",rs.getLong("amount_minor"),"currency",rs.getString("currency"),"receipt",rs.getString("order_number"),"status",rs.getString("status")),orderId,user);
        if(rows.isEmpty())throw new IllegalArgumentException("Order not found or not payable");
        var order=rows.get(0);
        var existing=jdbc.query("SELECT provider_order_id FROM payment_transaction WHERE order_id=? AND provider=? AND status IN ('INITIATED','AUTHORIZED') ORDER BY created_at DESC LIMIT 1",
            (rs,n)->rs.getString("provider_order_id"),orderId,provider.name());
        if(!existing.isEmpty()){
            Map<String,Object> providerOrder=Map.of("id",existing.get(0),"amount",order.get("amount"),"currency",order.get("currency"));
            return ApiResponse.success(Map.of("provider",provider.name(),"keyId",provider.publicKey(),"providerOrder",providerOrder,"reused",true));
        }
        Map<String,Object> result=provider.createOrder((Long)order.get("amount"),(String)order.get("currency"),(String)order.get("receipt"));
        Object gatewayId=result==null?null:result.get("id");
        if(gatewayId==null||String.valueOf(gatewayId).isBlank())throw new IllegalStateException("Payment provider did not return an order ID");
        jdbc.update("INSERT INTO payment_transaction(order_id,provider,provider_order_id,status,amount_minor,currency) VALUES (?,?,?,'INITIATED',?,?)",
            orderId,provider.name(),String.valueOf(gatewayId),order.get("amount"),order.get("currency"));
        jdbc.update("UPDATE customer_order SET status='PAYMENT_PENDING',updated_at=now() WHERE id=? AND user_id=?",orderId,user);
        return ApiResponse.success(Map.of("provider",provider.name(),"keyId",provider.publicKey(),"providerOrder",result,"reused",false));
    }
    private boolean isAdministrator(Authentication auth){return auth!=null&&auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_ADMIN")||a.getAuthority().equals("ROLE_SUPER_ADMIN"));}
    public record VerifyRequest(String providerOrderId,String providerPaymentId,String signature){}
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/payments/{orderId}/verify")
    public ApiResponse<?> verify(@PathVariable UUID orderId,@RequestBody VerifyRequest r,Authentication auth){
        if(provider==null)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Payment provider is not configured");
        var order=jdbc.query("SELECT amount_minor,currency,status FROM customer_order WHERE id=? AND user_id=?",
            (rs,n)->Map.of("amount",rs.getLong("amount_minor"),"currency",rs.getString("currency"),"status",rs.getString("status")),orderId,UUID.fromString(auth.getName()));
        if(order.isEmpty())throw new IllegalArgumentException("Order not found");
        if("PAID".equals(order.get(0).get("status")))return ApiResponse.success(Map.of("orderId",orderId,"status","PAID"));
        long amount=((Number)order.get(0).get("amount")).longValue();String currency=(String)order.get(0).get("currency");
        if(!provider.verifyCheckoutSignature(r.providerOrderId(),r.providerPaymentId(),r.signature()) ||
           !provider.verifyPaymentDetails(r.providerPaymentId(),r.providerOrderId(),amount,currency))
            throw new IllegalArgumentException("Payment could not be verified as captured for this order");
        int changed=jdbc.update("UPDATE payment_transaction SET provider_payment_id=?,status='CAPTURED',verified_at=now(),updated_at=now() WHERE order_id=? AND provider=? AND provider_order_id=? AND status IN ('INITIATED','AUTHORIZED') AND EXISTS (SELECT 1 FROM customer_order o WHERE o.id=payment_transaction.order_id AND o.user_id=?)",
            r.providerPaymentId(),orderId,provider.name(),r.providerOrderId(),UUID.fromString(auth.getName()));
        if(changed==0)throw new IllegalArgumentException("Payment transaction not found or already processed");
        settlement.settle(orderId,provider.name(),r.providerPaymentId());
        return ApiResponse.success(Map.of("orderId",orderId,"status","PAID"));
    }
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/webhooks/razorpay")
    public ApiResponse<?> webhook(@RequestHeader("X-Razorpay-Signature") String signature,
                                  @RequestBody String body) {
        if (provider == null || !"RAZORPAY".equals(provider.name())
                || !provider.verifyWebhookSignature(body, signature)) {
            throw new IllegalArgumentException("Invalid webhook signature");
        }

        final com.fasterxml.jackson.databind.JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid webhook payload");
        }

        String eventId = root.path("id").asText();
        String eventType = root.path("event").asText();
        if (eventId.isBlank() || eventType.isBlank()) {
            throw new IllegalArgumentException("Webhook event id and type are required");
        }

        int inserted = jdbc.update(
                "INSERT INTO payment_webhook_event(provider,provider_event_id,event_type,payload,processing_status) " +
                "VALUES ('RAZORPAY',?,?,?::jsonb,'RECEIVED') " +
                "ON CONFLICT(provider,provider_event_id) DO NOTHING",
                eventId, eventType, body);
        if (inserted == 0) {
            return ApiResponse.success(Map.of("received", true, "duplicate", true));
        }

        if ("payment.captured".equals(eventType)) {
            var payment = root.path("payload").path("payment").path("entity");
            String paymentId = payment.path("id").asText();
            String gatewayOrderId = payment.path("order_id").asText();
            String gatewayStatus = payment.path("status").asText();
            long amount = payment.path("amount").asLong(-1);
            String currency = payment.path("currency").asText();

            if (paymentId.isBlank() || gatewayOrderId.isBlank() || !"captured".equals(gatewayStatus)) {
                rejectWebhook(eventId, "Captured event is incomplete or not captured");
                return ApiResponse.success(Map.of("received", true, "rejected", true));
            }

            var matches = jdbc.query(
                    "SELECT p.id,p.order_id,p.provider_payment_id,p.status,p.amount_minor,p.currency," +
                    "o.status AS order_status FROM payment_transaction p " +
                    "JOIN customer_order o ON o.id=p.order_id " +
                    "WHERE p.provider='RAZORPAY' AND p.provider_order_id=? FOR UPDATE OF p,o",
                    (rs, n) -> {
                        Map<String, Object> row = new java.util.HashMap<>();
                        row.put("transactionId", rs.getObject("id", UUID.class));
                        row.put("orderId", rs.getObject("order_id", UUID.class));
                        row.put("paymentId", rs.getString("provider_payment_id"));
                        row.put("transactionStatus", rs.getString("status"));
                        row.put("amount", rs.getLong("amount_minor"));
                        row.put("currency", rs.getString("currency"));
                        row.put("orderStatus", rs.getString("order_status"));
                        return row;
                    }, gatewayOrderId);

            if (matches.isEmpty()) {
                rejectWebhook(eventId, "No local payment transaction matches the provider order");
                return ApiResponse.success(Map.of("received", true, "rejected", true));
            }

            var transaction = matches.get(0);
            boolean amountMatches = ((Number) transaction.get("amount")).longValue() == amount;
            boolean currencyMatches = currency.equals(transaction.get("currency"));
            String transactionStatus = (String) transaction.get("transactionStatus");
            String orderStatus = (String) transaction.get("orderStatus");
            String previousPaymentId = (String) transaction.get("paymentId");

            if (!amountMatches || !currencyMatches
                    || java.util.Set.of("CANCELLED", "REFUNDED", "PARTIALLY_REFUNDED").contains(orderStatus)
                    || ("CAPTURED".equals(transactionStatus) && !paymentId.equals(previousPaymentId))
                    || !java.util.Set.of("INITIATED", "AUTHORIZED", "FAILED", "CAPTURED").contains(transactionStatus)) {
                rejectWebhook(eventId, "Captured payment does not match a payable local transaction");
                return ApiResponse.success(Map.of("received", true, "rejected", true));
            }

            if (!"CAPTURED".equals(transactionStatus)) {
                jdbc.update(
                        "UPDATE payment_transaction SET provider_payment_id=?,status='CAPTURED'," +
                        "verified_at=now(),updated_at=now() WHERE id=?",
                        paymentId, transaction.get("transactionId"));
            }

            settlement.settle((UUID) transaction.get("orderId"), provider.name(), paymentId);
        } else if ("payment.failed".equals(eventType)) {
            var payment = root.path("payload").path("payment").path("entity");
            String gatewayOrderId = payment.path("order_id").asText();
            if (!gatewayOrderId.isBlank()) {
                jdbc.update(
                        "UPDATE payment_transaction SET status='FAILED',updated_at=now() " +
                        "WHERE provider='RAZORPAY' AND provider_order_id=? " +
                        "AND status IN ('INITIATED','AUTHORIZED')",
                        gatewayOrderId);
                jdbc.update(
                        "UPDATE customer_order SET status='CREATED',updated_at=now() " +
                        "WHERE id=(SELECT order_id FROM payment_transaction " +
                        "WHERE provider='RAZORPAY' AND provider_order_id=?) " +
                        "AND status='PAYMENT_PENDING'",
                        gatewayOrderId);
            }
        }

        jdbc.update(
                "UPDATE payment_webhook_event SET processing_status='PROCESSED',processed_at=now() " +
                "WHERE provider='RAZORPAY' AND provider_event_id=?",
                eventId);
        return ApiResponse.success(Map.of("received", true));
    }

    private void rejectWebhook(String eventId, String reason) {
        jdbc.update(
                "UPDATE payment_webhook_event SET processing_status='REJECTED',processed_at=now() " +
                "WHERE provider='RAZORPAY' AND provider_event_id=?",
                eventId);
        jdbc.update(
                "INSERT INTO audit_log(action,resource_type,resource_id,outcome,metadata) " +
                "VALUES ('PAYMENT_WEBHOOK_REJECTED','PAYMENT_WEBHOOK',?,'REJECTED',?::jsonb)",
                eventId, mapper.valueToTree(Map.of("reason", reason)).toString());
    }
}
