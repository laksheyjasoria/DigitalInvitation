package com.app.invitation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * Invitation validity includes the function date and all configured grace days.
 * The expiry instant is midnight immediately after the final valid calendar day
 * in the invitation's configured timezone.
 */
@Component
public class InvitationExpiryCalculator {
    public Instant calculate(LocalDate lastFunctionDate, int graceDays, ZoneId timezone) {
        if (lastFunctionDate == null) {
            throw new IllegalArgumentException("Last function date is required");
        }
        if (timezone == null) {
            throw new IllegalArgumentException("Invitation timezone is required");
        }
        if (graceDays < 0 || graceDays > 365) {
            throw new IllegalArgumentException("Grace period must be between 0 and 365 days");
        }
        return lastFunctionDate.plusDays((long) graceDays + 1L)
                .atStartOfDay(timezone)
                .toInstant();
    }
}
