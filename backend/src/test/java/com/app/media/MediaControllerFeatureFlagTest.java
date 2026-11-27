package com.app.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.app.config.SystemSettingService;
import com.app.storage.StorageProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class MediaControllerFeatureFlagTest {
    @Test
    void customerMediaApisAreBlockedWhenFeatureFlagIsOff() {
        MediaController controller = new MediaController(
                mock(JdbcTemplate.class), mock(StorageProvider.class),
                mock(SystemSettingService.class), false);

        ResponseStatusException upload = assertThrows(ResponseStatusException.class,
                () -> controller.upload(UUID.randomUUID(), null, null));
        ResponseStatusException list = assertThrows(ResponseStatusException.class,
                () -> controller.list(UUID.randomUUID(), null));

        assertEquals(HttpStatus.FORBIDDEN, upload.getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, list.getStatusCode());
    }

}
