package com.shinhan.eclipse.ai.domain.ipo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface IpoNewsRepository extends JpaRepository<IpoNews, Long> {

    List<IpoNews> findByStatusAndEmbeddingStatusOrderById(String status, String embeddingStatus);

    Optional<IpoNews> findByContentHash(String contentHash);

    @Modifying
    @Query("UPDATE IpoNews n SET n.embeddingStatus = :embeddingStatus, n.vectorDocId = :vectorDocId, n.contentHash = :contentHash WHERE n.id = :id")
    void updateEmbeddingResult(Long id, String embeddingStatus, String vectorDocId, String contentHash);

    @Modifying
    @Query("UPDATE IpoNews n SET n.embeddingStatus = :embeddingStatus WHERE n.id = :id")
    void updateEmbeddingStatus(Long id, String embeddingStatus);
}
