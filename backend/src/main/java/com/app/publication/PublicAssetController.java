package com.app.publication;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/** Keeps the legacy browser favicon URL working while the app uses its SVG favicon. */
@Controller
public class PublicAssetController {
    @GetMapping("/favicon.ico")
    public RedirectView favicon() {
        return new RedirectView("/favicon.svg");
    }
}
