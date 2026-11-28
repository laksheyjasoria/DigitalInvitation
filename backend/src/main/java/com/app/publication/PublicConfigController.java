package com.app.publication;
import com.app.common.api.ApiResponse;
import com.app.config.SystemSettingService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/public")
public class PublicConfigController {
 private final SystemSettingService settings;
 public PublicConfigController(SystemSettingService settings){this.settings=settings;}
 @GetMapping("/config")
 public ApiResponse<?> config(){
  return ApiResponse.success(Map.of(
   "paymentRequired",settings.getBoolean("invitation.publication.payment-required"),
   "previewEnabled",settings.getBoolean("invitation.preview.enabled"),
   "customSectionsEnabled",settings.getBoolean("invitation.custom-sections.enabled"),
   "rsvpEnabled",settings.getBoolean("invitation.rsvp.enabled"),
   "extensionEnabled",settings.getBoolean("invitation.expiry.extension-enabled"),
   "supportedLocales",settings.getStringList("invitation.supported-locales"),
   "defaultTimezone",settings.getString("invitation.default-timezone"),
   "supportedTimezones",settings.getStringList("invitation.supported-timezones")));
 }
}
