-- DB 불일치 방어: vector_doc_id 가 없는 경우 추가
ALTER TABLE ipo_news
    ADD COLUMN IF NOT EXISTS vector_doc_id VARCHAR(36) NULL;

ALTER TABLE ipo_news
    ADD COLUMN IF NOT EXISTS translation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

DROP INDEX IF EXISTS idx_ipo_news_translation_status ON ipo_news;
CREATE INDEX idx_ipo_news_translation_status ON ipo_news (translation_status);
