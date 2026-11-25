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
    private final JdbcTemplate jdbc;private final PaymentProvider provider;private final ObjectMapper mapper;
    public PaymentController(JdbcTemplate jdbc,ObjectProvider<PaymentProvider> provider,ObjectMapper mapper){this.jdbc=jdbc;this.provider=provider.getIfAvailable();this.mapper=mapper;}
    @PostMapping("/invitations/{id}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> createOrder(@PathVariable UUID id,Authentication auth){
        UUID user=UUID.fromString(auth.getName());jdbc.queryForObject("SELECT id FROM invitation WHERE id=? AND owner_user_id=? AND status IN ('DRAFT','PENDING_PAYMENT')",UUID.class,id,user);
        long amount=jdbc.query("SELECT setting_value::text FROM system_setting WHERE setting_key='pricing.base-price-minor'",rs->rs.next()?Long.parseLong(rs.getString(1).replace("\"","")):49900L);
        String currency=jdbc.query("SELECT setting_value::text FROM system_setting WHERE setting_key='pricing.currency'",rs->rs.next()?rs.getString(1).replace("\"",""):"INR");
        UUID orderId=UUID.randomUUID();String orderNumber="DI-"+UUID.randomUUID().toString().replace("-","").substring(0,18).toUpperCase();
        jdbc.update("INSERT INTO customer_order(id,user_id,invitation_id,order_number,currency,amount_minor,status,pricing_snapshot) VALUES (?,?,?,?,?,?,'CREATED',jsonb_build_object('amountMinor',?,'currency',?))",orderId,user,id,orderNumber,currency,amount,amount,currency);
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
    @PostMapping("/payments/{orderId}/verify")
    public ApiResponse<?> verify(@PathVariable UUID orderId,@RequestBody VerifyRequest r,Authentication auth){
        if(provider==null)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Payment provider is not configured");
        if(!provider.verifyCheckoutSignature(r.providerOrderId(),r.providerPaymentId(),r.signature()))throw new IllegalArgumentException("Payment signature verification failed");
        int changed=jdbc.update("UPDATE payment_transaction SET provider_payment_id=?,status='CAPTURED',verified_at=now(),updated_at=now() WHERE order_id=? AND provider=? AND provider_order_id=? AND status IN ('INITIATED','AUTHORIZED') AND EXISTS (SELECT 1 FROM customer_order o WHERE o.id=payment_transaction.order_id AND o.user_id=?)",r.providerPaymentId(),orderId,provider.name(),r.providerOrderId(),UUID.fromString(auth.getName()));
        if(changed==0)throw new IllegalArgumentException("Payment transaction not found or already processed");
        jdbc.update("UPDATE customer_order SET status='PAID',updated_at=now() WHERE id=? AND user_id=?",orderId,UUID.fromString(auth.getName()));
        return ApiResponse.success(Map.of("orderId",orderId,"status","PAID"));
    }
    @PostMapping("/webhooks/razorpay")
    public ApiResponse<?> webhook(@RequestHeader("X-Razorpay-Signature")String signature,@RequestBody String body){
        if(provider==null||!provider.name().equals("RAZORPAY")||!provider.verifyWebhookSignature(body,signature))throw new IllegalArgumentException("Invalid webhook signature");
        try{
            var root=mapper.readTree(body);String eventId=root.path("id").asText();String eventType=root.path("event").asText();
            jdbc.update("INSERT INTO payment_webhook_event(provider,provider_event_id,event_type,payload,processing_status) VALUES ('RAZORPAY',?,?,?::jsonb,'RECEIVED') ON CONFLICT(provider,provider_event_id) DO NOTHING",eventId,eventType,body);
        }catch(Exception e){throw new IllegalArgumentException("Invalid webhook payload");}
        return ApiResponse.success(Map.of("received",true));
    }
}
