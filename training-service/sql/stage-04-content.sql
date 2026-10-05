CREATE TABLE IF NOT EXISTS course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '课程ID',
    training_id BIGINT NOT NULL COMMENT '所属培训ID',
    title VARCHAR(100) NOT NULL COMMENT '课程名称',
    description VARCHAR(1000) DEFAULT NULL COMMENT '课程简介',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_training_id (training_id),
    INDEX idx_training_sort (training_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci COMMENT='培训课程表';

CREATE TABLE IF NOT EXISTS chapter (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '章节ID',
    course_id BIGINT NOT NULL COMMENT '所属课程ID',
    title VARCHAR(100) NOT NULL COMMENT '章节名称',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_course_id (course_id),
    INDEX idx_course_sort (course_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci COMMENT='课程章节表';

CREATE TABLE IF NOT EXISTS lesson (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '课时ID',
    chapter_id BIGINT NOT NULL COMMENT '所属章节ID',
    title VARCHAR(100) NOT NULL COMMENT '课时名称',
    lesson_type TINYINT NOT NULL DEFAULT 0 COMMENT '课时类型：0视频 1图文',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    video_object_key VARCHAR(500) DEFAULT NULL COMMENT 'OSS对象Key，阶段11使用',
    video_duration INT DEFAULT NULL COMMENT '视频时长，单位秒',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_chapter_id (chapter_id),
    INDEX idx_chapter_sort (chapter_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci COMMENT='课程课时表';
