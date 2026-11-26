package com.app.publication;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Serves the public invitation shell; invitation data is loaded through the public API. */
@Controller
public class PublicInvitationPageController {
    @GetMapping({"/i/{slug}", "/i/{slug}/"})
    public String invitationPage(@PathVariable String slug) {
        return "forward:/invitation.html";
    }
}
