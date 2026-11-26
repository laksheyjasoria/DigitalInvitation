package com.app.templatestudio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Component
public class TemplateDefinitionMapper {
    public Map<String, Object> map(byte[] zipBytes) {
        try {
            Map<String, byte[]> files = readArchive(zipBytes);
            Element root = parse(files.get("template.xml")).getDocumentElement();
            Element sample = parse(files.get("preview.xml")).getDocumentElement();
            List<Map<String, Object>> sections = new java.util.ArrayList<>();
            List<Map<String, Object>> sampleSections = new java.util.ArrayList<>();
            NodeList sectionNodes = root.getElementsByTagName("section");

            for (int i = 0; i < sectionNodes.getLength(); i++) {
                Element section = (Element) sectionNodes.item(i);
                String key = required(section, "key");
                List<Map<String, Object>> fields = new java.util.ArrayList<>();
                List<String> requiredFields = new java.util.ArrayList<>();
                Map<String, Object> values = new LinkedHashMap<>();
                boolean repeatable = Boolean.parseBoolean(section.getAttribute("repeatable"));
                NodeList fieldNodes = section.getElementsByTagName("field");

                for (int j = 0; j < fieldNodes.getLength(); j++) {
                    Element field = (Element) fieldNodes.item(j);
                    String fieldKey = required(field, "key");
                    String type = required(field, "type");
                    boolean fieldRequired = Boolean.parseBoolean(field.getAttribute("required"));
                    repeatable |= Boolean.parseBoolean(field.getAttribute("repeatable")) || type.equals("repeatable");

                    Map<String, Object> mappedField = new LinkedHashMap<>();
                    mappedField.put("key", fieldKey);
                    mappedField.put("label", required(field, "label"));
                    mappedField.put("type", normalizeType(type));
                    mappedField.put("required", fieldRequired);
                    mappedField.put("optional", Boolean.parseBoolean(field.getAttribute("optional")));
                    mappedField.put("placeholder", field.getAttribute("placeholder"));
                    fields.add(mappedField);
                    if (fieldRequired) requiredFields.add(fieldKey);

                    NodeList sampleValues = sample.getElementsByTagName(fieldKey);
                    if (sampleValues.getLength() > 0 && !sampleValues.item(0).getTextContent().isBlank()) {
                        values.put(fieldKey, sampleValues.item(0).getTextContent().trim());
                    }
                }

                boolean optional = Boolean.parseBoolean(section.getAttribute("optional"));
                Map<String, Object> mappedSection = new LinkedHashMap<>();
                mappedSection.put("key", key);
                mappedSection.put("label", required(section, "label"));
                mappedSection.put("enabled", true);
                mappedSection.put("required", !optional);
                mappedSection.put("optional", optional);
                mappedSection.put("repeatable", repeatable);
                mappedSection.put("fields", fields);
                mappedSection.put("requiredFields", requiredFields);
                sections.add(mappedSection);

                Map<String, Object> sampleSection = new LinkedHashMap<>();
                sampleSection.put("key", key);
                sampleSection.put("enabled", true);
                sampleSection.put("values", values);
                if (repeatable) sampleSection.put("items", List.of(values));
                sampleSections.add(sampleSection);
            }

            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("schemaVersion", root.getAttribute("schemaVersion").isBlank() ? "1" : root.getAttribute("schemaVersion"));
            definition.put("templateId", required(root, "id"));
            definition.put("name", required(root, "name"));
            definition.put("version", required(root, "version"));
            definition.put("sections", sections);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("definition", definition);
            result.put("sampleContent", Map.of("sections", sampleSections));
            result.put("title", required(root, "name"));
            return result;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to map template XML", ex);
        }
    }

    public Map<String, Object> mapPreview(byte[] zipBytes) {
        Map<String, byte[]> files = readArchive(zipBytes);
        Map<String, Object> result = new LinkedHashMap<>(map(zipBytes));
        Map<String, String> assets = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            String name = entry.getKey();
            if (name.equals("index.html") || name.equals("styles.css") || name.equals("script.js")
                    || name.equals("template.xml") || name.equals("preview.xml")) continue;
            String mime = mimeType(name);
            if (mime != null) {
                assets.put(name, "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(entry.getValue()));
            }
        }
        Map<String, Object> previewFiles = new LinkedHashMap<>();
        previewFiles.put("html", textFile(files, "index.html"));
        previewFiles.put("css", textFile(files, "styles.css"));
        previewFiles.put("js", textFile(files, "script.js"));
        previewFiles.put("assets", assets);
        result.put("previewFiles", previewFiles);
        return result;
    }

    private Map<String, byte[]> readArchive(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) throw new IllegalArgumentException("Template package is empty");
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    zip.transferTo(output);
                    files.put(entry.getName(), output.toByteArray());
                }
                zip.closeEntry();
            }
            return files;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to read template package preview", ex);
        }
    }

    private String textFile(Map<String, byte[]> files, String name) {
        byte[] value = files.get(name);
        if (value == null) throw new IllegalArgumentException("Template package is missing " + name);
        return new String(value, StandardCharsets.UTF_8);
    }

    private String mimeType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".avif")) return "image/avif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".woff2")) return "font/woff2";
        if (lower.endsWith(".ttf")) return "font/ttf";
        if (lower.endsWith(".otf")) return "font/otf";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".txt")) return "text/plain";
        return null;
    }

    private String normalizeType(String type) {
        return switch (type) {
            case "longtext" -> "textarea";
            case "phone" -> "tel";
            case "location" -> "address";
            case "repeatable" -> "text";
            default -> type;
        };
    }

    private Document parse(byte[] xml) throws Exception {
        if (xml == null) throw new IllegalArgumentException("Missing XML file");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private String required(Element element, String attribute) {
        String value = element.getAttribute(attribute).trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Missing XML attribute " + attribute);
        return value;
    }
}
