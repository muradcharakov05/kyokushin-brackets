package ru.kyokushin.brackets;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public final class Domain {
 @Entity(name="Tournament") @Table(name="tournament") public static class Tournament {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  public String name; public LocalDate eventDate; public String venue; public Long drawSeed;
 }
 @Entity @Table(name="category") public static class Category {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  public Long tournamentId; public String name; public String sex;
  public int minAge,maxAge,minBelt,maxBelt; public BigDecimal minWeight,maxWeight;
 }
 @Entity @Table(name="athlete") public static class Athlete {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  public Long tournamentId; public String name,club,sex; public LocalDate birthDate; public BigDecimal weight; public int belt; public String coach="",sportRank=""; public Integer kyu; public Long applicationId;
 }
 @Entity @Table(name="registration_batch") public static class RegistrationBatch {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  public Long tournamentId; public String sourceName,team; public java.time.Instant createdAt; public int acceptedCount;
 }
 @Entity @Table(name="print_settings") public static class PrintSettings {
  @Id public Long tournamentId;
  public String tatami="A",timeSlot="",chiefJudge="",secretary="";
  public boolean banner=true;
 }
 @Entity @Table(name="bout") public static class Bout {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  public Long categoryId; public int roundNumber,position; public Long redId,blueId,winnerId; public boolean resolved,automatic,bronze;
 }
}
