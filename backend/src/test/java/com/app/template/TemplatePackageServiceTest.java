package com.app.template;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class TemplatePackageServiceTest {
    private final TemplatePackageService service = new TemplatePackageService(new DefaultResourceLoader());

    @Test
    void loadsXmlTemplateAndItsFieldRules() {
        Map<String,Object> template=service.getTemplate("TEMP0000001-v1");
        assertEquals("Floral Garden",template.get("displayName"));
        assertEquals("WEDDING",template.get("category"));
        assertFalse((Boolean)template.get("customSectionsAllowed"));
        List<?> sections=(List<?>)template.get("sections");
        assertEquals(4,sections.size());
        Map<?,?> hero=(Map<?,?>)sections.get(0);
        assertTrue((Boolean)hero.get("required"));
        assertTrue(((List<?>)hero.get("requiredFields")).contains("headline"));
    }

    @Test
    void listsClasspathPackagesAndDummyPreviewData() {
        List<Map<String,Object>> templates=service.listTemplates();
        assertEquals(1,templates.size());
        assertEquals("TEMP0000001",templates.get(0).get("id"));
        Map<String,Object> preview=service.getPreviewData("TEMP0000001-v1");
        assertEquals("Alex & Sam",preview.get("title"));
        assertTrue(((Map<?,?>)preview.get("values")).containsKey("venue"));
    }
}