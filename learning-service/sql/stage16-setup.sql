-- Stage16 only. Does not modify existing CampusTrain databases.
CREATE DATABASE IF NOT EXISTS campus_learning CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus_learning;

CREATE TABLE IF NOT EXISTS learning_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    training_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    chapter_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    progress_seconds INT NOT NULL DEFAULT 0,
    duration_seconds INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    completed TINYINT NOT NULL DEFAULT 0,
    first_start_time DATETIME NOT NULL,
    last_learn_time DATETIME NOT NULL,
    complete_time DATETIME NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_lesson (user_id, lesson_id),
    KEY idx_user_training_completed (user_id, training_id, completed),
    KEY idx_user_chapter_completed (user_id, chapter_id, completed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NEW',
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    UNIQUE KEY uk_outbox_completion (user_id, lesson_id, event_type),
    KEY idx_outbox_status_id (status, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
