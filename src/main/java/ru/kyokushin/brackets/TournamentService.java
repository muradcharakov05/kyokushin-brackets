package ru.kyokushin.brackets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;
import java.security.SecureRandom;
import ru.kyokushin.brackets.Domain.*;
import ru.kyokushin.brackets.Inputs.*;
@Service @Transactional
public class TournamentService {
 final Tournaments tournaments; final Categories categories; final Athletes athletes; final Bouts bouts;
 public TournamentService(Tournaments t,Categories c,Athletes a,Bouts b){tournaments=t;categories=c;athletes=a;bouts=b;}
 static void require(boolean ok,String message){if(!ok)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 Tournament lock(Long id){return tournaments.lock(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Турнир не найден"));}
 void editable(Tournament t){require(t.drawSeed==null,"Сначала сбросьте жеребьёвку. Состав участников и категории заблокированы.");}
 @Transactional(readOnly=true) public Object list(){return tournaments.findAll(org.springframework.data.domain.Sort.by("id").descending());}
 public Tournament create(TournamentInput i){Tournament t=new Tournament();t.name=i.name().trim();t.eventDate=i.eventDate();t.venue=i.venue().trim();return tournaments.save(t);}
 public Category category(Long id,CategoryInput i){Tournament t=lock(id);editable(t);
  require(i.minAge()<=i.maxAge()&&i.minWeight().compareTo(i.maxWeight())<0&&i.minBelt()<=i.maxBelt(),"Проверьте границы категории");
  Category c=new Category();c.tournamentId=id;c.name=i.name().trim();c.sex=i.sex();c.minAge=i.minAge();c.maxAge=i.maxAge();c.minWeight=i.minWeight();c.maxWeight=i.maxWeight();c.minBelt=i.minBelt();c.maxBelt=i.maxBelt();
  for(Category x:categories.findByTournamentIdOrderById(id)) require(!(x.sex.equals(c.sex)&&x.minAge<=c.maxAge&&c.minAge<=x.maxAge&&x.minBelt<=c.maxBelt&&c.minBelt<=x.maxBelt&&x.minWeight.compareTo(c.maxWeight)<0&&c.minWeight.compareTo(x.maxWeight)<0),"Категория пересекается с «"+x.name+"»");
  return categories.save(c);
 }
 public Athlete athlete(Long id,AthleteInput i){Tournament t=lock(id);editable(t);int age=Period.between(i.birthDate(),t.eventDate).getYears();require(age>=4&&age<=100&&!i.birthDate().isAfter(t.eventDate),"Возраст на дату турнира должен быть от 4 до 100 лет");
  Athlete a=new Athlete();a.tournamentId=id;a.name=i.name().trim();a.club=i.club().trim();a.birthDate=i.birthDate();a.sex=i.sex();a.weight=i.weight();a.belt=i.belt();return athletes.save(a);
 }
 public void deleteAthlete(Long id,Long aid){editable(lock(id));Athlete a=athletes.findById(aid).orElseThrow();require(a.tournamentId.equals(id),"Участник другого турнира");athletes.delete(a);}
 public void deleteCategory(Long id,Long cid){editable(lock(id));Category c=categories.findById(cid).orElseThrow();require(c.tournamentId.equals(id),"Категория другого турнира");categories.delete(c);}
 static boolean fits(Athlete a,Category c,LocalDate date){int age=Period.between(a.birthDate,date).getYears();return a.sex.equals(c.sex)&&age>=c.minAge&&age<=c.maxAge&&a.belt>=c.minBelt&&a.belt<=c.maxBelt&&a.weight.compareTo(c.minWeight)>0&&a.weight.compareTo(c.maxWeight)<=0;}
 public record Placement(int place,Long athleteId) {}
 public record State(Tournament tournament,List<Category> categories,List<Athlete> athletes,List<Bout> bouts,Map<Long,Long> assignments,List<Long> unassigned,Map<Long,List<Placement>> podium){}
 @Transactional(readOnly=true) public State state(Long id){Tournament t=tournaments.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Турнир не найден"));var cs=categories.findByTournamentIdOrderById(id);var as=athletes.findByTournamentIdOrderById(id);Map<Long,Long> assigned=new LinkedHashMap<>();List<Long> un=new ArrayList<>();for(Athlete a:as){var found=cs.stream().filter(c->fits(a,c,t.eventDate)).toList();if(found.size()==1)assigned.put(a.id,found.get(0).id);else un.add(a.id);}var allBouts=cs.stream().flatMap(c->bouts.findByCategoryIdOrderByRoundNumberAscPositionAsc(c.id).stream()).toList();Map<Long,List<Placement>> podium=new LinkedHashMap<>();for(Category c:cs)podium.put(c.id,placements(allBouts.stream().filter(b->b.categoryId.equals(c.id)).toList()));return new State(t,cs,as,allBouts,assigned,un,podium);}
 public State draw(Long id,Long seed){Tournament t=lock(id);editable(t);State s=state(id);require(!s.athletes.isEmpty(),"Добавьте участников");require(s.unassigned.isEmpty(),"Не всем участникам найдена категория. Исправьте категории перед жеребьёвкой.");t.drawSeed=seed==null?new SecureRandom().nextLong():seed;Random random=new Random(t.drawSeed);
  for(Category c:s.categories){var entrants=new ArrayList<>(s.athletes.stream().filter(a->c.id.equals(s.assignments.get(a.id))).toList());if(entrants.isEmpty())continue;Collections.shuffle(entrants,random);int size=2;while(size<entrants.size())size*=2;
   var slots=ClubSeeding.place(entrants,size,random);
   for(int round=1,count=size/2;count>0;round++,count/=2) {
    for(int position=0;position<count;position++) {
     Bout b=new Bout();b.categoryId=c.id;b.roundNumber=round;b.position=position;
     if(round==1) {
      b.redId=slots[position*2]==null?null:slots[position*2].id;
      b.blueId=slots[position*2+1]==null?null:slots[position*2+1].id;
     }
     bouts.save(b);
    }
   }
   bouts.flush();ensureBronze(c.id);advance(c.id);
  }tournaments.saveAndFlush(t);return state(id);
 }
 static Long loser(Bout b) {
  if(!b.resolved||b.winnerId==null)return null;
  return Objects.equals(b.winnerId,b.redId)?b.blueId:b.redId;
 }

 static List<Placement> placements(List<Bout> bs) {
  List<Placement> result=new ArrayList<>();
  Bout finalBout=bs.stream().filter(b->!b.bronze).max(Comparator.comparingInt(b->b.roundNumber)).orElse(null);
  if(finalBout==null)return result;
  if(finalBout.resolved&&finalBout.winnerId!=null) {
   result.add(new Placement(1,finalBout.winnerId));
   if(loser(finalBout)!=null)result.add(new Placement(2,loser(finalBout)));
  }
  var bronze=bs.stream().filter(b->b.bronze).findFirst();
  if(bronze.isPresent()) {
   Bout b=bronze.get();
   if(b.resolved&&b.winnerId!=null)result.add(new Placement(3,b.winnerId));
  } else {
   bs.stream().filter(b->!b.bronze&&b.roundNumber==finalBout.roundNumber-1)
     .sorted(Comparator.comparingInt(b->b.position)).map(TournamentService::loser)
     .filter(Objects::nonNull).forEach(id->result.add(new Placement(3,id)));
  }
  return result;
 }

 void ensureBronze(Long cid) {
  var bs=bouts.findByCategoryIdOrderByRoundNumberAscPositionAsc(cid);
  long entrants=bs.stream().filter(b->!b.bronze&&b.roundNumber==1)
      .flatMap(b->java.util.stream.Stream.of(b.redId,b.blueId)).filter(Objects::nonNull).count();
  if((entrants==3||entrants==4||entrants>=16)&&bs.stream().noneMatch(b->b.bronze)) {
   Bout bronze=new Bout();bronze.categoryId=cid;bronze.bronze=true;bronze.automatic=entrants==3;bronze.position=0;
   bronze.roundNumber=bs.stream().mapToInt(b->b.roundNumber).max().orElseThrow()+1;
   bouts.saveAndFlush(bronze);
  }
 }

 // Existing draws retain their seeding and results. Only a missing medal bout is added.
 public void upgradeExistingDraws() {
  for(Tournament t:tournaments.findAll())if(t.drawSeed!=null) {
   lock(t.id);
   for(Category c:categories.findByTournamentIdOrderById(t.id)) {
    ensureBronze(c.id);advance(c.id);
   }
  }
 }

 void advance(Long cid) {
  var bs=bouts.findByCategoryIdOrderByRoundNumberAscPositionAsc(cid);
  var main=bs.stream().filter(b->!b.bronze).toList();
  int finalRound=main.stream().mapToInt(b->b.roundNumber).max().orElse(1);
  for(Bout b:bs) {
   boolean ready=b.roundNumber==1&&!b.bronze;
   if(b.bronze) {
    var semis=main.stream().filter(x->x.roundNumber==finalRound-1).toList();
    b.automatic=semis.size()==2&&semis.stream().anyMatch(x->x.automatic);
    ready=semis.size()==2&&semis.stream().allMatch(x->x.resolved);
    if(ready){b.redId=loser(semis.get(0));b.blueId=loser(semis.get(1));}
   } else if(!ready) {
    Bout left=main.stream().filter(x->x.roundNumber==b.roundNumber-1&&x.position==b.position*2).findFirst().orElseThrow();
    Bout right=main.stream().filter(x->x.roundNumber==b.roundNumber-1&&x.position==b.position*2+1).findFirst().orElseThrow();
    b.redId=left.winnerId;b.blueId=right.winnerId;ready=left.resolved&&right.resolved;
   }
   if(ready&&!b.resolved&&(b.redId==null||b.blueId==null)) {
    b.winnerId=b.redId!=null?b.redId:b.blueId;b.resolved=true;b.automatic=true;
   }
  }
  bouts.saveAllAndFlush(bs);
 }
 public State winner(Long id,Long bid,Long winner){lock(id);Bout b=bouts.findById(bid).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Бой не найден"));Category c=categories.findById(b.categoryId).orElseThrow();require(c.tournamentId.equals(id),"Бой другого турнира");require(!b.resolved&&b.redId!=null&&b.blueId!=null,"Бой ещё не готов или уже завершён");require(winner.equals(b.redId)||winner.equals(b.blueId),"Победитель должен участвовать в бою");b.winnerId=winner;b.resolved=true;bouts.saveAndFlush(b);advance(c.id);return state(id);}
 public State reset(Long id){Tournament t=lock(id);for(Category c:categories.findByTournamentIdOrderById(id))bouts.deleteByCategoryId(c.id);t.drawSeed=null;tournaments.saveAndFlush(t);return state(id);}
}
