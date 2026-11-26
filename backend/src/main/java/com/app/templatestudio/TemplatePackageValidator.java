package com.app.templatestudio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

@Component
public class TemplatePackageValidator {
    private static final int MAX_ARCHIVE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_EXPANDED_BYTES = 25 * 1024 * 1024;
    private static final int MAX_ENTRIES = 150;
    private static final Set<String> REQUIRED = Set.of("index.html", "styles.css", "script.js", "template.xml", "preview.xml");
    private final TemplateDefinitionMapper definitionMapper;

    public TemplatePackageValidator(TemplateDefinitionMapper definitionMapper) {
        this.definitionMapper = definitionMapper;
    }

    public ValidationResult validate(byte[] archive) {
        if (archive == null || archive.length == 0 || archive.length > MAX_ARCHIVE_BYTES) {
            throw new IllegalArgumentException("Template ZIP must be between 1 byte and 10 MB");
        }
        Set<String> names = new HashSet<>();
        byte[] definition = null;
        byte[] preview = null;
        int count = 0;
        int expanded = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_ENTRIES) throw new IllegalArgumentException("Template ZIP contains too many files");
                String name = entry.getName();
                validatePath(name);
                if (entry.isDirectory()) continue;
                if (!names.add(name)) throw new IllegalArgumentException("Duplicate ZIP entry: " + name);
                validateFileType(name);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > MAX_EXPANDED_BYTES) throw new IllegalArgumentException("Expanded template package exceeds 25 MB");
                    out.write(buffer, 0, read);
                }
                if (name.equals("template.xml")) definition = out.toByteArray();
                if (name.equals("preview.xml")) preview = out.toByteArray();
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read template ZIP", ex);
        }
        if (!names.containsAll(REQUIRED)) {
            Set<String> missing = new HashSet<>(REQUIRED);
            missing.removeAll(names);
            throw new IllegalArgumentException("Template package is missing: " + String.join(", ", missing));
        }
        try {
            Document document = parseAndValidateDefinition(definition);
            Document previewDocument = parseSecurely(preview);
            if (!"preview".equals(previewDocument.getDocumentElement().getTagName()))
                throw new IllegalArgumentException("preview.xml must have a <preview> root element");
            definitionMapper.map(archive);
            String id = document.getDocumentElement().getAttribute("id");
            String name = document.getDocumentElement().getAttribute("name");
            String version = document.getDocumentElement().getAttribute("version");
            if (!id.matches("TEMP[0-9]{7}") || name.isBlank() || name.length() > 160
                    || !version.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,39}")) {
                throw new IllegalArgumentException("template.xml requires a TEMP0000001 ID, a name up to 160 characters and a safe version string");
            }
            return new ValidationResult(id, name, version, count, expanded, sha256(archive));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Template XML validation failed: " + ex.getMessage(), ex);
        }
    }

    private Document parseAndValidateDefinition(byte[] xml) throws Exception {
        var schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        schemaFactory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var schema = schemaFactory.newSchema(new ClassPathResource("templates/template.xsd").getURL());
        var builderFactory = secureFactory();
        var builder = builderFactory.newDocumentBuilder();
        var document = builder.parse(new ByteArrayInputStream(xml));
        schema.newValidator().validate(new StreamSource(new ByteArrayInputStream(xml)));
        return document;
    }

    private Document parseSecurely(byte[] xml) throws Exception {
        return secureFactory().newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private DocumentBuilderFactory secureFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    private void validatePath(String name) {
        if (name == null || name.isBlank() || name.length() > 180 || name.startsWith("/") || name.startsWith("\\")
                || name.contains("\\") || name.contains(":") || name.matches("^[A-Za-z]:.*")
                || name.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Unsafe ZIP path");
        }
        for (String segment : name.split("/", -1)) {
            if (segment.equals("..") || segment.equals(".") || segment.isBlank()) {
                throw new IllegalArgumentException("Unsafe ZIP path: " + name);
            }
        }
    }

    private void validateFileType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".exe") || lower.endsWith(".dll") || lower.endsWith(".class")
                || lower.endsWith(".jar") || lower.endsWith(".sh") || lower.endsWith(".bat")
                || lower.endsWith(".ps1") || lower.endsWith(".php") || lower.endsWith(".jsp")) {
            throw new IllegalArgumentException("Executable or server-side files are not allowed: " + name);
        }
        if (!(lower.endsWith(".html") || lower.endsWith(".css") || lower.endsWith(".js")
                || lower.endsWith(".xml") || lower.endsWith(".svg") || lower.endsWith(".png")
                || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")
                || lower.endsWith(".woff2") || lower.endsWith(".ttf") || lower.endsWith(".otf")
                || lower.endsWith(".gif") || lower.endsWith(".avif") || lower.endsWith(".mp4")
                || lower.endsWith(".webm") || lower.endsWith(".mp3") || lower.endsWith(".ogg")
                || lower.endsWith(".json") || lower.endsWith(".txt"))) {
            throw new IllegalArgumentException("Unsupported template asset type: " + name);
        }
    }

    private String sha256(byte[] value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
        StringBuilder result = new StringBuilder();
        for (byte b : digest) result.append(String.format("%02x", b));
        return result.toString();
    }

    public record ValidationResult(String templateId, String templateName, String version,
                                   int fileCount, int expandedBytes, String sha256) {}
}
