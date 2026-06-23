package com.shinhan.eclipse.ai.domain.score;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface IpoScoreRepository extends JpaRepository<IpoScore, Long> {

    Optional<IpoScore> findByIpoId(Long ipoId);

    List<IpoScore> findBySummaryIsNullAndTopNewsIdsIsNotNull();

    @Modifying
    @Transactional
    @Query("UPDATE IpoScore s SET s.summary = :summary WHERE s.ipoId = :ipoId")
    void updateSummary(@Param("ipoId") Long ipoId, @Param("summary") String summary);
}
