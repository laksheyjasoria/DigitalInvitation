package com.app.payment;

import java.util.Map;

public interface PaymentProvider {
    String name();
    String publicKey();
    Map<String,Object> createOrder(long amountMinor,String currency,String receipt);
    boolean verifyPaymentDetails(String paymentId,String orderId,long amountMinor,String currency);
    boolean verifyCheckoutSignature(String providerOrderId,String providerPaymentId,String signature);
    boolean verifyWebhookSignature(String rawBody,String signature);
}
