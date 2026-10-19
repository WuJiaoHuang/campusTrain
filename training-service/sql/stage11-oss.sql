-- Execute once against campus_training after checking whether these columns exist.
ALTER TABLE lesson
    ADD COLUMN video_size BIGINT NULL COMMENT 'Video size in bytes',
    ADD COLUMN video_content_type VARCHAR(100) NULL COMMENT 'Video content type';
