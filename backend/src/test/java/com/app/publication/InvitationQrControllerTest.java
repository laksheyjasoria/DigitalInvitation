package com.app.publication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.io.ByteArrayInputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

class InvitationQrControllerTest {
    @Test
    void generatedQrDecodesToTheExactPublishedLocationUrl() throws Exception {
        String location = "https://maps.app.goo.gl/location-test";
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq("public-slug")))
                .thenReturn(List.of(location));

        var response = new InvitationQrController(jdbc).qr("public-slug");
        var image = ImageIO.read(new ByteArrayInputStream(response.getBody()));
        int width = image.getWidth(), height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        assertEquals(0, image.getRGB(0, 0) >>> 24, "QR quiet-zone background must be transparent");
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (pixels[i] >>> 24) == 0 ? 0xFFFFFFFF : (0xFF000000 | (pixels[i] & 0x00FFFFFF));
        }
        String decoded = new MultiFormatReader().decode(
                new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)))).getText();

        assertEquals(location, decoded);
    }

    @Test
    void expiredOrRevokedPublicationCannotProduceAQRCode() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq("expired")))
                .thenReturn(List.of());

        assertThrows(ResponseStatusException.class, () -> new InvitationQrController(jdbc).qr("expired"));
    }

    @Test
    void publicationWithoutLocationDoesNotFallBackToInvitationUrl() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq("no-location")))
                .thenReturn(java.util.Arrays.asList((String) null));

        assertThrows(ResponseStatusException.class, () -> new InvitationQrController(jdbc).qr("no-location"));
    }
}
