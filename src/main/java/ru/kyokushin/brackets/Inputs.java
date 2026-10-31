package ru.kyokushin.brackets;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
public final class Inputs {
 public record TournamentInput(@NotBlank @Size(max=160) String name,@NotNull LocalDate eventDate,@NotNull @Size(max=200) String venue) {}
 public record CategoryInput(@NotBlank @Size(max=160) String name,@Pattern(regexp="M|F") @NotNull String sex,
  @Min(4) @Max(100) int minAge,@Min(4) @Max(100) int maxAge,
  @NotNull @DecimalMin("0") @DecimalMax("499") @Digits(integer=3,fraction=2) BigDecimal minWeight,@NotNull @DecimalMin("1") @DecimalMax("500") @Digits(integer=3,fraction=2) BigDecimal maxWeight,
  @Min(0) @Max(6) int minBelt,@Min(0) @Max(6) int maxBelt) {}
 public record AthleteInput(@NotBlank @Size(max=160) String name,@NotNull @Size(max=160) String club,
  @NotNull LocalDate birthDate,@NotNull @Pattern(regexp="M|F") String sex,
  @NotNull @DecimalMin("1") @DecimalMax("500") @Digits(integer=3,fraction=2) BigDecimal weight,@Min(0) @Max(6) int belt) {}
 public record WinnerInput(@NotNull Long athleteId) {}
 public record DrawInput(Long seed) {}
}
