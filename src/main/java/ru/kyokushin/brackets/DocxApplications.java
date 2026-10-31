package ru.kyokushin.brackets;

import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.XMLConstants;
import java.io.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.*;
import java.util.*;
import java.util.zip.*;

/** Reads table cells only. No macros, external XML resources or document instructions are executed. */
@Component
class DocxApplications {
 static final int MAX_XML=12*1024*1024;
 static final String NS="http://schemas.openxmlformats.org/wordprocessingml/2006/main";
 record Entry(int sourceRow,String name,String club,LocalDate birthDate,String sex,
              BigDecimal weight,Integer belt,Integer kyu,String coach,String sportRank) {}
 record Parsed(List<Entry> entries,List<String> warnings) {}

 Parsed parse(byte[] bytes,String team) {
  TournamentService.require(bytes.length<=10*1024*1024,"Размер файла превышает 10 МБ");
  try {
   byte[] xml=null;
   try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
    int count=0,total=0;for(ZipEntry e;(e=zip.getNextEntry())!=null;) {
     if(++count>2000)throw new IOException("Too many archive entries");
     if("word/document.xml".equals(e.getName())) {
      xml=zip.readNBytes(MAX_XML+1);if(xml.length>MAX_XML)throw new IOException("Document too large");break;
     } else {total+=zip.readNBytes(MAX_XML+1).length;if(total>MAX_XML)throw new IOException("Archive expansion too large");}
    }
   }
   TournamentService.require(xml!=null,"Файл не является документом DOCX");
   var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
   factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
   factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
   factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
   factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
   var document=factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
   List<Entry> entries=new ArrayList<>();List<String> warnings=new ArrayList<>();
   var tables=document.getElementsByTagNameNS(NS,"tbl");
   for(int ti=0;ti<tables.getLength();ti++) {
    Map<String,Integer> headers=null;int sourceRow=0;
    for(Element tr:children((Element)tables.item(ti),"tr")) {
     sourceRow++;List<String> cells=children(tr,"tc").stream().map(DocxApplications::text).toList();
     Map<String,Integer> detected=header(cells);
     if(detected.containsKey("name")&&detected.containsKey("birthDate")) {
      headers=detected;continue;
     }
     if(headers==null)continue;
     String name=get(cells,headers,"name");
     if(name.isBlank()||name.equalsIgnoreCase("ФИО"))continue;
     String dob=get(cells,headers,"birthDate"),weight=get(cells,headers,"weight"),kyu=get(cells,headers,"kyu");
     LocalDate birth=null;BigDecimal kg=null;Integer grade=null,belt=null;
     try{birth=LocalDate.parse(dob,DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT));}
     catch(Exception ignored){try{birth=LocalDate.parse(dob);}catch(Exception ignoredAgain){}}
     try{kg=new BigDecimal(weight.replace(",",".").replaceAll("(?iu)\\s*(кг|kg)\\s*","").strip());}catch(Exception ignored){}
     try{grade=Integer.valueOf(kyu.strip());belt=beltFromKyu(grade);}catch(Exception ignored){}
     String sex=normalize(get(cells,headers,"sex"));
     sex=switch(sex){case "м","муж","мужской","мальчик","юноша","m"->"M";case "ж","д","жен","женский","девочка","девушка","f"->"F";default->"";};
     String club=get(cells,headers,"club");if(club.isBlank())club=team.strip();
     entries.add(new Entry(sourceRow,name,club,birth,sex,kg,belt,grade,get(cells,headers,"coach"),get(cells,headers,"sportRank")));
     TournamentService.require(entries.size()<=500,"В одной заявке допускается не более 500 участников");
    }
   }
   TournamentService.require(!entries.isEmpty(),"Таблица участников не найдена. Нужны столбцы ФИО, дата рождения, пол, точный вес и Кю.");
   warnings.add("Возраст определяется на дату выбранного турнира. Категории берутся из его настроек, а не из названия документа.");
   warnings.add("Кю: 0 и 11 — белый; 10–9 — оранжевый; 8–7 — синий; 6–5 — жёлтый; 4–3 — зелёный; 2–1 — коричневый. Исходный Кю сохраняется.");
   return new Parsed(entries,warnings);
  } catch(org.springframework.web.server.ResponseStatusException e){throw e;}
  catch(Exception e){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Не удалось прочитать DOCX. Проверьте формат и целостность файла.");}
 }

 static Integer beltFromKyu(int kyu) {return switch(kyu){case 0,11->0;case 9,10->1;case 7,8->2;case 5,6->3;case 3,4->4;case 1,2->5;default->null;};}
 static String normalize(String s){return s.toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("[^\\p{L}\\p{N}]","");}
 static Map<String,Integer> header(List<String> cells) {
  Map<String,Integer> h=new HashMap<>();
  for(int i=0;i<cells.size();i++) {
   String t=normalize(cells.get(i));
   if(t.contains("фамилия")&&t.contains("имя")||t.equals("фио")||t.startsWith("фиоспортсмен"))h.put("name",i);
   if(t.startsWith("датарожд"))h.put("birthDate",i);
   if(t.equals("пол")||t.startsWith("полмж"))h.put("sex",i);
   if(t.startsWith("точныйвес")||t.equals("вес")||t.equals("вескг"))h.put("weight",i);
   if(t.equals("кю")||t.startsWith("кюдан"))h.put("kyu",i);
   if(t.contains("фиотренер")||t.equals("тренер"))h.put("coach",i);
   if(t.contains("разряд"))h.put("sportRank",i);
   if(t.contains("команда")||t.contains("клуб"))h.put("club",i);
  }
  return h;
 }
 static String get(List<String> cells,Map<String,Integer> headers,String key){Integer i=headers.get(key);return i==null||i>=cells.size()?"":cells.get(i).strip();}
 static List<Element> children(Element e,String tag){List<Element> result=new ArrayList<>();for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element el&&NS.equals(el.getNamespaceURI())&&tag.equals(el.getLocalName()))result.add(el);return result;}
 static String text(Element e){StringBuilder s=new StringBuilder();var ts=e.getElementsByTagNameNS(NS,"t");for(int i=0;i<ts.getLength();i++)s.append(ts.item(i).getTextContent()).append(' ');return s.toString().replaceAll("\\s+"," ").strip();}
}
