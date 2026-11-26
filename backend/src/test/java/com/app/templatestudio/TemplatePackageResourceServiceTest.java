package com.app.templatestudio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ResourceLoader;

class TemplatePackageResourceServiceTest {
    @Test
    void loadsPublishedPackageFilesFromTheClasspathResourceLocation() throws Exception {
        String path = "templates/packages/TEMP0000001/1.0.0.zip";
        ResourceLoader loader = mock(ResourceLoader.class);
        when(loader.getResource("classpath:" + path)).thenReturn(new ByteArrayResource(packageZip()));
        TemplatePackageResourceService service = new TemplatePackageResourceService(loader, new TemplateDefinitionMapper());

        var preview = service.previewFiles(path);

        assertEquals("<html><body><h1>Invitation</h1></body></html>", preview.get("html"));
        assertEquals("body{margin:0}", preview.get("css"));
        assertEquals("window.templateLoaded=true;", preview.get("js"));
        assertTrue(service.isAvailable(path));
    }

    @Test
    void rejectsUntrustedClasspathResourcePaths() {
        ResourceLoader loader = mock(ResourceLoader.class);
        TemplatePackageResourceService service = new TemplatePackageResourceService(loader, new TemplateDefinitionMapper());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.previewFiles("templates/../../application.yml"));
    }

    private byte[] packageZip() throws Exception {
        String definition = "<template id=\"TEMP0000001\" name=\"Test\" version=\"1.0.0\" schemaVersion=\"1\"><sections><section key=\"hero\" label=\"Hero\"><field key=\"headline\" label=\"Headline\" type=\"text\"/></section></sections></template>";
        String preview = "<preview><hero><headline>Hello</headline></hero></preview>";
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            add(zip, "template.xml", definition);
            add(zip, "preview.xml", preview);
            add(zip, "index.html", "<html><body><h1>Invitation</h1></body></html>");
            add(zip, "styles.css", "body{margin:0}");
            add(zip, "script.js", "window.templateLoaded=true;");
            return bytes.toByteArray();
        }
    }

    private void add(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
