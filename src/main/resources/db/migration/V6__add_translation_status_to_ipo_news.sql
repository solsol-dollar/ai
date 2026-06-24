ALTER TABLE ipo_news
    ADD COLUMN vector_doc_id VARCHAR(36) NULL;

ALTER TABLE ipo_news
    ADD COLUMN translation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

CREATE INDEX idx_ipo_news_translation_status ON ipo_news (translation_status);
