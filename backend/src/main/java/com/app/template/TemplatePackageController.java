package com.app.template;

import com.app.common.api.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog/template-packages")
public class TemplatePackageController {
    private final TemplatePackageService templates;
    public TemplatePackageController(TemplatePackageService templates) { this.templates=templates; }
    @GetMapping public ApiResponse<?> list() { return ApiResponse.success(templates.listTemplates()); }
    @GetMapping("/{templateId}") public ApiResponse<?> get(@PathVariable String templateId) { return ApiResponse.success(templates.getTemplate(templateId)); }
    @GetMapping("/{templateId}/preview-data") public ApiResponse<?> previewData(@PathVariable String templateId) { return ApiResponse.success(templates.getPreviewData(templateId)); }
}