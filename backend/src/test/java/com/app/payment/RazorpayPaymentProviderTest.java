package com.app.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RazorpayPaymentProviderTest {
    private final RazorpayPaymentProvider provider =
            new RazorpayPaymentProvider("rzp_test_key", "test-secret", "webhook-secret");

    @Test
    void sandboxModeRejectsLiveCredentials() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new RazorpayPaymentProvider("rzp_live_key", "secret", "webhook", "test"));
    }

    @Test
    void liveModeRejectsSandboxCredentials() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new RazorpayPaymentProvider("rzp_test_key", "secret", "webhook", "live"));
    }

    @Test
    void verifiesCheckoutSignatureAndRejectsTampering() {
        assertTrue(provider.verifyCheckoutSignature(
                "order_123", "pay_123",
                "849e7fdaf775fb6157f9e71da8e3fb39a6d995aab0814361fdfe3a1d77c11647"));
        assertFalse(provider.verifyCheckoutSignature(
                "order_123", "pay_123", "invalid-signature"));
        assertFalse(provider.verifyCheckoutSignature(
                "different-order", "pay_123",
                "849e7fdaf775fb6157f9e71da8e3fb39a6d995aab0814361fdfe3a1d77c11647"));
    }

    @Test
    void verifiesWebhookSignatureAgainstExactRawPayload() {
        String payload = "{\"event\":\"payment.captured\"}";
        assertTrue(provider.verifyWebhookSignature(payload,
                "0e95258623492dd0dae77245c958c256d46b4a30b338084b7e996fc25653c79c"));
        assertFalse(provider.verifyWebhookSignature(payload + " ",
                "0e95258623492dd0dae77245c958c256d46b4a30b338084b7e996fc25653c79c"));
    }
}
