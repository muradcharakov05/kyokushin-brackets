package ru.kyokushin.brackets;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.util.*;
import ru.kyokushin.brackets.Domain.*;

/** A standalone, printable A4 document; no browser print API is required. */
@RestController
public class BracketPdf {
 private final TournamentService service; private final PrintConfiguration configuration;
 public BracketPdf(TournamentService service,PrintConfiguration configuration){this.service=service;this.configuration=configuration;}

 @GetMapping(value="/api/tournaments/{id}/categories/{cid}/print.pdf",produces="application/pdf")
 public ResponseEntity<byte[]> pdf(@PathVariable Long id,@PathVariable Long cid) throws IOException {
  var state=service.state(id);
  var category=state.categories().stream().filter(c->c.id.equals(cid)).findFirst()
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Категория не найдена"));
  var bouts=state.bouts().stream().filter(b->b.categoryId.equals(cid)).toList();
  TournamentService.require(!bouts.isEmpty(),"Сетка этой категории ещё не построена. Сначала проведите жеребьёвку турнира.");
  return ResponseEntity.ok().header("Content-Disposition","attachment; filename=bracket-"+id+"-"+cid+".pdf")
      .cacheControl(CacheControl.noStore()).body(render(state,category,bouts,configuration.get(id)));
 }


 static byte[] render(TournamentService.State state,Category category,List<Bout> bouts) throws IOException {
  return render(state,category,bouts,new PrintSettings());
 }
 static byte[] render(TournamentService.State state,Category category,List<Bout> bouts,PrintSettings settings) throws IOException {
  try(var doc=new PDDocument();var stream=BracketPdf.class.getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
   if(stream==null)throw new IOException("Missing bundled font");
   var font=PDType0Font.load(doc,stream);var main=bouts.stream().filter(b->!b.bronze).toList();
   int finalRound=main.stream().mapToInt(b->b.roundNumber).max().orElseThrow();
   org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject banner=null;
   if(settings.banner)try(var in=BracketPdf.class.getResourceAsStream("/print-banner.png")) {
    if(in!=null)banner=org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject.createFromByteArray(doc,in.readAllBytes(),"banner");
   }
   int pageNumber=0;
   for(int first=1;first<=finalRound;first+=4) {
    int last=Math.min(first+3,finalRound),lastRound=last;
    for(Bout root:main.stream().filter(b->b.roundNumber==lastRound).toList()) {
     PDPage page=new PDPage(PDRectangle.A4);doc.addPage(page);pageNumber++;
     try(var out=new PDPageContentStream(doc,page)) {
      Canvas c=new Canvas(out,font,page.getMediaBox().getHeight());
      if(banner!=null)out.drawImage(banner,84,page.getMediaBox().getHeight()-72,491,52);
      else c.center(18,55,15,state.tournament().name,557);
      c.center(18,84,10,"Турнирная таблица в дисциплине КУМИТЕ",557);
      c.center(18,96,8,category.name,557);
      c.line(18,100,575,100);
      c.center(18,108,8,"ТАТАМИ: "+settings.tatami+(settings.timeSlot.isBlank()?"":" (Дисциплина Кумите "+settings.timeSlot+")"),557);
      c.fill(18,112,557,12,.77f,.77f,.77f);
      long count=state.athletes().stream().filter(a->category.id.equals(state.assignments().get(a.id))).count();
      c.text(20,121,7,"Участники: "+count,130);
      int columns=last-first+1,firstCount=1<<(last-first);
      float step=Math.min(101,(557-151f)/columns),pitch=23,startY=146;
      for(int round=first;round<=last;round++) {
       float endpoint=169+(round-first)*step;
       String title=round==finalRound?"Финал":"1/"+(1<<(finalRound-round));
       c.text(endpoint-13,121,7,title,step);
       int scale=1<<(last-round);
       for(Bout b:main)if(b.roundNumber==round&&b.position/scale==root.position) {
        // Automatic passes are internal routing nodes, not printable bouts.
        if(b.automatic)continue;
        int local=b.position-root.position*scale;
        float center=startY+((local+.5f)*(1<<(round-first+1))-.5f)*pitch;
        float offset=(1<<(round-first))*pitch/2;
        if(round==first) {
         leaf(c,state,b.redId,18,center-offset,151,b.roundNumber==1);
         leaf(c,state,b.blueId,18,center+offset,151,b.roundNumber==1);
        } else {
         // Render all next-round entries consistently with the interactive pool.
         leaf(c,state,b.redId,endpoint-step,center-offset,step,false);
         leaf(c,state,b.blueId,endpoint-step,center+offset,step,false);
        }
        c.line(endpoint,center-offset,endpoint,center+offset);
        c.line(endpoint,center,endpoint+step,center);
        c.text(endpoint-25,center+3,7,settings.tatami+b.id,24);
        if(round==finalRound&&b.winnerId!=null)c.text(endpoint+3,center-3,7,athlete(state,b.winnerId),step-6);
       }
      }
      float bottom=startY+(firstCount*2-1)*pitch;
      var bronze=bouts.stream().filter(b->b.bronze).findFirst();
      if(last==finalRound&&bronze.isPresent()&&!bronze.get().automatic) {
       Bout b=bronze.get();float x=169,y=bottom+13;
       c.text(x+step+23,y-23,7,"Бой за третье место",150);
       leaf(c,state,b.redId,x,y,step,false);leaf(c,state,b.blueId,x,y+23,step,false);
       c.line(x+step,y,x+step,y+23);c.line(x+step,y+11.5f,x+step*2,y+11.5f);
       c.text(x+step-25,y+15,7,settings.tatami+b.id,24);
       if(b.winnerId!=null)c.text(x+step+3,y+8,7,athlete(state,b.winnerId),step-6);
       bottom=y+23;
      }
      float tableY=bottom+24;
      c.text(18,tableY-6,7,"Призовые места",250);
      var medals=state.podium().getOrDefault(category.id,List.of());
      int thirds=(int)medals.stream().filter(m->m.place()==3).count();
      int expectedThirds=bronze.isPresent()?1:count>=4?2:count==3?1:0;
      int[] places=expectedThirds==2?new int[]{1,2,3,3}:new int[]{1,2,3};
      c.box(18,tableY,253,11*(places.length+1));c.line(44,tableY,44,tableY+11*(places.length+1));
      c.text(20,tableY+8,7,"Место",23);c.text(47,tableY+8,7,"Победители",220);
      Map<Integer,Integer> used=new HashMap<>();
      for(int i=0;i<places.length;i++) {
       int place=places[i];float y=tableY+11*(i+1);c.line(18,y,271,y);c.text(30,y+8,7,Integer.toString(place),12);
       var list=medals.stream().filter(m->m.place()==place).toList();int index=used.getOrDefault(place,0);used.put(place,index+1);
       if(index<list.size())c.text(47,y+8,7,athlete(state,list.get(index).athleteId()),220);
      }
      float officials=tableY+11*(places.length+1)+32;
      c.text(20,officials,7,"Главный судья",100);c.text(170,officials,7,settings.chiefJudge,405);
      c.text(20,officials+23,7,"Главный секретарь",110);c.text(170,officials+23,7,settings.secretary,405);
      c.line(95,officials+10,155,officials+10);c.line(95,officials+33,155,officials+33);
      c.text(18,819,6,state.tournament().name+" · "+state.tournament().eventDate+" · Лист "+pageNumber,557);
     }
    }
   }
   doc.getDocumentInformation().setTitle(category.name+" - "+state.tournament().name);doc.getDocumentInformation().setAuthor("Кумитэ");
   var buffer=new ByteArrayOutputStream();doc.save(buffer);return buffer.toByteArray();
  }
 }
 private static void leaf(Canvas c,TournamentService.State state,Long id,float x,float y,float width,boolean first) throws IOException {
  String name=athlete(state,id);
  if(name.isBlank())name=first?"Свободный проход":"";
  c.text(x+1,y-10,8,name,width-4);
  String coach=state.athletes().stream().filter(a->a.id.equals(id)).map(a->a.coach.isBlank()?a.club:a.coach).findFirst().orElse("");
  c.text(x+1,y-2,6,coach,width-4);c.line(x,y,x+width,y);
 }
 private static String athlete(TournamentService.State state,Long id) {
  return state.athletes().stream().filter(a->a.id.equals(id)).map(a->a.name).findFirst().orElse("");
 }
 private static String club(TournamentService.State state,Long id) {
  return state.athletes().stream().filter(a->a.id.equals(id)).map(a->a.club).findFirst().orElse("");
 }
 private static class Canvas {
  final PDPageContentStream out;final PDType0Font font;final float height;
  Canvas(PDPageContentStream out,PDType0Font font,float height){this.out=out;this.font=font;this.height=height;}
  void center(float x,float top,float size,String text,float width) throws IOException {
   float actual=Math.min(width,font.getStringWidth(text)/1000*size);text(x+(width-actual)/2,top,size,text,width);
  }
  void text(float x,float top,float size,String text,float maxWidth) throws IOException {
   // Replace unsupported glyphs with a printable replacement, never fail the
   // whole document because a club uses an emoji in its name.
   StringBuilder supported=new StringBuilder();
   for(int cp:text.codePoints().toArray()) {
    if(Character.isISOControl(cp)){supported.append(' ');continue;}
    String glyph=new String(Character.toChars(cp));
    try{font.getStringWidth(glyph);supported.append(glyph);}catch(IllegalArgumentException e){supported.append('?');}
   }
   String clean=supported.toString();
   if(font.getStringWidth(clean)/1000*size>maxWidth) {
    while(!clean.isEmpty()&&font.getStringWidth(clean+"...")/1000*size>maxWidth)
     clean=clean.substring(0,clean.offsetByCodePoints(clean.length(),-1));
    clean+="...";
   }
   out.setNonStrokingColor(0f,0f,0f);out.beginText();out.setFont(font,size);out.newLineAtOffset(x,height-top);out.showText(clean);out.endText();
  }
  void line(float x1,float y1,float x2,float y2) throws IOException {
   out.setStrokingColor(0f,0f,0f);out.setLineWidth(.4f);out.moveTo(x1,height-y1);out.lineTo(x2,height-y2);out.stroke();
  }
  void box(float x,float y,float width,float h) throws IOException {
   out.setStrokingColor(0f,0f,0f);out.setLineWidth(.4f);out.addRect(x,height-y-h,width,h);out.stroke();
  }
  void fill(float x,float y,float width,float h,float r,float g,float b) throws IOException {
   out.setNonStrokingColor(r,g,b);out.addRect(x,height-y-h,width,h);out.fill();
  }
 }
}
