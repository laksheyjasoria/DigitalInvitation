package com.app.invitation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class InvitationLocationUrlTest {
    @Test
    void acceptsGoogleMapsShareLinksAndPreservesTheSubmittedUrl() {
        String url = "https://maps.app.goo.gl/example?entry=ttu";
        assertEquals(url, InvitationLocationUrl.validate("  " + url + "  ", true));
    }

    @Test
    void permitsMissingLocationWhileSavingDraftButRequiresItForPublication() {
        assertEquals("", InvitationLocationUrl.validate("", false));
        assertThrows(IllegalArgumentException.class, () -> InvitationLocationUrl.validate("", true));
    }

    @Test
    void rejectsNonHttpsAndHostlessUrls() {
        assertThrows(IllegalArgumentException.class,
                () -> InvitationLocationUrl.validate("http://maps.google.com/example", true));
        assertThrows(IllegalArgumentException.class,
                () -> InvitationLocationUrl.validate("https:/missing-host", true));
        assertThrows(IllegalArgumentException.class,
                () -> InvitationLocationUrl.validate("javascript:alert(1)", true));
    }

    @Test
    void rejectsCredentialsAndOversizedUrls() {
        assertThrows(IllegalArgumentException.class,
                () -> InvitationLocationUrl.validate("https://user:pass@maps.google.com/", true));
        assertThrows(IllegalArgumentException.class,
                () -> InvitationLocationUrl.validate("https://maps.google.com/" + "a".repeat(2000), true));
    }
}
