package com.app.invitation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class InvitationExpiryCalculatorTest {
    private final InvitationExpiryCalculator calculator = new InvitationExpiryCalculator();

    @Test
    void zeroGraceExpiresAtMidnightAfterFunctionDate() {
        Instant expiry = calculator.calculate(
                LocalDate.of(2026, 11, 26), 0, ZoneId.of("Asia/Kolkata"));
        assertEquals(Instant.parse("2026-11-26T18:30:00Z"), expiry);
    }

    @Test
    void sevenGraceDaysIncludesAllSevenCalendarDays() {
        Instant expiry = calculator.calculate(
                LocalDate.of(2026, 11, 26), 7, ZoneId.of("Asia/Kolkata"));
        assertEquals(Instant.parse("2026-12-03T18:30:00Z"), expiry);
    }

    @Test
    void calculatesCalendarDaysAcrossDaylightSavingTransition() {
        Instant expiry = calculator.calculate(
                LocalDate.of(2026, 3, 7), 1, ZoneId.of("America/New_York"));
        assertEquals(Instant.parse("2026-03-09T04:00:00Z"), expiry);
    }

    @Test
    void rejectsInvalidGraceAndMissingInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(LocalDate.now(), -1, ZoneId.of("UTC")));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(LocalDate.now(), 366, ZoneId.of("UTC")));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(null, 7, ZoneId.of("UTC")));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(LocalDate.now(), 7, null));
    }
}
