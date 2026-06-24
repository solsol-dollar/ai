CREATE TABLE ipo_news_analysis (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    ipo_id              BIGINT NOT NULL,
    ticker              VARCHAR(20),
    analysis_status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sentiment_score     DECIMAL(5,4),
    signal_strength     DECIMAL(5,4),
    consistency_score   DECIMAL(5,4),
    risk_factors        JSON,
    source_news_indexes JSON,
    raw_llm_response    TEXT,
    news_count          INT DEFAULT 0,
    analyzed_at         DATETIME,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ipo_analysis (ipo_id)
);
