package com.app.publication;

import com.app.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationQrPreviewController {
    public record PreviewRequest(@NotBlank @Size(max = 2048) String locationUrl) {}

    @PostMapping("/qr-preview")
    public ApiResponse<?> preview(@Valid @RequestBody PreviewRequest request) throws Exception {
        String locationUrl = request.locationUrl().trim();
        final URI parsed;
        try {
            parsed = URI.create(locationUrl);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Enter a valid HTTPS event location URL");
        }
        if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null) {
            throw new IllegalArgumentException("Event location must be a valid HTTPS URL");
        }
        return ApiResponse.success(Map.of("dataUrl", InvitationQrController.dataUrl(locationUrl)));
    }
}
