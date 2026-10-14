CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt密码哈希',
    nickname VARCHAR(100) DEFAULT NULL COMMENT '昵称',
    role VARCHAR(20) NOT NULL COMMENT '角色：STUDENT TEACHER ADMIN',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username),
    INDEX idx_role (role),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

UPDATE sys_user
SET password_hash = '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy'
WHERE password_hash IS NULL OR password_hash = '';

ALTER TABLE sys_user
    MODIFY password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt密码哈希';

INSERT INTO sys_user (username, password_hash, nickname, role, status)
VALUES
    ('student01', '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy', '学生一', 'STUDENT', 1),
    ('teacher01', '$2a$10$Jph/Ue8y6dpYy3C1OHeaVuTpC587ALM5mtFvJLS/ZFP1mlZDQnpt6', '教师一', 'TEACHER', 1),
    ('admin01', '$2a$10$yFs9R9aGM2J3F32O85EWoeQUd6Tt.P/J3tCqeTRHSMSQ8hk39WJk2', '管理员一', 'ADMIN', 1)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    nickname = VALUES(nickname),
    role = VALUES(role),
    status = VALUES(status);
