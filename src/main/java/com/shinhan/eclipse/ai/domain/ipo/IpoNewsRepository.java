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

    List<IpoNews> findByStatusAndEmbeddingStatusInOrderById(String status, List<String> embeddingStatuses);

    @Query("SELECT DISTINCT n.ipoId FROM IpoNews n WHERE n.embeddingStatus = :status AND n.status = 'ACTIVE'")
    List<Long> findDistinctIpoIdByEmbeddingStatus(@Param("status") String status);

    Optional<IpoNews> findByContentHash(String contentHash);

    Optional<IpoNews> findByContentHashAndEmbeddingStatus(String contentHash, String embeddingStatus);

    List<IpoNews> findByIpoIdAndStatusAndEmbeddingStatus(Long ipoId, String status, String embeddingStatus);

    @Modifying
    @Transactional
    @Query("UPDATE IpoNews n SET n.embeddingStatus = :embeddingStatus, n.vectorDocId = :vectorDocId, n.contentHash = :contentHash WHERE n.id = :id")
    void updateEmbeddingResult(Long id, String embeddingStatus, String vectorDocId, String contentHash);

    @Modifying
    @Transactional
    @Query("UPDATE IpoNews n SET n.embeddingStatus = :embeddingStatus WHERE n.id = :id")
    void updateEmbeddingStatus(Long id, String embeddingStatus);

    @Modifying
    @Transactional
    @Query("UPDATE IpoNews n SET n.titleKo = :titleKo, n.summary = :summaryKo, n.translationStatus = :status WHERE n.id = :id")
    void updateTranslation(@Param("id") Long id, @Param("titleKo") String titleKo,
                           @Param("summaryKo") String summaryKo, @Param("status") String status);

    @Modifying
    @Transactional
    @Query("UPDATE IpoNews n SET n.translationStatus = :status WHERE n.id = :id")
    void updateTranslationStatus(@Param("id") Long id, @Param("status") String status);

    @Modifying
    @Transactional
    @Query("UPDATE IpoNews n SET n.titleKo = :titleKo, n.translationStatus = 'COMPLETED' WHERE n.id = :id")
    void updateTitleKo(@Param("id") Long id, @Param("titleKo") String titleKo);

    @Modifying
    @Transactional
    @Query("UPDATE IpoNews n SET n.contentKo = :contentKo WHERE n.id = :id")
    void updateContentKo(@Param("id") Long id, @Param("contentKo") String contentKo);
}
