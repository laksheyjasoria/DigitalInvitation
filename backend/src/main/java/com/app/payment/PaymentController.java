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
        if(!settings.getBoolean("invitation.publication.payment-required"))throw new IllegalArgumentException("Payment is not required for this invitation configuration");
        var invitation=jdbc.queryForMap("SELECT id,last_function_date,timezone FROM invitation WHERE id=? AND owner_user_id=? AND status='DRAFT'",id,user);
        long baseAmount=settings.getLong("pricing.base-price-minor");
        BigDecimal taxPercent=settings.getDecimal("pricing.tax-percent");
        if(baseAmount<0||taxPercent.compareTo(BigDecimal.ZERO)<0||taxPercent.compareTo(new BigDecimal("100"))>0)throw new IllegalStateException("Configured pricing is invalid");
        long taxAmount=BigDecimal.valueOf(baseAmount).multiply(taxPercent).divide(new BigDecimal("100"),0,RoundingMode.HALF_UP).longValueExact();
        long amount=Math.addExact(baseAmount,taxAmount);
        String currency=settings.getString("pricing.currency");
        int graceDays=settings.getInt("invitation.expiry.grace-days");
        long expiryVersion=jdbc.queryForObject("SELECT version FROM system_setting WHERE setting_key='invitation.expiry.grace-days'",Long.class);
        String lastFunctionDate=invitation.get("last_function_date")==null?"":invitation.get("last_function_date").toString();
        String timezone=String.valueOf(invitation.get("timezone"));
        Map<String,Object> expiryPolicy=Map.of("graceDays",graceDays,"settingVersion",expiryVersion,"lastFunctionDate",lastFunctionDate,"timezone",timezone,"paymentRequired",true);
        var requiredConsents=jdbc.query("SELECT substring(setting_key from 17) consent_key FROM system_setting WHERE setting_key LIKE 'consent.required.%' AND setting_value='true'::jsonb",(rs,n)->rs.getString("consent_key"));
        var acceptedRows=jdbc.query("SELECT consent_key,consent_version FROM invitation_consent WHERE invitation_id=? AND user_id=? AND content_version=(SELECT version FROM invitation WHERE id=?) AND accepted=true",
            (rs,n)->Map.of("key",rs.getString("consent_key"),"version",rs.getString("consent_version")),id,user,id);
        Map<String,String> consentVersions=new java.util.HashMap<>();
        acceptedRows.forEach(row->consentVersions.put((String)row.get("key"),(String)row.get("version")));
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
        if(!settings.getBoolean("invitation.expiry.extension-enabled"))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Invitation extensions are not available");
        UUID user=UUID.fromString(auth.getName());
        var invitation=jdbc.query("SELECT i.status,p.id publication_id FROM invitation i JOIN LATERAL (SELECT id FROM invitation_publication WHERE invitation_id=i.id ORDER BY published_at DESC LIMIT 1) p ON true WHERE i.id=? AND i.owner_user_id=?",
            (rs,n)->Map.of("status",rs.getString("status"),"publicationId",rs.getObject("publication_id",UUID.class)),id,user);
        if(invitation.isEmpty()||!java.util.Set.of("PUBLISHED","EXPIRED").contains(invitation.get(0).get("status")))
            throw new IllegalArgumentException("Only published or expired invitations can be extended");
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
        var rows=jdbc.query("SELECT invitation_id FROM customer_order WHERE id=? AND user_id=? AND status IN ('CREATED','PAYMENT_PENDING') AND NOT EXISTS (SELECT 1 FROM payment_transaction p WHERE p.order_id=customer_order.id)",
            (rs,n)->rs.getObject("invitation_id",UUID.class),orderId,user);
        if(rows.isEmpty())throw new IllegalArgumentException("Order cannot be cancelled after a provider checkout has been created");
        jdbc.update("UPDATE customer_order SET status='CANCELLED',updated_at=now() WHERE id=? AND user_id=?",orderId,user);
        if("INVITATION".equals(jdbc.queryForObject("SELECT order_type FROM customer_order WHERE id=?",String.class,orderId)))
            jdbc.update("UPDATE invitation SET status='DRAFT',updated_at=now() WHERE id=? AND status='PENDING_PAYMENT'",rows.get(0));
        return ApiResponse.success(Map.of("orderId",orderId,"status","CANCELLED"));
    }
    @PostMapping("/payments/{orderId}/checkout")
    public ApiResponse<?> checkout(@PathVariable UUID orderId,Authentication auth){
        if(provider==null)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Payment provider is not configured");
        var rows=jdbc.query("SELECT o.id,o.amount_minor,o.currency,o.order_number FROM customer_order o WHERE o.id=? AND o.user_id=? AND o.status IN ('CREATED','PAYMENT_PENDING')",
            (rs,n)->Map.of("id",rs.getObject("id").toString(),"amount",rs.getLong("amount_minor"),"currency",rs.getString("currency"),"receipt",rs.getString("order_number")),orderId,UUID.fromString(auth.getName()));
        if(rows.isEmpty())throw new IllegalArgumentException("Order not found or not payable");var o=rows.get(0);
        Map<String,Object> result=provider.createOrder((Long)o.get("amount"),(String)o.get("currency"),(String)o.get("receipt"));
        String gatewayId=String.valueOf(result.get("id"));
        jdbc.update("INSERT INTO payment_transaction(order_id,provider,provider_order_id,status,amount_minor,currency) VALUES (?, ?, ?,'INITIATED',?,?)",orderId,provider.name(),gatewayId,o.get("amount"),o.get("currency"));
        jdbc.update("UPDATE customer_order SET status='PAYMENT_PENDING',updated_at=now() WHERE id=?",orderId);
        return ApiResponse.success(Map.of("provider",provider.name(),"keyId",provider.publicKey(),"providerOrder",result));
    }
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
    public ApiResponse<?> webhook(@RequestHeader("X-Razorpay-Signature")String signature,@RequestBody String body){
        if(provider==null||!provider.name().equals("RAZORPAY")||!provider.verifyWebhookSignature(body,signature))
            throw new IllegalArgumentException("Invalid webhook signature");
        try{
            var root=mapper.readTree(body);String eventId=root.path("id").asText();String eventType=root.path("event").asText();
            if(eventId.isBlank())throw new IllegalArgumentException("Webhook event id is required");
            int inserted=jdbc.update("INSERT INTO payment_webhook_event(provider,provider_event_id,event_type,payload,processing_status) VALUES ('RAZORPAY',?,?,?::jsonb,'RECEIVED') ON CONFLICT(provider,provider_event_id) DO NOTHING",eventId,eventType,body);
            if(inserted==0)return ApiResponse.success(Map.of("received",true,"duplicate",true));
            var payment=root.path("payload").path("payment").path("entity");
            String paymentId=payment.path("id").asText();String gatewayOrderId=payment.path("order_id").asText();
            if(eventType.equals("payment.captured")||eventType.equals("order.paid")){
                if(paymentId.isBlank()||gatewayOrderId.isBlank())throw new IllegalArgumentException("Captured payment event is incomplete");
                int updated=jdbc.update("UPDATE payment_transaction SET provider_payment_id=?,status='CAPTURED',verified_at=now(),updated_at=now() WHERE provider='RAZORPAY' AND provider_order_id=? AND amount_minor=? AND currency=? AND status IN ('INITIATED','AUTHORIZED')",
                    paymentId,gatewayOrderId,payment.path("amount").asLong(),payment.path("currency").asText());
                if(updated==1){
                    UUID paidOrderId=jdbc.queryForObject("SELECT order_id FROM payment_transaction WHERE provider='RAZORPAY' AND provider_order_id=?",UUID.class,gatewayOrderId);
                    settlement.settle(paidOrderId,provider.name(),paymentId);
                }
            }else if(eventType.equals("payment.failed")){
                jdbc.update("UPDATE payment_transaction SET status='FAILED',updated_at=now() WHERE provider='RAZORPAY' AND provider_order_id=? AND status='INITIATED'",gatewayOrderId);
                jdbc.update("UPDATE customer_order SET status='FAILED',updated_at=now() WHERE id=(SELECT order_id FROM payment_transaction WHERE provider='RAZORPAY' AND provider_order_id=?) AND status<>'PAID'",gatewayOrderId);
            }
            jdbc.update("UPDATE payment_webhook_event SET processing_status='PROCESSED',processed_at=now() WHERE provider='RAZORPAY' AND provider_event_id=?",eventId);
        }catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("Invalid webhook payload");}
        return ApiResponse.success(Map.of("received",true));
    }
}
