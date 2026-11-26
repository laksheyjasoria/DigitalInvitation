package com.app.templatestudio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class TemplateDefinitionMapperTest {
    private final TemplateDefinitionMapper mapper = new TemplateDefinitionMapper();

    @Test
    void mapsXmlFieldsAndSampleValuesToInvitationEditorModel() throws Exception {
        String definition = """
            <?xml version="1.0"?>
            <template id="TEMP0000001" name="Garden" version="1.2.0" schemaVersion="1">
              <sections>
                <section key="hero" label="Invitation">
                  <field key="headline" label="Heading" type="text" required="true"/>
                  <field key="intro" label="Message" type="textarea" optional="true"/>
                </section>
                <section key="schedule" label="Schedule" optional="true">
                  <field key="eventDate" label="Date" type="date" required="true"/>
                </section>
              </sections>
            </template>
            """;
        String preview = """
            <?xml version="1.0"?>
            <preview><headline>Join us</headline><intro>Celebrate together</intro><eventDate>2027-02-14</eventDate></preview>
            """;
        Map<String, Object> result = mapper.map(zip(definition, preview));
        Map<?, ?> model = (Map<?, ?>) result.get("definition");
        assertEquals("TEMP0000001", model.get("templateId"));
        assertEquals("1.2.0", model.get("version"));
        var sections = (java.util.List<?>) model.get("sections");
        Map<?, ?> hero = (Map<?, ?>) sections.get(0);
        assertTrue((Boolean) hero.get("required"));
        assertFalse((Boolean) ((Map<?, ?>) sections.get(1)).get("required"));
        var fields = (java.util.List<?>) hero.get("fields");
        assertEquals("headline", ((Map<?, ?>) fields.get(0)).get("key"));
        Map<?, ?> content = (Map<?, ?>) result.get("sampleContent");
        var sampleSections = (java.util.List<?>) content.get("sections");
        Map<?, ?> sampleHero = (Map<?, ?>) sampleSections.get(0);
        Map<?, ?> values = (Map<?, ?>) sampleHero.get("values");
        assertEquals("Join us", values.get("headline"));
        assertEquals("Celebrate together", values.get("intro"));
    }

    private byte[] zip(String definition, String preview) throws Exception {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream out = new ZipOutputStream(bytes)) {
            add(out, "template.xml", definition);
            add(out, "preview.xml", preview);
            return bytes.toByteArray();
        }
    }

    private void add(ZipOutputStream out, String name, String value) throws Exception {
        out.putNextEntry(new ZipEntry(name));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
