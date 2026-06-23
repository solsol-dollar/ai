ALTER TABLE ipo_news
    ADD COLUMN content_hash  VARCHAR(64)  NULL,
    ADD COLUMN embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN vector_doc_id VARCHAR(36)  NULL;

CREATE INDEX idx_ipo_news_embedding_status ON ipo_news (embedding_status);
