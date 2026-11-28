package com.app.templatestudio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class TemplatePackageValidatorTest {
    private final TemplatePackageValidator validator = new TemplatePackageValidator(new TemplateDefinitionMapper());

    @Test
    void acceptsValidTemplatePackage() throws Exception {
        var result = validator.validate(packageZip(definition(), preview(), false));
        assertEquals("TEMP0000001", result.templateId());
        assertEquals("Floral Garden", result.templateName());
        assertEquals("1.0.0", result.version());
        assertEquals(7, result.fileCount());
        assertEquals(64, result.sha256().length());
    }

    @Test
    void rejectsTemplateWithoutNamedLocationQrSlot() {
        byte[] archive = packageZip(definition(), preview(), false, "<!doctype html><html><body>Preview</body></html>");
        assertThrows(IllegalArgumentException.class, () -> validator.validate(archive));
    }

    @Test
    void rejectsUnsafeTemplatePackageVersion() {
        String unsafe = definition().replace("version=\"1.0.0\"", "version=\"../latest\"");
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(packageZip(unsafe, preview(), false)));
    }

    @Test
    void rejectsUnsupportedXmlFieldTypesBeforeStaging() {
        String invalid = definition().replace("type=\"text\"", "type=\"widget\"");
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(packageZip(invalid, preview(), false)));
    }

    @Test
    void rejectsArchivePathTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(packageZip(definition(), preview(), true)));
    }

    @Test
    void rejectsXmlExternalEntities() {
        String xxe = "<!DOCTYPE template [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>" + definition();
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(packageZip(xxe, preview(), false)));
    }

    @Test
    void rejectsMissingRequiredFiles() throws Exception {
        byte[] zip = new byte[0];
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream out = new ZipOutputStream(bytes)) {
            add(out, "template.xml", definition());
            zip = bytes.toByteArray();
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
        byte[] finalZip = zip;
        assertThrows(IllegalArgumentException.class, () -> validator.validate(finalZip));
    }

    private byte[] packageZip(String xml, String preview, boolean traversal) {
        return packageZip(xml, preview, traversal, "<!doctype html><html><body><img data-location-qr alt=\"Event location QR\"></body></html>");
    }

    private byte[] packageZip(String xml, String preview, boolean traversal, String html) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream out = new ZipOutputStream(bytes)) {
            add(out, "index.html", html);
            add(out, "styles.css", "body{margin:0}");
            add(out, "script.js", "void 0;");
            add(out, "template.xml", xml);
            add(out, "preview.xml", preview);
            add(out, "assets/loop.gif", "GIF89a");
            add(out, "animations/sequence.json", "{\"frames\":[]}");
            if (traversal) add(out, "../outside.txt", "bad");
            return bytes.toByteArray();
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    private void add(ZipOutputStream out, String name, String value) throws Exception {
        out.putNextEntry(new ZipEntry(name));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }

    private String definition() {
        return "<?xml version=\"1.0\"?><template id=\"TEMP0000001\" name=\"Floral Garden\" version=\"1.0.0\" schemaVersion=\"1\"><sections><section key=\"hero\" label=\"Invitation\"><field key=\"headline\" label=\"Heading\" type=\"text\" required=\"true\"/></section></sections></template>";
    }

    private String preview() {
        return "<?xml version=\"1.0\"?><preview><headline>Sample</headline></preview>";
    }
}
