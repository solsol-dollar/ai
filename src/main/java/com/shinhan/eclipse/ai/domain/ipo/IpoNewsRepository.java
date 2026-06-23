package com.shinhan.eclipse.ai.domain.ipo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface IpoNewsRepository extends JpaRepository<IpoNews, Long> {

    List<IpoNews> findByStatusAndEmbeddingStatusOrderById(String status, String embeddingStatus);

    @Query("SELECT DISTINCT n.ipoId FROM IpoNews n WHERE n.embeddingStatus = :status AND n.status = 'ACTIVE'")
    List<Long> findDistinctIpoIdByEmbeddingStatus(@Param("status") String status);

    Optional<IpoNews> findByContentHash(String contentHash);

    List<IpoNews> findByIpoIdAndStatusAndEmbeddingStatus(Long ipoId, String status, String embeddingStatus);

    @Modifying
    @Transactional
    @Query("UPDATE IpoNews n SET n.embeddingStatus = :embeddingStatus, n.vectorDocId = :vectorDocId, n.contentHash = :contentHash WHERE n.id = :id")
    void updateEmbeddingResult(Long id, String embeddingStatus, String vectorDocId, String contentHash);

    @Modifying
    @Transactional
    @Query("UPDATE IpoNews n SET n.embeddingStatus = :embeddingStatus WHERE n.id = :id")
    void updateEmbeddingStatus(Long id, String embeddingStatus);
}
