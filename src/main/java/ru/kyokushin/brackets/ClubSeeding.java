package ru.kyokushin.brackets;

import java.text.Normalizer;
import java.util.*;
import ru.kyokushin.brackets.Domain.Athlete;

/** Balances each club at every split of the bracket, retaining balanced bye slots. */
final class ClubSeeding {
 private ClubSeeding() {}

 static String team(Athlete athlete) {
  String club=Normalizer.normalize(athlete.club,Normalizer.Form.NFKC)
      .strip().replaceAll("\\s+"," ").toLowerCase(Locale.ROOT);
  // Unaffiliated athletes are independent, rather than one artificial team.
  return club.isEmpty()?"athlete:"+athlete.id:"club:"+club;
 }

 static Athlete[] place(List<Athlete> entrants,int size,Random random) {
  List<Integer> seeds=new ArrayList<>(List.of(1,2));
  while(seeds.size()<size) {
   int next=seeds.size()*2+1;
   List<Integer> expanded=new ArrayList<>();
   for(int seed:seeds){expanded.add(seed);expanded.add(next-seed);}
   seeds=expanded;
  }
  boolean[] occupied=new boolean[size];
  for(int i=0;i<size;i++)occupied[i]=seeds.get(i)<=entrants.size();
  Athlete[] slots=new Athlete[size];
  split(new ArrayList<>(entrants),0,size,occupied,slots,random);
  return slots;
 }

 private static void split(List<Athlete> entrants,int start,int end,
                           boolean[] occupied,Athlete[] slots,Random random) {
  if(entrants.isEmpty())return;
  if(end-start==1){slots[start]=entrants.get(0);return;}
  int mid=(start+end)/2,leftCapacity=0;
  for(int i=start;i<mid;i++)if(occupied[i])leftCapacity++;
  Map<String,List<Athlete>> grouped=new LinkedHashMap<>();
  for(Athlete a:entrants)grouped.computeIfAbsent(team(a),key->new ArrayList<>()).add(a);
  List<List<Athlete>> teams=new ArrayList<>(grouped.values());
  Collections.shuffle(teams,random);
  List<Athlete> left=new ArrayList<>(),right=new ArrayList<>(),odd=new ArrayList<>();
  for(List<Athlete> team:teams) {
   Collections.shuffle(team,random);
   int half=team.size()/2;
   left.addAll(team.subList(0,half));
   right.addAll(team.subList(half,half*2));
   if(team.size()%2!=0)odd.add(team.get(team.size()-1));
  }
  // Each club has at most one extra athlete at any node. Allocate extras
  // to the remaining balanced capacities, using the seed to resolve ties.
  for(Athlete a:odd) {
   int l=leftCapacity-left.size(),r=entrants.size()-leftCapacity-right.size();
   if(l>r||(l==r&&random.nextBoolean()))left.add(a);else right.add(a);
  }
  split(left,start,mid,occupied,slots,random);
  split(right,mid,end,occupied,slots,random);
 }
}
