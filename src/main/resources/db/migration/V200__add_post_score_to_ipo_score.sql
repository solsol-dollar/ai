ALTER TABLE ipo_score
    ADD COLUMN post_final_score  INT           NULL,
    ADD COLUMN post_grade        VARCHAR(30)   NULL,
    ADD COLUMN post_reason       VARCHAR(500)  NULL,
    ADD COLUMN post_top_news_ids JSON          NULL,
    ADD COLUMN post_summary      TEXT          NULL,
    ADD COLUMN post_news_count   INT           NULL,
    ADD COLUMN post_scored_at    DATETIME      NULL;
