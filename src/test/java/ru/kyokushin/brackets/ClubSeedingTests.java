package ru.kyokushin.brackets;

import org.junit.jupiter.api.Test;
import java.util.*;
import ru.kyokushin.brackets.Domain.Athlete;
import static org.junit.jupiter.api.Assertions.*;

class ClubSeedingTests {
 Athlete athlete(int id,String club){Athlete a=new Athlete();a.id=(long)id;a.club=club;return a;}

 @Test void everyClubBalancedAtEveryBracketLevelWithByes() {
  for(int n=1;n<=65;n++)for(int seed=0;seed<15;seed++) {
   Random random=new Random(seed);List<Athlete> entrants=new ArrayList<>();
   for(int i=0;i<n;i++)entrants.add(athlete(i,"Клуб "+random.nextInt(1+seed%9)));
   int size=2;while(size<n)size*=2;
   var slots=ClubSeeding.place(entrants,size,new Random(seed));
   assertEquals(n,Arrays.stream(slots).filter(Objects::nonNull).map(a->a.id).distinct().count());
   for(int width=2;width<=size;width*=2)for(int start=0;start<size;start+=width) {
    Map<String,Integer> delta=new HashMap<>();
    for(int i=start;i<start+width;i++)if(slots[i]!=null)
     delta.merge(ClubSeeding.team(slots[i]),i<start+width/2?1:-1,Integer::sum);
    assertTrue(delta.values().stream().allMatch(d->Math.abs(d)<=1),"Unbalanced club at size "+n);
   }
   // No empty first-round bout for categories with more than one athlete.
   if(n>1)for(int i=0;i<size;i+=2)assertTrue(slots[i]!=null||slots[i+1]!=null);
  }
 }

 @Test void twoTeammatesCanMeetOnlyInFinalAndFourOnlyInSemis() {
  for(int teamSize:new int[]{2,4}) {
   List<Athlete> entrants=new ArrayList<>();
   for(int n=0;n<16;n++)entrants.add(athlete(n,n<teamSize?"Общая команда":"Клуб "+n));
   for(int seed=0;seed<30;seed++) {
    var slots=ClubSeeding.place(entrants,16,new Random(seed));
    Set<Integer> branches=new HashSet<>();
    for(int i=0;i<slots.length;i++)if(slots[i].id<teamSize)branches.add(i/(16/teamSize));
    assertEquals(teamSize,branches.size());
   }
  }
 }

 @Test void clubNamesIgnoreCaseAndWhitespaceAndEmptyClubsAreIndependent() {
  assertEquals(ClubSeeding.team(athlete(1,"  СК   Север  ")),ClubSeeding.team(athlete(2,"ск север")));
  assertNotEquals(ClubSeeding.team(athlete(1,"")),ClubSeeding.team(athlete(2,"  ")));
 }

 @Test void seedIsStable() {
  var entrants=List.of(athlete(1,"А"),athlete(2,"А"),athlete(3,"Б"),athlete(4,"Б"),athlete(5,"В"));
  var a=Arrays.stream(ClubSeeding.place(entrants,8,new Random(12))).map(x->x==null?null:x.id).toList();
  var b=Arrays.stream(ClubSeeding.place(entrants,8,new Random(12))).map(x->x==null?null:x.id).toList();
  assertEquals(a,b);
 }
}
