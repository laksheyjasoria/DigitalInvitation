package com.app.templatestudio;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class TemplateDefinitionMapperTest {
    @Test
    void mapsTemplateXmlToCustomerFormDefinition() throws Exception {
        Map<String, Object> result = new TemplateDefinitionMapper().map(zip());
        Map<?, ?> definition = (Map<?, ?>) result.get("definition");
        assertEquals("TEMP0000001", definition.get("templateId"));
        var sections = (java.util.List<?>) definition.get("sections");
        assertTrue((Boolean) ((Map<?, ?>) sections.get(0)).get("required"));
        assertFalse((Boolean) ((Map<?, ?>) sections.get(1)).get("required"));
        Map<?, ?> sample = (Map<?, ?>) ((java.util.List<?>) ((Map<?, ?>) result.get("sampleContent")).get("sections")).get(0);
        assertEquals("Join us", ((Map<?, ?>) sample.get("values")).get("headline"));
        Map<?, ?> details = (Map<?, ?>) sections.get(1);
        assertEquals("2027-01-01", ((Map<?, ?>) ((Map<?, ?>) ((java.util.List<?>) ((Map<?, ?>) result.get("sampleContent")).get("sections")).get(1)).get("values")).get("date"));
        Map<?, ?> eventsSample = (Map<?, ?>) ((java.util.List<?>) ((Map<?, ?>) result.get("sampleContent")).get("sections")).get(2);
        var eventItems = (java.util.List<?>) eventsSample.get("items");
        assertEquals(2, eventItems.size());
        assertEquals("Ceremony", ((Map<?, ?>) eventItems.get(1)).get("eventName"));
    }

    @Test
    void includesActualTemplateFilesAndDataUriAssetsInPreview() throws Exception {
        Map<String, Object> result = new TemplateDefinitionMapper().mapPreview(zip());
        Map<?, ?> preview = (Map<?, ?>) result.get("previewFiles");
        assertTrue(((String) preview.get("html")).contains("invitation-root"));
        assertEquals("body{color:#333}", preview.get("css"));
        assertEquals("window.previewReady=true;", preview.get("js"));
        Map<?, ?> assets = (Map<?, ?>) preview.get("assets");
        assertTrue(((String) assets.get("assets/mark.svg")).startsWith("data:image/svg+xml;base64,"));
        assertTrue(((String) assets.get("assets/animation.js")).startsWith("data:application/javascript;base64,"));
    }

    private byte[] zip() throws Exception {
        String xml = "<template id=\"TEMP0000001\" name=\"Garden\" version=\"1.0\" schemaVersion=\"1\"><sections><section key=\"hero\" label=\"Invitation\"><field key=\"headline\" label=\"Heading\" type=\"text\" required=\"true\"/></section><section key=\"details\" label=\"Details\" optional=\"true\"><field key=\"date\" label=\"Date\" type=\"date\"/></section><section key=\"events\" label=\"Events\" repeatable=\"true\"><field key=\"eventName\" label=\"Function\" type=\"text\"/></section></sections></template>";
        String preview = "<preview><hero><headline>Join us</headline></hero><details><date>2027-01-01</date></details><events><item><eventName>Welcome</eventName></item><item><eventName>Ceremony</eventName></item></events></preview>";
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            add(zip, "template.xml", xml);
            add(zip, "preview.xml", preview);
            add(zip, "index.html", "<!doctype html><html><head><title>Test</title></head><body><main id=\"invitation-root\"></main></body></html>");
            add(zip, "styles.css", "body{color:#333}");
            add(zip, "script.js", "window.previewReady=true;");
            add(zip, "assets/mark.svg", "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>");
            add(zip, "assets/animation.js", "window.animationLoaded=true;");
            return bytes.toByteArray();
        }
    }

    private void add(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
