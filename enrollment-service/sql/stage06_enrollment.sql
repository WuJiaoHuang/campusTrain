CREATE DATABASE IF NOT EXISTS campus_enrollment
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE campus_enrollment;

CREATE TABLE IF NOT EXISTS enrollment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '报名ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    training_id BIGINT NOT NULL COMMENT '培训ID',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0已取消 1已报名',
    enroll_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_training (user_id, training_id),
    KEY idx_user_id (user_id),
    KEY idx_training_id (training_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci COMMENT='培训报名表';
