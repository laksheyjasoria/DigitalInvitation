package com.app.templatestudio;

import java.io.InputStream;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class TemplatePackageResourceService {
    private final ResourceLoader resources;
    private final TemplateDefinitionMapper mapper;

    public TemplatePackageResourceService(ResourceLoader resources, TemplateDefinitionMapper mapper) {
        this.resources = resources;
        this.mapper = mapper;
    }

    public Map<String, Object> preview(String resourcePath) {
        if (resourcePath == null || !resourcePath.matches("templates/packages/TEMP[0-9]{7}/[A-Za-z0-9._-]{1,40}\\.zip")) {
            throw new IllegalArgumentException("Template package resource path is invalid");
        }
        Resource resource = resources.getResource("classpath:" + resourcePath);
        try (InputStream input = resource.getInputStream()) {
            return mapper.mapPreview(input.readAllBytes());
        } catch (Exception ex) {
            throw new IllegalStateException("The published template package is missing from this application release", ex);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> previewFiles(String resourcePath) {
        return (Map<String, Object>) preview(resourcePath).get("previewFiles");
    }
}
