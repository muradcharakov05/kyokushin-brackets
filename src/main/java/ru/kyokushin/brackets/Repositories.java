package ru.kyokushin.brackets;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
import ru.kyokushin.brackets.Domain.*;
interface Tournaments extends JpaRepository<Tournament,Long> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from Tournament t where t.id=:id") Optional<Tournament> lock(@Param("id") Long id);
}
interface Categories extends JpaRepository<Category,Long> { List<Category> findByTournamentIdOrderById(Long id); }
interface Athletes extends JpaRepository<Athlete,Long> { List<Athlete> findByTournamentIdOrderById(Long id); }
interface Bouts extends JpaRepository<Bout,Long> { List<Bout> findByCategoryIdOrderByRoundNumberAscPositionAsc(Long id); void deleteByCategoryId(Long id); }

interface RegistrationBatches extends JpaRepository<Domain.RegistrationBatch,Long> { List<Domain.RegistrationBatch> findByTournamentIdOrderByIdDesc(Long id); }
interface PrintSettingsRepository extends JpaRepository<Domain.PrintSettings,Long> {}
