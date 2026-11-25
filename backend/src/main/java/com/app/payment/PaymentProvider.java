package com.app.payment;

import java.util.Map;

public interface PaymentProvider {
    String name();
    String publicKey();
    Map<String,Object> createOrder(long amountMinor,String currency,String receipt);
    boolean verifyCheckoutSignature(String providerOrderId,String providerPaymentId,String signature);
    boolean verifyWebhookSignature(String rawBody,String signature);
}
