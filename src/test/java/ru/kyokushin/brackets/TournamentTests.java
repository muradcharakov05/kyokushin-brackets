package ru.kyokushin.brackets;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import ru.kyokushin.brackets.Inputs.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=${test.jdbc.url:jdbc:h2:mem:test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}","spring.datasource.username=${test.jdbc.username:sa}","spring.datasource.password=${test.jdbc.password:}"})
@AutoConfigureMockMvc @Transactional
class TournamentTests {
 @Autowired TournamentService s; @Autowired MockMvc mvc; @Autowired Bouts boutRepository;
 final LocalDate date=LocalDate.of(2027,5,10);
 Long tournament(){return s.create(new TournamentInput("Тестовый кубок",date,"Зал")).id;}
 CategoryInput cat(String name,int low,int high){return new CategoryInput(name,"M",12,13,new BigDecimal(low),new BigDecimal(high),0,6);}
 AthleteInput person(int n,int weight){return new AthleteInput("Спортсмен "+n,"Клуб",date.minusYears(13),"M",new BigDecimal(weight),2);}
 @Test void listEndpoint() throws Exception {tournament();mvc.perform(get("/api/tournaments")).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Тестовый кубок"));}
 @Test void migrationAndValidation() throws Exception {mvc.perform(post("/api/tournaments").contentType("application/json").content("{\"name\":\"\",\"eventDate\":\"2027-05-10\",\"venue\":\"Зал\"}")).andExpect(status().isBadRequest());mvc.perform(get("/api/tournaments/999999")).andExpect(status().isNotFound());}
 @Test void boundariesAndTournamentAge(){Long id=tournament();var c=s.category(id,cat("Лёгкая",0,45));var heavy=s.category(id,cat("Тяжёлая",45,500));var a=s.athlete(id,person(1,45));var b=s.athlete(id,person(2,46));assertEquals(c.id,s.state(id).assignments().get(a.id));assertEquals(heavy.id,s.state(id).assignments().get(b.id));var birthday=s.athlete(id,new AthleteInput("Ещё 11 лет","",date.minusYears(12).plusDays(1),"M",new BigDecimal(40),0));assertTrue(s.state(id).unassigned().contains(birthday.id));}
 @Test void overlappingCategoriesRejected(){Long id=tournament();s.category(id,cat("А",0,45));assertThrows(Exception.class,()->s.category(id,cat("Б",40,60)));}
 @Test void unassignedBlocksDraw(){Long id=tournament();s.athlete(id,person(1,40));assertThrows(Exception.class,()->s.draw(id,1L));assertNull(s.state(id).tournament().drawSeed);}
 @Test void drawAllSizesAndPlayToChampion(){for(int n:new int[]{1,2,3,4,5,7,8,9,16,17,33}){Long id=tournament();s.category(id,cat("Общая",0,500));for(int i=0;i<n;i++)s.athlete(id,person(i,40));var state=s.draw(id,42L);var placed=state.bouts().stream().filter(b->b.roundNumber==1).flatMap(b->java.util.stream.Stream.of(b.redId,b.blueId)).filter(Objects::nonNull).toList();assertEquals(n,placed.size());assertEquals(n,new HashSet<>(placed).size());int played=0;while(true){var ready=state.bouts().stream().filter(b->!b.resolved&&b.redId!=null&&b.blueId!=null).findFirst();if(ready.isEmpty())break;var b=ready.get();state=s.winner(id,b.id,b.redId);played++;assertTrue(played<=n);}assertEquals(n>=16||n==4?n:n-1,played);assertTrue(state.bouts().stream().allMatch(b->b.resolved));var champion=state.bouts().stream().filter(b->!b.bronze).max(Comparator.comparingInt(b->b.roundNumber)).orElseThrow();assertNotNull(champion.winnerId);}}
 @Test void seedIsReproducibleAndResetUnlocks(){Long id=tournament();s.category(id,cat("А",0,500));for(int n=0;n<7;n++)s.athlete(id,person(n,40));var a=s.draw(id,123L).bouts().stream().filter(b->b.roundNumber==1).map(b->b.redId+":"+b.blueId).toList();assertThrows(Exception.class,()->s.athlete(id,person(10,40)));s.reset(id);assertTrue(s.state(id).bouts().isEmpty());var b=s.draw(id,123L).bouts().stream().filter(x->x.roundNumber==1).map(x->x.redId+":"+x.blueId).toList();assertEquals(a,b);}
 @Test void invalidWinnerAndOtherTournamentRejected(){Long id=tournament();s.category(id,cat("А",0,500));for(int n=0;n<2;n++)s.athlete(id,person(n,40));var b=s.draw(id,1L).bouts().get(0);assertThrows(Exception.class,()->s.winner(id,b.id,999999L));Long other=tournament();assertThrows(Exception.class,()->s.winner(other,b.id,b.redId));s.winner(id,b.id,b.redId);assertThrows(Exception.class,()->s.winner(id,b.id,b.blueId));}
 @Test void beltAndSexAreIsolated(){Long id=tournament();s.category(id,new CategoryInput("Младшие пояса","F",12,13,BigDecimal.ZERO,new BigDecimal(500),0,2));var a=s.athlete(id,person(1,40));assertTrue(s.state(id).unassigned().contains(a.id));var b=s.athlete(id,new AthleteInput("Анна","Клуб",date.minusYears(12),"F",new BigDecimal(40),3));assertTrue(s.state(id).unassigned().contains(b.id));}
 @Test void csvExport() throws Exception {Long id=tournament();s.category(id,cat("А",0,500));s.athlete(id,person(1,40));s.draw(id,1L);mvc.perform(get("/api/tournaments/"+id+"/export")).andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=brackets.csv"));assertEquals("\"'=SUM(A1)\"",Api.cell("=SUM(A1)"));}

 TournamentService.State completeMain(Long id) {
  var state=s.state(id);
  while(true) {
   var ready=state.bouts().stream().filter(b->!b.bronze&&!b.resolved&&b.redId!=null&&b.blueId!=null).findFirst();
   if(ready.isEmpty())return state;
   var b=ready.get();state=s.winner(id,b.id,b.redId);
  }
 }
 Long populated(int size) {
  Long id=tournament();s.category(id,cat("Категория",0,500));
  for(int n=0;n<size;n++)s.athlete(id,person(n,40));
  s.draw(id,42L);return id;
 }
 @Test void podiumForSmallCategories() {
  for(int n:new int[]{1,2,5,8,15}) {
   Long id=populated(n);var state=completeMain(id);
   var podium=state.podium().values().iterator().next();
   assertEquals(Math.min(4,n),podium.size());
   assertEquals(1,podium.stream().filter(p->p.place()==1).count());
   assertEquals(n>1?1:0,podium.stream().filter(p->p.place()==2).count());
   assertEquals(n>3?2:n==3?1:0,podium.stream().filter(p->p.place()==3).count());
   assertEquals(podium.size(),podium.stream().map(p->p.athleteId()).distinct().count());
   assertTrue(state.bouts().stream().noneMatch(b->b.bronze));
   var finalBout=state.bouts().get(state.bouts().size()-1);
   if(n>1)assertEquals(TournamentService.loser(finalBout),podium.stream().filter(p->p.place()==2).findFirst().orElseThrow().athleteId());
  }
 }
 @Test void bronzeThresholdAndLosersAdvanceOnlyToBronze() throws Exception {
  for(int n:new int[]{4,16,17,32,33}) {
   Long id=populated(n);var state=completeMain(id);
   var bronze=state.bouts().stream().filter(b->b.bronze).findFirst().orElseThrow();
   assertFalse(bronze.resolved);assertNotNull(bronze.redId);assertNotNull(bronze.blueId);
   var finalBout=state.bouts().stream().filter(b->!b.bronze).max(Comparator.comparingInt(b->b.roundNumber)).orElseThrow();
   var losers=state.bouts().stream().filter(b->!b.bronze&&b.roundNumber==finalBout.roundNumber-1).map(TournamentService::loser).toList();
   assertEquals(new HashSet<>(losers),Set.of(bronze.redId,bronze.blueId));
   assertFalse(losers.contains(finalBout.redId));assertFalse(losers.contains(finalBout.blueId));
   assertEquals(2,state.podium().values().iterator().next().size());
   var medals=s.winner(id,bronze.id,bronze.blueId).podium().values().iterator().next();
   assertEquals(3,medals.size());assertEquals(bronze.blueId,medals.stream().filter(p->p.place()==3).findFirst().orElseThrow().athleteId());
   var saved=s.state(id);assertEquals(medals,saved.podium().values().iterator().next());
   mvc.perform(get("/api/tournaments/"+id+"/podium/export")).andExpect(status().isOk()).andExpect(header().string("Content-Disposition","attachment; filename=podium.csv"));
  }
 }
 @Test void existingDrawUpgradePreservesResultsAndIsIdempotent() {
  Long id=populated(16);var before=completeMain(id);
  var bronze=before.bouts().stream().filter(b->b.bronze).findFirst().orElseThrow();
  boutRepository.deleteById(bronze.id);boutRepository.flush();
  var outcomes=s.state(id).bouts().stream().map(b->b.id+":"+b.winnerId).toList();
  s.upgradeExistingDraws();s.upgradeExistingDraws();
  var after=s.state(id);
  assertEquals(1,after.bouts().stream().filter(b->b.bronze).count());
  assertEquals(outcomes,after.bouts().stream().filter(b->!b.bronze).map(b->b.id+":"+b.winnerId).toList());
  assertNotNull(after.bouts().stream().filter(b->b.bronze).findFirst().orElseThrow().redId);
 }

 @Test void printablePdfHandlesWideBracketsAndIncludesAllEntrants() throws Exception {
  for(int n:new int[]{9,17,33}) {
   Long id=tournament();var category=s.category(id,cat("Юноши 12-13 до 45 кг",0,45));
   for(int i=0;i<n;i++)s.athlete(id,person(i,40));
   s.draw(id,42L);
   byte[] bytes=mvc.perform(get("/api/tournaments/"+id+"/categories/"+category.id+"/print.pdf"))
     .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
     .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray();
   try(var pdf=org.apache.pdfbox.Loader.loadPDF(bytes)) {
    assertEquals(n==9?1:n==17?3:5,pdf.getNumberOfPages());
    String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
    assertTrue(text.contains("Юноши 12-13 до 45 кг"));
    assertTrue(text.contains("ПРИЗОВЫЕ")||text.contains("Призовые"));
    for(int i=0;i<n;i++)assertTrue(text.contains("Спортсмен "+i));
    if(n>=16)assertTrue(text.contains("Бой за третье место"));
    assertTrue(pdf.getPage(0).getMediaBox().getWidth()<pdf.getPage(0).getMediaBox().getHeight());
    if(System.getProperty("pdf.qa.dir")!=null) {
     var dir=java.nio.file.Path.of(System.getProperty("pdf.qa.dir"));java.nio.file.Files.createDirectories(dir);
     java.nio.file.Files.write(dir.resolve("bracket-"+n+".pdf"),bytes);
     var renderer=new org.apache.pdfbox.rendering.PDFRenderer(pdf);
     for(int page=0;page<pdf.getNumberOfPages();page++)
      javax.imageio.ImageIO.write(renderer.renderImageWithDPI(page,110),"PNG",dir.resolve("bracket-"+n+"-"+(page+1)+".png").toFile());
    }
   }
  }
 }
 @Test void printRejectsMissingDrawAndUnrelatedCategory() throws Exception {
  Long id=tournament();var category=s.category(id,cat("Без сетки",0,500));s.athlete(id,person(1,40));
  mvc.perform(get("/api/tournaments/"+id+"/categories/"+category.id+"/print.pdf"))
    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Сетка этой категории ещё не построена. Сначала проведите жеребьёвку турнира."));
  mvc.perform(get("/api/tournaments/"+id+"/categories/999999/print.pdf")).andExpect(status().isNotFound());
  assertNull(s.state(id).tournament().drawSeed);
 }

 @Test void threeAthletesHaveAutomaticBronzeWithoutInventedOpponent() {
  Long id=populated(3);assertEquals(2,s.state(id).bouts().stream().filter(b->!b.automatic).count());var state=completeMain(id);var bronze=state.bouts().stream().filter(b->b.bronze).findFirst().orElseThrow();
  assertTrue(bronze.automatic);assertTrue(bronze.resolved);assertNotNull(bronze.winnerId);
  assertEquals(3,state.podium().values().iterator().next().size());
 }
 @Test void fourAthletePoolMatchesPortraitReferenceStructure() throws Exception {
  Long id=tournament();var c=s.category(id,cat("Мальчики 8-9 лет -28 кг",0,45));
  for(int i=0;i<4;i++)s.athlete(id,person(i,40));s.draw(id,13L);
  var settings=new Domain.PrintSettings();settings.chiefJudge="Иванов Иван Иванович";settings.secretary="Петрова Анна Ивановна";
  var state=s.state(id);byte[] bytes=BracketPdf.render(state,c,state.bouts(),settings);
  try(var pdf=org.apache.pdfbox.Loader.loadPDF(bytes)) {
   assertEquals(1,pdf.getNumberOfPages());String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
   for(String expected:List.of("КУМИТЕ","ТАТАМИ: A","Финал","Победители","Бой за третье место","Главный судья",settings.chiefJudge))assertTrue(text.contains(expected),expected);
   if(System.getProperty("pdf.qa.dir")!=null){var dir=java.nio.file.Path.of(System.getProperty("pdf.qa.dir"));java.nio.file.Files.createDirectories(dir);java.nio.file.Files.write(dir.resolve("pool-reference-4.pdf"),bytes);javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0,120),"PNG",dir.resolve("pool-reference-4.png").toFile());}
  }
 }

 @Test void byesImmediatelyAdvanceAndDisappearFromPdfAndFightCsv() throws Exception {
  for(int n:new int[]{1,3,5,6,9,17}) {
   Long id=populated(n);var state=s.state(id);var category=state.categories().get(0);
   var byes=state.bouts().stream().filter(b->!b.bronze&&b.roundNumber==1&&b.automatic).toList();
   assertFalse(byes.isEmpty());
   for(var bye:byes) {
    assertTrue(bye.resolved);assertNotNull(bye.winnerId);
    if(n>1) {
     var parent=state.bouts().stream().filter(b->!b.bronze&&b.roundNumber==2&&b.position==bye.position/2).findFirst().orElseThrow();
     assertEquals(bye.winnerId,bye.position%2==0?parent.redId:parent.blueId);
     assertFalse(parent.resolved); // An opponent's pending fight is never skipped.
    }
   }
   try(var pdf=org.apache.pdfbox.Loader.loadPDF(BracketPdf.render(state,category,state.bouts()))) {
    String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
    assertFalse(text.contains("Свободный проход"));
    for(var bye:byes) {
     String name=state.athletes().stream().filter(a->a.id.equals(bye.winnerId)).findFirst().orElseThrow().name;
     assertEquals(1,java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(name)+"(?![0-9])").matcher(text).results().count(),name);
     assertFalse(java.util.regex.Pattern.compile("(?m)^A"+bye.id+"$").matcher(text).find());
    }
    if(n==9&&System.getProperty("pdf.qa.dir")!=null) {
     var dir=java.nio.file.Path.of(System.getProperty("pdf.qa.dir"));java.nio.file.Files.createDirectories(dir);
     var bytes=new java.io.ByteArrayOutputStream();pdf.save(bytes);java.nio.file.Files.write(dir.resolve("byes-next-round.pdf"),bytes.toByteArray());
     javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(pdf).renderImageWithDPI(0,120),"PNG",dir.resolve("byes-next-round.png").toFile());
    }
   }
   var csv=mvc.perform(get("/api/tournaments/"+id+"/export")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
   assertEquals(state.bouts().stream().filter(b->!b.automatic).count()+1,csv.lines().count());
  }
 }
}
