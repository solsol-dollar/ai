package com.shinhan.eclipse.ai.domain.score;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface IpoScoreRepository extends JpaRepository<IpoScore, Long> {
    Optional<IpoScore> findByIpoId(Long ipoId);
}
