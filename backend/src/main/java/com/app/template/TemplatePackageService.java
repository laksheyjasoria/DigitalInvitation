package com.app.template;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@Service
public class TemplatePackageService {
    private static final Set<String> FIELD_TYPES = Set.of("text","textarea","date","time","datetime","image","phone","email","url","address","select","boolean","number");
    private final ResourceLoader resources;
    private volatile Schema schema;

    public TemplatePackageService(ResourceLoader resources) { this.resources = resources; }

    public List<Map<String,Object>> listTemplates() {
        try {
            Document catalog = parse(resource("classpath:/templates/catalog.xml"), null);
            NodeList entries = catalog.getDocumentElement().getElementsByTagName("template");
            List<Map<String,Object>> result = new ArrayList<>();
            for (int i=0;i<entries.getLength();i++) {
                String id=((Element)entries.item(i)).getAttribute("id");
                Map<String,Object> t=getTemplate(id);
                result.add(Map.of("id",id,"displayName",t.get("displayName"),"category",t.get("category"),
                    "description",t.get("description"),"version",t.get("version"),"previewUrl",t.get("previewUrl")));
            }
            return result;
        } catch (TemplatePackageException ex) { throw ex; }
        catch (Exception ex) { throw new TemplatePackageException("Template catalogue could not be loaded",ex); }
    }

    public Map<String,Object> getTemplate(String id) {
        validateId(id);
        try {
            Document doc=parse(resource("classpath:/templates/"+id+"/template.xml"),templateSchema());
            Element root=doc.getDocumentElement();
            if(!id.equals(root.getAttribute("id"))) throw new TemplatePackageException("Template package ID does not match its directory");

            Map<String,Map<String,Object>> fields=new LinkedHashMap<>();
            NodeList fieldNodes=root.getElementsByTagName("field");
            for(int i=0;i<fieldNodes.getLength();i++) {
                Element field=(Element)fieldNodes.item(i);
                if(!field.hasAttribute("key")) continue;
                String key=field.getAttribute("key"), type=field.getAttribute("type");
                if(!key.matches("[A-Za-z][A-Za-z0-9_-]{0,79}") || !FIELD_TYPES.contains(type))
                    throw new TemplatePackageException("Invalid field key or unsupported field type in "+id);
                Map<String,Object> item=new LinkedHashMap<>();
                item.put("key",key); item.put("label",field.getAttribute("label")); item.put("type",type);
                item.put("required",bool(field,"required",false)); item.put("editable",bool(field,"editable",true));
                item.put("removable",bool(field,"removable",false));
                if(field.hasAttribute("placeholder")) item.put("placeholder",field.getAttribute("placeholder"));
                if(fields.putIfAbsent(key,item)!=null) throw new TemplatePackageException("Duplicate field key: "+key);
            }

            List<Map<String,Object>> sections=new ArrayList<>();
            Set<String> sectionKeys=new LinkedHashSet<>();
            NodeList sectionNodes=root.getElementsByTagName("section");
            for(int i=0;i<sectionNodes.getLength();i++) {
                Element section=(Element)sectionNodes.item(i);
                String key=section.getAttribute("key");
                if(!key.matches("[A-Za-z][A-Za-z0-9_-]{0,79}") || !sectionKeys.add(key))
                    throw new TemplatePackageException("Invalid or duplicate section key: "+key);
                List<String> refs=new ArrayList<>();
                NodeList refNodes=section.getElementsByTagName("field");
                for(int j=0;j<refNodes.getLength();j++) {
                    String ref=((Element)refNodes.item(j)).getAttribute("ref");
                    if(!fields.containsKey(ref)) throw new TemplatePackageException("Section "+key+" references unknown field "+ref);
                    refs.add(ref);
                }
                Map<String,Object> value=new LinkedHashMap<>();
                value.put("key",key); value.put("label",section.getAttribute("label"));
                value.put("required",bool(section,"required",false)); value.put("enabled",bool(section,"enabled",true));
                value.put("repeatable",bool(section,"repeatable",false)); value.put("fields",refs);
                value.put("requiredFields",refs.stream().filter(k->Boolean.TRUE.equals(fields.get(k).get("required"))).toList());
                sections.add(value);
            }
            verifyPackageFiles(id);
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("id",id); result.put("displayName",root.getAttribute("displayName"));
            result.put("category",root.getAttribute("category")); result.put("description",root.getAttribute("description"));
            result.put("version",root.getAttribute("version")); result.put("layout",root.getAttribute("layout"));
            result.put("fields",new ArrayList<>(fields.values())); result.put("sections",sections);
            result.put("customSectionsAllowed",false); result.put("previewUrl","/template-preview/"+id);
            result.put("previewData",getPreviewData(id));
            return result;
        } catch(TemplatePackageException ex) { throw ex; }
        catch(Exception ex) { throw new TemplatePackageException("Template package "+id+" is invalid",ex); }
    }

    public Map<String,Object> getPreviewData(String id) {
        validateId(id);
        try {
            Document doc=parse(resource("classpath:/templates/"+id+"/preview.xml"),null);
            Element root=doc.getDocumentElement();
            Map<String,Object> result=new LinkedHashMap<>();
            result.put("title",root.getAttribute("title")); result.put("subtitle",root.getAttribute("subtitle"));
            Map<String,String> values=new LinkedHashMap<>();
            NodeList nodes=root.getElementsByTagName("value");
            for(int i=0;i<nodes.getLength();i++) {
                Element e=(Element)nodes.item(i); values.put(e.getAttribute("field"),e.getTextContent());
            }
            result.put("values",values); return result;
        } catch(Exception ex) { throw new TemplatePackageException("Template preview data could not be loaded",ex); }
    }

    public Resource previewPage(String id) {
        validateId(id);
        Resource page=resource("classpath:/templates/"+id+"/index.html");
        if(!page.exists()) throw new TemplatePackageException("Template preview page was not found");
        return page;
    }

    public Resource asset(String id,String name) {
        validateId(id);
        if(!Set.of("styles.css","script.js").contains(name)) throw new TemplatePackageException("Unsupported template asset");
        Resource asset=resource("classpath:/templates/"+id+"/"+name);
        if(!asset.exists()) throw new TemplatePackageException("Template asset was not found");
        return asset;
    }

    private void verifyPackageFiles(String id) {
        for(String name:List.of("index.html","styles.css","script.js","preview.xml"))
            if(!resource("classpath:/templates/"+id+"/"+name).exists()) throw new TemplatePackageException("Template package is missing "+name);
    }
    private Resource resource(String path) { return resources.getResource(path); }
    private Schema templateSchema() throws Exception {
        if(schema==null) synchronized(this) {
            if(schema==null) {
                SchemaFactory factory=SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
                try(InputStream in=resource("classpath:/templates/template.xsd").getInputStream()) {
                    schema=factory.newSchema(new javax.xml.transform.stream.StreamSource(in));
                }
            }
        }
        return schema;
    }
    private Document parse(Resource resource,Schema validationSchema) throws Exception {
        if(!resource.exists()) throw new TemplatePackageException("Required template package file is missing");
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false); factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        if(validationSchema!=null) factory.setSchema(validationSchema);
        try(InputStream in=resource.getInputStream()) { return factory.newDocumentBuilder().parse(in); }
        catch(SAXException ex) { throw new TemplatePackageException("Template XML failed schema validation",ex); }
    }
    private static boolean bool(Element e,String name,boolean fallback) { return e.hasAttribute(name)?Boolean.parseBoolean(e.getAttribute(name)):fallback; }
    private static void validateId(String id) {
        if(id==null || !id.matches("TEMP[0-9]{7}")) throw new TemplatePackageException("Invalid template package ID");
    }
}