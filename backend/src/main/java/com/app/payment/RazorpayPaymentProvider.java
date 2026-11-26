package com.app.payment;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name="app.payment.razorpay.enabled",havingValue="true")
public class RazorpayPaymentProvider implements PaymentProvider {
    private final String keyId,keySecret,webhookSecret;private final RestClient client;
    public RazorpayPaymentProvider(@Value("${app.payment.razorpay.key-id}")String keyId,
      @Value("${app.payment.razorpay.key-secret}")String keySecret,
      @Value("${app.payment.razorpay.webhook-secret}")String webhookSecret){
        if(keyId==null||keyId.isBlank()||keySecret==null||keySecret.isBlank()||webhookSecret==null||webhookSecret.isBlank())
            throw new IllegalStateException("Razorpay is enabled but one or more required credentials are missing");
        this.keyId=keyId;this.keySecret=keySecret;this.webhookSecret=webhookSecret;
        this.client=RestClient.builder().baseUrl("https://api.razorpay.com/v1").defaultHeaders(h->h.setBasicAuth(keyId,keySecret)).build();
    }
    public String name(){return "RAZORPAY";}
    public String publicKey(){return keyId;}
    public Map<String,Object> createOrder(long amountMinor,String currency,String receipt){
        return client.post().uri("/orders").body(Map.of("amount",amountMinor,"currency",currency,"receipt",receipt,"payment_capture",1)).retrieve().body(Map.class);
    }
    public boolean verifyPaymentDetails(String paymentId,String orderId,long amountMinor,String currency){
        Map payment=client.get().uri("/payments/{id}",paymentId).retrieve().body(Map.class);
        return payment!=null && "captured".equals(payment.get("status"))
            && orderId.equals(payment.get("order_id"))
            && Long.valueOf(amountMinor).equals(((Number)payment.get("amount")).longValue())
            && currency.equals(payment.get("currency"));
    }
    public boolean verifyCheckoutSignature(String orderId,String paymentId,String signature){
        return constantTime(hmac(keySecret,orderId+"|"+paymentId),signature);
    }
    public boolean verifyWebhookSignature(String body,String signature){return constantTime(hmac(webhookSecret,body),signature);}
    private static String hmac(String secret,String text){
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException("Unable to verify payment signature",e);}
    }
    private static boolean constantTime(String a,String b){return b!=null&&java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
}
