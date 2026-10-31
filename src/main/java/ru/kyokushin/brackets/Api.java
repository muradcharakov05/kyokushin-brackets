package ru.kyokushin.brackets;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import java.util.*;
import ru.kyokushin.brackets.Inputs.*;
@RestController @RequestMapping("/api/tournaments")
public class Api {
 final TournamentService s; public Api(TournamentService s){this.s=s;}
 @GetMapping public Object list(){return s.list();}
 @PostMapping public Object create(@Valid @RequestBody TournamentInput i){return s.create(i);}
 @GetMapping("/{id}") public Object state(@PathVariable Long id){return s.state(id);}
 @PostMapping("/{id}/categories") public Object category(@PathVariable Long id,@Valid @RequestBody CategoryInput i){return s.category(id,i);}
 @PostMapping("/{id}/athletes") public Object athlete(@PathVariable Long id,@Valid @RequestBody AthleteInput i){return s.athlete(id,i);}
 @DeleteMapping("/{id}/athletes/{aid}") public void deleteAthlete(@PathVariable Long id,@PathVariable Long aid){s.deleteAthlete(id,aid);}
 @DeleteMapping("/{id}/categories/{cid}") public void deleteCategory(@PathVariable Long id,@PathVariable Long cid){s.deleteCategory(id,cid);}
 @PostMapping("/{id}/draw") public Object draw(@PathVariable Long id,@RequestBody DrawInput i){return s.draw(id,i.seed());}
 @DeleteMapping("/{id}/draw") public Object reset(@PathVariable Long id){return s.reset(id);}
 @PostMapping("/{id}/bouts/{bid}/winner") public Object winner(@PathVariable Long id,@PathVariable Long bid,@Valid @RequestBody WinnerInput i){return s.winner(id,bid,i.athleteId());}
 @GetMapping(value="/{id}/export",produces="text/csv;charset=UTF-8") public ResponseEntity<String> export(@PathVariable Long id){var state=s.state(id);StringBuilder csv=new StringBuilder("\ufeffКатегория;Раунд;Бой;Красный;Белый;Победитель\r\n");for(var b:state.bouts()){if(b.automatic)continue;String category=state.categories().stream().filter(c->c.id.equals(b.categoryId)).findFirst().orElseThrow().name;csv.append(cell(category)).append(';').append(cell(b.bronze?"Бой за 3 место":Integer.toString(b.roundNumber))).append(';').append(b.position+1).append(';').append(cell(name(state,b.redId))).append(';').append(cell(name(state,b.blueId))).append(';').append(cell(name(state,b.winnerId))).append("\r\n");}return ResponseEntity.ok().header("Content-Disposition","attachment; filename=brackets.csv").body(csv.toString());}
 @GetMapping(value="/{id}/podium/export",produces="text/csv;charset=UTF-8")
 public ResponseEntity<String> podiumExport(@PathVariable Long id) {
  var state=s.state(id);StringBuilder csv=new StringBuilder("\ufeffКатегория;Место;Спортсмен;Команда\r\n");
  for(var category:state.categories())for(var p:state.podium().get(category.id)) {
   var athlete=state.athletes().stream().filter(a->a.id.equals(p.athleteId())).findFirst().orElseThrow();
   csv.append(cell(category.name)).append(';').append(p.place()).append(';').append(cell(athlete.name)).append(';').append(cell(athlete.club)).append("\r\n");
  }
  return ResponseEntity.ok().header("Content-Disposition","attachment; filename=podium.csv").body(csv.toString());
 }
 static String name(TournamentService.State s,Long id){return s.athletes().stream().filter(a->a.id.equals(id)).map(a->a.name).findFirst().orElse("");}
 static String cell(String v){if(v.matches("^[=+@-].*"))v="'"+v;return "\""+v.replace("\"","\"\"")+"\"";}
}
@RestControllerAdvice class Errors {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",Objects.requireNonNullElse(e.getReason(),"Ошибка запроса")));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(Map.of("message","Проверьте поля формы: "+e.getBindingResult().getFieldErrors().stream().map(x->x.getField()+" "+x.getDefaultMessage()).findFirst().orElse("Некорректные данные")));}
 @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) ResponseEntity<?> malformed(Exception e){return ResponseEntity.badRequest().body(Map.of("message","Некорректный формат данных"));}
 @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class) ResponseEntity<?> large(){return ResponseEntity.status(413).body(Map.of("message","Размер DOCX не должен превышать 10 МБ"));}
 @ExceptionHandler(NoSuchElementException.class) ResponseEntity<?> missing(){return ResponseEntity.status(404).body(Map.of("message","Запись не найдена"));}
}
