package ru.kyokushin.brackets;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.math.BigDecimal;
import ru.kyokushin.brackets.Inputs.*;
@RestController public class Demo {
 final TournamentService s; public Demo(TournamentService s){this.s=s;}
 @PostMapping("/api/tournaments/demo") @Transactional public Object create(){
  var date=LocalDate.now().plusMonths(1);var t=s.create(new TournamentInput("Открытый кубок · Киокушинкай",date,"Спортивный центр · Демотурнир"));
  s.category(t.id,new CategoryInput("Юноши 12–13 · до 45 кг","M",12,13,new BigDecimal("0"),new BigDecimal("45"),0,6));
  s.category(t.id,new CategoryInput("Юноши 12–13 · свыше 45 кг","M",12,13,new BigDecimal("45"),new BigDecimal("500"),0,6));
  String[] names={"Александр Морозов","Михаил Соколов","Даниил Волков","Артём Козлов","Иван Попов","Максим Орлов","Никита Лебедев","Егор Смирнов","Матвей Петров","Андрей Белов","Роман Васильев","Дмитрий Фёдоров","Тимофей Захаров","Кирилл Новиков","Глеб Иванов","Лев Кузнецов"};
  String[] clubs={"СК Сэйкен","Додзё Север","Клуб Ичигэки","СК Кэнсэй"};
  for(int n=0;n<names.length;n++)s.athlete(t.id,new AthleteInput(names[n],clubs[n%4],date.minusYears(13).minusDays(n),"M",new BigDecimal(n<9?35+n:46+n%8),1+n%5));
  return t;
 }
}
