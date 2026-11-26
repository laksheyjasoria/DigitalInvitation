package com.app.templatestudio;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.Map;import java.util.zip.*;
import org.junit.jupiter.api.Test;
class TemplateDefinitionMapperTest{
 @Test void mapsTemplateXmlToCustomerFormDefinition()throws Exception{
  String xml="<template id=\"TEMP0000001\" name=\"Garden\" version=\"1.0\" schemaVersion=\"1\"><sections><section key=\"hero\" label=\"Invitation\"><field key=\"headline\" label=\"Heading\" type=\"text\" required=\"true\"/></section><section key=\"details\" label=\"Details\" optional=\"true\"><field key=\"date\" label=\"Date\" type=\"date\"/></section></sections></template>";
  String preview="<preview><headline>Join us</headline><date>2027-01-01</date></preview>";
  Map<String,Object> result=new TemplateDefinitionMapper().map(zip(xml,preview));
  Map<?,?> definition=(Map<?,?>)result.get("definition");assertEquals("TEMP0000001",definition.get("templateId"));
  var sections=(java.util.List<?>)definition.get("sections");assertTrue((Boolean)((Map<?,?>)sections.get(0)).get("required"));assertFalse((Boolean)((Map<?,?>)sections.get(1)).get("required"));
  Map<?,?> sample=(Map<?,?>)((java.util.List<?>)((Map<?,?>)result.get("sampleContent")).get("sections")).get(0);
  assertEquals("Join us",((Map<?,?>)sample.get("values")).get("headline"));
 }
 private byte[] zip(String xml,String preview)throws Exception{try(ByteArrayOutputStream b=new ByteArrayOutputStream();ZipOutputStream z=new ZipOutputStream(b)){add(z,"template.xml",xml);add(z,"preview.xml",preview);return b.toByteArray();}}
 private void add(ZipOutputStream z,String name,String value)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(value.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
}