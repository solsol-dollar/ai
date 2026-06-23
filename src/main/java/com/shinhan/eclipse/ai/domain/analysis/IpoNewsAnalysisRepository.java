package com.shinhan.eclipse.ai.domain.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IpoNewsAnalysisRepository extends JpaRepository<IpoNewsAnalysis, Long> {
    Optional<IpoNewsAnalysis> findByIpoId(Long ipoId);
    boolean existsByIpoIdAndAnalysisStatus(Long ipoId, String analysisStatus);
}
