package com.app.health;

import com.app.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class HealthController {
    @GetMapping("/status")
    public ApiResponse<Map<String, String>> status() {
        return ApiResponse.success(Map.of("application", "DigitalInvitation", "status", "UP"));
    }
}
