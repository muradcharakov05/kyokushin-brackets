package ru.kyokushin.brackets;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication
public class Application {
 @org.springframework.context.annotation.Bean org.springframework.boot.CommandLineRunner upgradeDraws(TournamentService service){return args->service.upgradeExistingDraws();}
 public static void main(String[] args) { SpringApplication.run(Application.class,args); } }
