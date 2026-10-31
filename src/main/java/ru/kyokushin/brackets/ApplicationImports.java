package ru.kyokushin.brackets;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import ru.kyokushin.brackets.Domain.*;
import ru.kyokushin.brackets.DocxApplications.Entry;

@Service @Transactional
class ApplicationImports {
 final TournamentService tournaments;final DocxApplications reader;final RegistrationBatches batches;final Categories categories;final Athletes athletes;
 ApplicationImports(TournamentService t,DocxApplications r,RegistrationBatches b,Categories c,Athletes a){tournaments=t;reader=r;batches=b;categories=c;athletes=a;}
 record Row(Entry entry,Long categoryId,String categoryName,List<String> errors,boolean duplicate){}
 record Preview(List<Row> rows,List<String> warnings){}
 record Request(String sourceName,String team,List<Entry> entries){}
 record Accepted(Long applicationId,int accepted,List<Long> athleteIds){}

 Preview file(Long id,byte[] bytes,String team){var t=tournaments.lock(id);tournaments.editable(t);var parsed=reader.parse(bytes,team==null?"":team);return check(id,parsed.entries(),parsed.warnings());}
 Preview check(Long id,List<Entry> entries,List<String> warnings) {
  var t=tournaments.lock(id);tournaments.editable(t);
  TournamentService.require(entries!=null&&!entries.isEmpty()&&entries.size()<=500,"В заявке должно быть от 1 до 500 участников");
  var configuredCategories=categories.findByTournamentIdOrderById(id);
  Set<String> seen=new HashSet<>();for(var a:athletes.findByTournamentIdOrderById(id))seen.add(key(a.name,a.birthDate,a.sex));
  List<Row> rows=new ArrayList<>();
  for(Entry e:entries) {
   TournamentService.require(e!=null,"Некорректная строка заявки");
   List<String> errors=new ArrayList<>();
   if(e.name()==null||e.name().isBlank()||e.name().length()>160)errors.add("Укажите ФИО (до 160 символов)");
   if(e.club()==null||e.club().isBlank()||e.club().length()>160)errors.add("Укажите команду (до 160 символов)");
   if(!Set.of("M","F").contains(e.sex()==null?"":e.sex()))errors.add("Укажите пол М или Ж");
   if(e.birthDate()==null)errors.add("Дата рождения должна быть ДД.ММ.ГГГГ");
   else {int age=Period.between(e.birthDate(),t.eventDate).getYears();if(age<4||age>100||e.birthDate().isAfter(t.eventDate))errors.add("Возраст на дату турнира должен быть 4–100 лет");}
   if(e.weight()==null||e.weight().compareTo(BigDecimal.ONE)<0||e.weight().compareTo(new BigDecimal(500))>0||e.weight().stripTrailingZeros().scale()>2)errors.add("Проверьте точный вес: 1–500 кг, до двух знаков после запятой");
   if(e.belt()==null||e.belt()<0||e.belt()>6)errors.add("Не определён пояс: проверьте Кю или выберите пояс");
   if(e.kyu()!=null&&(e.kyu()<0||e.kyu()>11))errors.add("Кю должен быть от 0 до 11");
   if(e.kyu()!=null&&e.belt()!=null&&!Objects.equals(DocxApplications.beltFromKyu(e.kyu()),e.belt()))errors.add("Пояс не соответствует указанному Кю");
   if(e.coach()!=null&&e.coach().length()>160)errors.add("ФИО тренера: максимум 160 символов");
   if(e.sportRank()!=null&&e.sportRank().length()>80)errors.add("Разряд: максимум 80 символов");
   boolean duplicate=e.name()!=null&&e.birthDate()!=null&&!seen.add(key(e.name(),e.birthDate(),e.sex()));
   if(duplicate)errors.add("Участник уже есть в турнире или повторяется в заявке");
   Category category=null;
   if(errors.isEmpty()) {
    Athlete a=new Athlete();a.name=e.name();a.sex=e.sex();a.birthDate=e.birthDate();a.weight=e.weight();a.belt=e.belt();
    var matches=configuredCategories.stream().filter(c->TournamentService.fits(a,c,t.eventDate)).toList();
    if(matches.size()==1)category=matches.get(0);else errors.add("Нет подходящей категории по полу, возрасту, весу и поясу. Добавьте её в разделе «Категории».");
   }
   rows.add(new Row(e,category==null?null:category.id,category==null?"":category.name,errors,duplicate));
  }
  return new Preview(rows,warnings);
 }
 Accepted confirm(Long id,Request request) {
  TournamentService.require(request!=null&&request.team()!=null&&!request.team().isBlank()&&request.team().length()<=160,"Укажите команду заявки");
  TournamentService.require(request.sourceName()!=null&&request.sourceName().length()<=220,"Слишком длинное имя файла");
  var preview=check(id,request.entries(),List.of());
  var bad=preview.rows().stream().filter(r->!r.errors().isEmpty()).findFirst();
  TournamentService.require(bad.isEmpty(),bad.isPresent()?"Строка "+bad.get().entry().sourceRow()+": "+String.join("; ",bad.get().errors()):"");
  RegistrationBatch batch=new RegistrationBatch();batch.tournamentId=id;batch.sourceName=request.sourceName();batch.team=request.team().strip();batch.createdAt=Instant.now();batch.acceptedCount=request.entries().size();batches.saveAndFlush(batch);
  List<Long> ids=new ArrayList<>();
  for(Entry e:request.entries()) {
   var a=tournaments.athlete(id,new Inputs.AthleteInput(e.name(),e.club(),e.birthDate(),e.sex(),e.weight(),e.belt()));
   a.coach=e.coach()==null?"":e.coach().strip();a.sportRank=e.sportRank()==null?"":e.sportRank().strip();a.kyu=e.kyu();a.applicationId=batch.id;
   athletes.save(a);ids.add(a.id);
  }
  athletes.flush();return new Accepted(batch.id,ids.size(),ids);
 }
 public Object list(Long id){tournaments.state(id);return batches.findByTournamentIdOrderByIdDesc(id);}
 static String key(String name,LocalDate birth,String sex){return DocxApplications.normalize(name)+"|"+birth+"|"+sex;}
}

@RestController @RequestMapping("/api/tournaments/{id}/applications")
class ApplicationImportApi {
 final ApplicationImports service;ApplicationImportApi(ApplicationImports service){this.service=service;}
 @PostMapping(value="/preview",consumes="multipart/form-data") Object preview(@PathVariable Long id,@RequestParam("file") MultipartFile file,@RequestParam(defaultValue="") String team) throws java.io.IOException {
  TournamentService.require(file.getOriginalFilename()!=null&&file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".docx"),"Загрузите заявку в формате DOCX");
  TournamentService.require(!file.isEmpty()&&file.getSize()<=10*1024*1024,"Размер файла должен быть от 1 байта до 10 МБ");
  return service.file(id,file.getBytes(),team);
 }
 @PostMapping("/check") Object check(@PathVariable Long id,@RequestBody ApplicationImports.Request request){return service.check(id,request.entries(),List.of());}
 @PostMapping Object confirm(@PathVariable Long id,@RequestBody ApplicationImports.Request request){return service.confirm(id,request);}
 @GetMapping Object list(@PathVariable Long id){return service.list(id);}
 @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class) org.springframework.http.ResponseEntity<?> large(){return org.springframework.http.ResponseEntity.status(413).body(Map.of("message","Размер DOCX не должен превышать 10 МБ"));}
}
