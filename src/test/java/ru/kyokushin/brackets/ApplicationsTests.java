package ru.kyokushin.brackets;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import java.io.*;
import java.util.zip.*;
import ru.kyokushin.brackets.DocxApplications.Entry;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${test.jdbc.url:jdbc:h2:mem:applications;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}","spring.datasource.username=${test.jdbc.username:sa}","spring.datasource.password=${test.jdbc.password:}"})
@AutoConfigureMockMvc @Transactional
class ApplicationsTests {
 @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;@Autowired DocxApplications parser;@Autowired ApplicationImports imports;@Autowired TournamentService tournaments;@Autowired RegistrationBatches batches;@Autowired MockMvc mvc;
 Long tournament(boolean categories){var t=tournaments.create(new Inputs.TournamentInput("Турнир заявок",LocalDate.of(2026,10,18),"Зал"));if(categories)for(String sex:List.of("M","F"))tournaments.category(t.id,new Inputs.CategoryInput(sex,sex,4,18,BigDecimal.ZERO,new BigDecimal(500),0,6));return t.id;}
 Entry entry(String name){return new Entry(2,name,"Команда",LocalDate.of(2018,2,16),"M",new BigDecimal("29.7"),2,8,"ФИО тренера","3 юн");}
 static String xml(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
 static byte[] docx(List<List<String>> rows) throws Exception {
  StringBuilder body=new StringBuilder("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:tbl>");
  for(var row:rows){body.append("<w:tr>");for(String cell:row)body.append("<w:tc><w:p><w:r><w:t>").append(xml(cell)).append("</w:t></w:r></w:p></w:tc>");body.append("</w:tr>");}
  body.append("</w:tbl></w:body></w:document>");return zipped(body.toString());
 }
 static byte[] zipped(String document) throws Exception {var buffer=new ByteArrayOutputStream();try(var z=new ZipOutputStream(buffer)){z.putNextEntry(new ZipEntry("word/document.xml"));z.write(document.getBytes(java.nio.charset.StandardCharsets.UTF_8));z.closeEntry();}return buffer.toByteArray();}
 static List<String> headers(){return List.of("№","Пол (м/ж)","Фамилия Имя Отчество спортсмена","Дата рождения (ДД.ММ.ГГГГ)","Разряд","Кю","Дисциплина (весовая категория)","Точный вес","ФИО тренера (ов)","Виза врача");}
 @Test void suppliedDocxFormatParsesCommaWeightFemaleDAndKyu() throws Exception {
  byte[] bytes=docx(List.of(headers(),List.of("1","Д","Тестова Анна","16.02.2018","3 юн","8","Весовая категория","29,7","Тренер Один",""),List.of("","М","Тестов Иван","01.01.2020","-","0","Весовая категория","19,4","Тренер Два","")));
  var result=parser.parse(bytes,"Команда");assertEquals(2,result.entries().size());var e=result.entries().get(0);assertEquals("F",e.sex());assertEquals(new BigDecimal("29.7"),e.weight());assertEquals(2,e.belt());assertEquals("Тренер Один",e.coach());assertEquals(0,result.entries().get(1).belt());
 }
 @Test void actualUserDocumentCanBeVerifiedWithoutImportingPrivateData() throws Exception {
  String path=System.getProperty("test.application.docx");if(path==null)return;
  var parsed=parser.parse(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path)),"Команда проверки");assertEquals(30,parsed.entries().size());assertTrue(parsed.entries().stream().allMatch(e->e.birthDate()!=null&&e.belt()!=null&&e.weight()!=null&&!e.sex().isBlank()));Long id=tournament(true);assertTrue(imports.check(id,parsed.entries(),List.of()).rows().stream().allMatch(r->r.categoryId()!=null&&r.errors().isEmpty()));assertTrue(tournaments.state(id).athletes().isEmpty());
 }
 @Test void previewIsReadOnlyAndConfirmAssignsAndPreservesApplicationDetails() {
  Long id=tournament(true);var preview=imports.check(id,List.of(entry("Тест Один")),List.of());assertTrue(preview.rows().get(0).errors().isEmpty());assertTrue(tournaments.state(id).athletes().isEmpty());
  var result=imports.confirm(id,new ApplicationImports.Request("team.docx","Команда",List.of(entry("Тест Один"))));assertEquals(1,result.accepted());
  var state=tournaments.state(id);var a=state.athletes().get(0);assertNotNull(state.assignments().get(a.id));assertEquals(8,a.kyu);assertEquals("ФИО тренера",a.coach);assertEquals("3 юн",a.sportRank);assertEquals(result.applicationId(),a.applicationId);
 }
 @Test void duplicateImportIsRejectedAndNothingIsPartiallyAdded() {
  Long id=tournament(true);imports.confirm(id,new ApplicationImports.Request("first.docx","Команда",List.of(entry("Тест Один"))));
  assertThrows(Exception.class,()->imports.confirm(id,new ApplicationImports.Request("again.docx","Команда",List.of(entry("Новый спортсмен"),entry("Тест Один")))));
  assertEquals(1,tournaments.state(id).athletes().size());assertEquals(1,batches.findByTournamentIdOrderByIdDesc(id).size());
 }
 @Test void unmatchedAndBadRowsAreExplainedAndNeverInserted() {
  Long id=tournament(false);var preview=imports.check(id,List.of(entry("Без категории")),List.of());assertNull(preview.rows().get(0).categoryId());assertFalse(preview.rows().get(0).errors().isEmpty());
  assertThrows(Exception.class,()->imports.confirm(id,new ApplicationImports.Request("a.docx","Команда",List.of(entry("Без категории")))));assertTrue(tournaments.state(id).athletes().isEmpty());
 }
 @Test void drawLocksImports() {Long id=tournament(true);imports.confirm(id,new ApplicationImports.Request("a.docx","Команда",List.of(entry("Один"))));tournaments.draw(id,1L);assertThrows(Exception.class,()->imports.check(id,List.of(entry("Другой")),List.of()));}
 @Test void invalidXmlCannotReadExternalFiles() throws Exception {
  assertThrows(Exception.class,()->parser.parse(zipped("<!DOCTYPE a [<!ENTITY xxe SYSTEM 'file:///C:/Windows/win.ini'>]><w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'>&xxe;</w:document>"),"Команда"));
 }
 @Test void multipartAndApplicationHistoryEndpointsWork() throws Exception {
  Long id=tournament(true);var bytes=docx(List.of(headers(),List.of("1","М","Тестовый спортсмен","16.02.2018","-","8","Весовая категория","29,7","Тренер","")));
  mvc.perform(multipart("/api/tournaments/"+id+"/applications/preview").file(new MockMultipartFile("file","team.docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document",bytes)).param("team","Команда"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.rows[0].categoryName").value("M"));
  mvc.perform(post("/api/tournaments/"+id+"/applications").contentType("application/json").content(mapper.writeValueAsBytes(new ApplicationImports.Request("http.docx","Команда",List.of(entry("HTTP участник")))))).andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(1));
  mvc.perform(get("/api/tournaments/"+id+"/applications")).andExpect(status().isOk()).andExpect(jsonPath("$[0].acceptedCount").value(1));
  mvc.perform(post("/api/tournaments/"+id+"/applications/check").contentType("application/json").content("{\"entries\":[]}")).andExpect(status().isBadRequest());
 }
 @Test void printSettingsRemainEditableAfterDrawAndHaveBlankSignatures() throws Exception {
  Long id=tournament(true);
  mvc.perform(get("/api/tournaments/"+id+"/print-settings")).andExpect(status().isOk()).andExpect(jsonPath("$.chiefJudge").value(""));
  mvc.perform(put("/api/tournaments/"+id+"/print-settings").contentType("application/json").content("{\"tatami\":\"B\",\"timeSlot\":\"12:00-14:00\",\"chiefJudge\":\"Судья\",\"secretary\":\"Секретарь\",\"banner\":true}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.tatami").value("B"));
 }
}
