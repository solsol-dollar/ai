package com.shinhan.eclipse.ai.domain.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface IpoNewsAnalysisRepository extends JpaRepository<IpoNewsAnalysis, Long> {
    Optional<IpoNewsAnalysis> findByIpoId(Long ipoId);
    boolean existsByIpoIdAndAnalysisStatus(Long ipoId, String analysisStatus);

    @Query("SELECT a.ipoId FROM IpoNewsAnalysis a WHERE a.analysisStatus = :status")
    List<Long> findIpoIdsByAnalysisStatus(@Param("status") String status);
}
