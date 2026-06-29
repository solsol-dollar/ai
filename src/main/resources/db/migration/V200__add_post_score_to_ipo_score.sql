ALTER TABLE ipo_score
    ADD COLUMN IF NOT EXISTS post_final_score  INT           NULL,
    ADD COLUMN IF NOT EXISTS post_grade        VARCHAR(30)   NULL,
    ADD COLUMN IF NOT EXISTS post_reason       VARCHAR(500)  NULL,
    ADD COLUMN IF NOT EXISTS post_top_news_ids JSON          NULL,
    ADD COLUMN IF NOT EXISTS post_summary      TEXT          NULL,
    ADD COLUMN IF NOT EXISTS post_news_count   INT           NULL,
    ADD COLUMN IF NOT EXISTS post_scored_at    DATETIME      NULL;
