ALTER TABLE ipo_news
    ADD COLUMN translation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' AFTER vector_doc_id;

CREATE INDEX idx_ipo_news_translation_status ON ipo_news (translation_status);
