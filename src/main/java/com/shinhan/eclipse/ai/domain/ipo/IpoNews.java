package com.shinhan.eclipse.ai.domain.ipo;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "ipo_news")
public class IpoNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ipoId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 100)
    private String source;

    private LocalDateTime publishedAt;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";   // BaseEntity의 소프트 삭제 필드

    @Column(length = 64)
    private String contentHash;

    @Column(length = 255)
    private String titleKo;

    @Column(nullable = false, length = 20)
    private String embeddingStatus = "PENDING";

    @Column(length = 36)
    private String vectorDocId;

    @Column(nullable = false, length = 20)
    private String translationStatus = "PENDING";

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
