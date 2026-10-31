package ru.kyokushin.brackets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ru.kyokushin.brackets.Domain.PrintSettings;
@Service @Transactional
class PrintConfiguration {
 final TournamentService service;final PrintSettingsRepository repository;
 PrintConfiguration(TournamentService s,PrintSettingsRepository r){service=s;repository=r;}
 record Settings(@NotBlank @Pattern(regexp="[A-Za-zА-Яа-я0-9]{1,8}") String tatami,@NotNull @Size(max=80) String timeSlot,@NotNull @Size(max=160) String chiefJudge,@NotNull @Size(max=160) String secretary,boolean banner){}
 PrintSettings get(Long id){service.state(id);return repository.findById(id).orElseGet(()->{var p=new PrintSettings();p.tournamentId=id;return p;});}
 PrintSettings save(Long id,Settings input){service.lock(id);var p=get(id);p.tatami=input.tatami().toUpperCase(java.util.Locale.ROOT);p.timeSlot=input.timeSlot().strip();p.chiefJudge=input.chiefJudge().strip();p.secretary=input.secretary().strip();p.banner=input.banner();return repository.save(p);}
}
@RestController @RequestMapping("/api/tournaments/{id}/print-settings")
class PrintConfigurationApi {
 final PrintConfiguration configuration;PrintConfigurationApi(PrintConfiguration c){configuration=c;}
 @GetMapping Object get(@PathVariable Long id){return configuration.get(id);}
 @PutMapping Object save(@PathVariable Long id,@Valid @RequestBody PrintConfiguration.Settings settings){return configuration.save(id,settings);}
}
