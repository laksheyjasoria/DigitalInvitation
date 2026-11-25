package com.app.payment;

import com.app.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    private final JdbcTemplate jdbc;private final PaymentProvider provider;private final ObjectMapper mapper;private final com.app.config.SystemSettingService settings;
    public PaymentController(JdbcTemplate jdbc,ObjectProvider<PaymentProvider> provider,ObjectMapper mapper,com.app.config.SystemSettingService settings){this.jdbc=jdbc;this.provider=provider.getIfAvailable();this.mapper=mapper;this.settings=settings;}
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/invitations/{id}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> createOrder(@PathVariable UUID id,Authentication auth){
        UUID user=UUID.fromString(auth.getName());
        if(!settings.getBoolean("invitation.publication.payment-required"))throw new IllegalArgumentException("Payment is not required for this invitation configuration");
        var invitation=jdbc.queryForMap("SELECT id,last_function_date,timezone FROM invitation WHERE id=? AND owner_user_id=? AND status IN ('DRAFT','PENDING_PAYMENT')",id,user);
        long amount=settings.getLong("pricing.base-price-minor");
        String currency=settings.getString("pricing.currency");
        int graceDays=settings.getInt("invitation.expiry.grace-days");
        long expiryVersion=jdbc.queryForObject("SELECT version FROM system_setting WHERE setting_key='invitation.expiry.grace-days'",Long.class);
        String lastFunctionDate=invitation.get("last_function_date")==null?"":invitation.get("last_function_date").toString();
        String timezone=String.valueOf(invitation.get("timezone"));
        Map<String,Object> expiryPolicy=Map.of("graceDays",graceDays,"settingVersion",expiryVersion,"lastFunctionDate",lastFunctionDate,"timezone",timezone,"paymentRequired",true);
        String pricingSnapshot;
        try{pricingSnapshot=mapper.writeValueAsString(Map.of("amountMinor",amount,"currency",currency,"expiryPolicy",expiryPolicy));}
        catch(Exception e){throw new IllegalStateException("Unable to snapshot order terms");}
        UUID orderId=UUID.randomUUID();String orderNumber="DI-"+UUID.randomUUID().toString().replace("-","").substring(0,18).toUpperCase();
        jdbc.update("INSERT INTO customer_order(id,user_id,invitation_id,order_number,currency,amount_minor,status,pricing_snapshot) VALUES (?,?,?,?,?,?,'CREATED',?::jsonb)",orderId,user,id,orderNumber,currency,amount,pricingSnapshot);
        jdbc.update("UPDATE invitation SET status='PENDING_PAYMENT',updated_at=now() WHERE id=? AND owner_user_id=?",id,user);
        return ApiResponse.success(Map.of("orderId",orderId,"orderNumber",orderNumber,"amountMinor",amount,"currency",currency));
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
        jdbc.update("UPDATE customer_order SET status='PAID',updated_at=now() WHERE id=? AND user_id=?",orderId,UUID.fromString(auth.getName()));
        jdbc.update("UPDATE invitation SET status='READY_TO_PUBLISH',updated_at=now() WHERE id=(SELECT invitation_id FROM customer_order WHERE id=?) AND status='PENDING_PAYMENT'",orderId);
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
                    jdbc.update("UPDATE customer_order SET status='PAID',updated_at=now() WHERE id=(SELECT order_id FROM payment_transaction WHERE provider='RAZORPAY' AND provider_order_id=?)",gatewayOrderId);
                    jdbc.update("UPDATE invitation SET status='READY_TO_PUBLISH',updated_at=now() WHERE id=(SELECT invitation_id FROM customer_order WHERE id=(SELECT order_id FROM payment_transaction WHERE provider='RAZORPAY' AND provider_order_id=?)) AND status='PENDING_PAYMENT'",gatewayOrderId);
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
