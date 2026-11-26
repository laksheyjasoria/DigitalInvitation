package com.app.templatestudio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Component
public class TemplateDefinitionMapper {
    public Map<String, Object> map(byte[] archive) {
        try {
            Map<String, byte[]> files = readPackage(archive);
            Element root = parse(files.get("template.xml")).getDocumentElement();
            Element previewRoot = parse(files.get("preview.xml")).getDocumentElement();
            List<Map<String, Object>> sections = new ArrayList<>();
            List<Map<String, Object>> previewSections = new ArrayList<>();
            NodeList sectionNodes = root.getElementsByTagName("section");
            for (int i = 0; i < sectionNodes.getLength(); i++) {
                Element section = (Element) sectionNodes.item(i);
                String sectionKey = required(section, "key");
                List<Map<String, Object>> fields = new ArrayList<>();
                List<String> requiredFields = new ArrayList<>();
                Map<String, Object> values = new LinkedHashMap<>();
                boolean repeatable = false;
                NodeList fieldNodes = section.getElementsByTagName("field");
                for (int j = 0; j < fieldNodes.getLength(); j++) {
                    Element field = (Element) fieldNodes.item(j);
                    String key = required(field, "key");
                    String type = required(field, "type");
                    boolean isRequired = Boolean.parseBoolean(field.getAttribute("required"));
                    boolean isRepeatable = Boolean.parseBoolean(field.getAttribute("repeatable")) || "repeatable".equals(type);
                    repeatable |= isRepeatable;
                    Map<String, Object> definition = new LinkedHashMap<>();
                    definition.put("key", key);
                    definition.put("label", required(field, "label"));
                    definition.put("type", normalizeType(type));
                    definition.put("required", isRequired);
                    definition.put("placeholder", field.getAttribute("placeholder"));
                    fields.add(definition);
                    if (isRequired) requiredFields.add(key);
                    String sample = childText(previewRoot, key);
                    if (!sample.isBlank()) values.put(key, sample);
                }
                boolean optional = Boolean.parseBoolean(section.getAttribute("optional"));
                Map<String, Object> sectionDefinition = new LinkedHashMap<>();
                sectionDefinition.put("key", sectionKey);
                sectionDefinition.put("label", required(section, "label"));
                sectionDefinition.put("enabled", true);
                sectionDefinition.put("required", !optional);
                sectionDefinition.put("optional", optional);
                sectionDefinition.put("repeatable", repeatable);
                sectionDefinition.put("fields", fields);
                sectionDefinition.put("requiredFields", requiredFields);
                sections.add(sectionDefinition);
                Map<String, Object> sampleSection = new LinkedHashMap<>();
                sampleSection.put("key", sectionKey);
                sampleSection.put("enabled", true);
                sampleSection.put("values", values);
                if (repeatable) sampleSection.put("items", List.of(values));
                previewSections.add(sampleSection);
            }
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("schemaVersion", 1);
            definition.put("templateId", required(root, "id"));
            definition.put("name", required(root, "name"));
            definition.put("version", required(root, "version"));
            definition.put("sections", sections);
            Map<String, Object> sampleContent = new LinkedHashMap<>();
            sampleContent.put("sections", previewSections);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("definition", definition);
            result.put("sampleContent", sampleContent);
            result.put("title", required(root, "name"));
            return result;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to map template XML to invitation form model", ex);
        }
    }

    private Map<String, byte[]> readPackage(byte[] archive) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && (entry.getName().equals("template.xml") || entry.getName().equals("preview.xml"))) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    zip.transferTo(out);
                    files.put(entry.getName(), out.toByteArray());
                }
                zip.closeEntry();
            }
        }
        if (!files.containsKey("template.xml") || !files.containsKey("preview.xml")) {
            throw new IllegalArgumentException("Template XML or preview XML is missing");
        }
        return files;
    }

    private org.w3c.dom.Document parse(byte[] xml) throws Exception {
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
        if (value.isBlank()) throw new IllegalArgumentException("Missing XML attribute: " + attribute);
        return value;
    }

    private String childText(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagName(name);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private String normalizeType(String type) {
        return switch (type.toLowerCase(java.util.Locale.ROOT)) {
            case "longtext", "multiline" -> "textarea";
            case "phone" -> "tel";
            case "location" -> "address";
            case "repeatable" -> "text";
            default -> type.toLowerCase(java.util.Locale.ROOT);
        };
    }
}
