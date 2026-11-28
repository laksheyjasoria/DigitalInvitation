package com.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
/** Reads runtime business rules from versioned database settings. */
public class SystemSettingService {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public SystemSettingService(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 public String getString(String key){return read(key,String.class);}
 public int getInt(String key){return read(key,Integer.class);}
 public long getLong(String key){return read(key,Long.class);}
 public boolean getBoolean(String key){return read(key,Boolean.class);}
 public java.math.BigDecimal getDecimal(String key){return read(key,java.math.BigDecimal.class);}
 public <T>T read(String key,Class<T> type){
  String json=jdbc.query("SELECT setting_value::text FROM system_setting WHERE setting_key=?",rs->rs.next()?rs.getString(1):null,key);
  if(json==null)throw new IllegalStateException("Required system setting is missing: "+key);
  try{return mapper.readValue(json,type);}catch(Exception e){throw new IllegalStateException("System setting has invalid value: "+key,e);}
 }
 public List<String> getStringList(String key){
  String json=jdbc.query("SELECT setting_value::text FROM system_setting WHERE setting_key=?",rs->rs.next()?rs.getString(1):null,key);
  if(json==null)throw new IllegalStateException("Required system setting is missing: "+key);
  try{return mapper.readValue(json,mapper.getTypeFactory().constructCollectionType(List.class,String.class));}
  catch(Exception e){throw new IllegalStateException("System setting has invalid value: "+key,e);}
 }
}
