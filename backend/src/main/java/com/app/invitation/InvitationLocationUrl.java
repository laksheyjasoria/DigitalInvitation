package com.app.invitation;

import java.net.URI;

final class InvitationLocationUrl {
    private InvitationLocationUrl() { }

    static String validate(String rawUrl, boolean required) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        if (value.isBlank()) {
            if (required) throw new IllegalArgumentException("Add the event location Google Maps URL before publishing");
            return "";
        }
        if (value.length() > 2000) throw new IllegalArgumentException("Event location URL is too long");
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Event location URL must be a valid HTTPS link");
            }
            return value;
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Event location URL")) throw ex;
            throw new IllegalArgumentException("Event location URL is invalid");
        }
    }
}
