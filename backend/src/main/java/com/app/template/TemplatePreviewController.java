package com.app.template;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class TemplatePreviewController {
    private final TemplatePackageService templates;
    public TemplatePreviewController(TemplatePackageService templates) { this.templates=templates; }

    @GetMapping(value="/template-preview/{templateId}",produces=MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> preview(@PathVariable String templateId) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore())
            .header("Content-Security-Policy","sandbox allow-scripts; default-src 'self' data:; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'none'; form-action 'none'; base-uri 'none'; frame-ancestors 'self'")
            .header(HttpHeaders.X_CONTENT_TYPE_OPTIONS,"nosniff").body(templates.previewPage(templateId));
    }

    @GetMapping(value="/templates/{templateId}/{assetName}")
    public ResponseEntity<Resource> asset(@PathVariable String templateId,@PathVariable String assetName) {
        MediaType type="styles.css".equals(assetName)?MediaType.valueOf("text/css"):MediaType.valueOf("application/javascript");
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.X_CONTENT_TYPE_OPTIONS,"nosniff").body(templates.asset(templateId,assetName));
    }
}