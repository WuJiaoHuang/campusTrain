-- DEVELOPMENT ONLY - DESTRUCTIVE RESET
-- This script truncates only CampusTrain business tables in campus_user,
-- campus_training and campus_enrollment. Do not run in production.

SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE campus_enrollment.enrollment;
TRUNCATE TABLE campus_training.lesson;
TRUNCATE TABLE campus_training.chapter;
TRUNCATE TABLE campus_training.course;
TRUNCATE TABLE campus_training.training;
TRUNCATE TABLE campus_user.sys_user;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO campus_user.sys_user (id, username, password_hash, nickname, role, status) VALUES
(1001, 'student01', '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy', '学生一', 'STUDENT', 1),
(1002, 'student02', '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy', '学生二', 'STUDENT', 1),
(1003, 'student03', '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy', '学生三', 'STUDENT', 1),
(1004, 'student04', '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy', '学生四', 'STUDENT', 1),
(1005, 'student_disabled', '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy', '禁用学生', 'STUDENT', 0),
(1101, 'teacher01', '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy', '教师一', 'TEACHER', 1),
(1102, 'teacher02', '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy', '教师二', 'TEACHER', 1),
(1201, 'admin01', '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy', '管理员一', 'ADMIN', 1);

INSERT INTO campus_training.training
    (id, title, description, teacher_id, capacity, enrolled_count, status, enroll_start_time, enroll_end_time, start_time, end_time)
VALUES
(2001, 'Java 基础草稿课', '草稿状态培训', 1101, 30, 0, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 14 DAY), DATE_ADD(NOW(), INTERVAL 15 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(2002, 'Spring Boot 满员实战', '正在报名且满员', 1101, 3, 3, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
(2003, '微服务报名实战', '正在报名且部分报名', 1101, 5, 2, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 12 DAY), DATE_ADD(NOW(), INTERVAL 35 DAY)),
(2004, 'Redis 缓存入门', '正在报名且有人报名', 1101, 10, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 12 DAY), DATE_ADD(NOW(), INTERVAL 15 DAY), DATE_ADD(NOW(), INTERVAL 40 DAY)),
(2005, '数据结构预告课', '报名尚未开始', 1101, 10, 0, 1, DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 12 DAY), DATE_ADD(NOW(), INTERVAL 14 DAY), DATE_ADD(NOW(), INTERVAL 40 DAY)),
(2006, '旧版 Java 课程', '报名窗口已结束', 1102, 10, 0, 1, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 20 DAY)),
(2007, '云原生进行中', '进行中培训', 1102, 20, 0, 2, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 20 DAY)),
(2008, '算法已结束班', '已结束培训', 1102, 20, 0, 3, DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2009, '前端工程化训练', 'teacher02 的报名中培训', 1102, 2, 1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY), DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 25 DAY)),
(2010, '无人报名样例课', '零报名测试培训', 1102, 8, 0, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY), DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 25 DAY));

INSERT INTO campus_training.course (id, training_id, title, description, sort) VALUES
(3001, 2003, '服务治理基础', 'Spring Cloud 基础', 10),
(3002, 2003, '报名链路实战', '报名业务实现', 20),
(3003, 2004, '缓存原理', '多级缓存基础', 10),
(3004, 2009, '工程化规范', '前端构建与发布', 10);
INSERT INTO campus_training.chapter (id, course_id, title, sort) VALUES
(4001, 3001, '服务发现', 10), (4002, 3001, '流量治理', 20),
(4003, 3002, '报名一致性', 10), (4004, 3003, 'Cache Aside', 10),
(4005, 3003, '缓存风险', 20), (4006, 3004, '构建工具', 10);
INSERT INTO campus_training.lesson (id, chapter_id, title, lesson_type, sort) VALUES
(5001, 4001, 'Nacos 服务发现', 1, 10), (5002, 4001, 'OpenFeign 调用', 1, 20),
(5003, 4002, 'Sentinel 限流视频', 0, 10), (5004, 4002, '熔断与降级', 1, 20),
(5005, 4003, '本地事务与补偿', 1, 10), (5006, 4003, '报名状态机', 1, 20),
(5007, 4004, '缓存读写流程', 1, 10), (5008, 4005, '穿透与击穿', 1, 10),
(5009, 4005, '缓存雪崩视频', 0, 20), (5010, 4006, 'Vite 构建', 1, 10);

INSERT INTO campus_enrollment.enrollment (id, user_id, training_id, status, enroll_time) VALUES
(6001, 1001, 2002, 1, NOW()), (6002, 1002, 2002, 1, NOW()), (6003, 1003, 2002, 1, NOW()),
(6004, 1001, 2003, 1, NOW()), (6005, 1002, 2003, 1, NOW()), (6006, 1004, 2003, 0, NOW()),
(6007, 1001, 2004, 1, NOW()), (6008, 1002, 2009, 1, NOW()), (6009, 1003, 2006, 0, NOW());

-- Consistency checks: every query must return zero rows.
SELECT t.id, t.enrolled_count, COUNT(e.id) AS active_count
FROM campus_training.training t LEFT JOIN campus_enrollment.enrollment e ON e.training_id = t.id AND e.status = 1
GROUP BY t.id, t.enrolled_count HAVING t.enrolled_count <> COUNT(e.id);
SELECT id, enrolled_count, capacity FROM campus_training.training WHERE enrolled_count > capacity;
SELECT e.* FROM campus_enrollment.enrollment e LEFT JOIN campus_user.sys_user u ON u.id = e.user_id WHERE u.id IS NULL;
SELECT e.* FROM campus_enrollment.enrollment e JOIN campus_user.sys_user u ON u.id = e.user_id WHERE u.role <> 'STUDENT';
SELECT t.* FROM campus_training.training t LEFT JOIN campus_user.sys_user u ON u.id = t.teacher_id WHERE u.id IS NULL;
SELECT t.* FROM campus_training.training t JOIN campus_user.sys_user u ON u.id = t.teacher_id WHERE u.role <> 'TEACHER';
SELECT user_id, training_id, COUNT(*) AS duplicate_count FROM campus_enrollment.enrollment GROUP BY user_id, training_id HAVING COUNT(*) > 1;
SELECT c.* FROM campus_training.course c LEFT JOIN campus_training.training t ON t.id = c.training_id WHERE t.id IS NULL;
SELECT c.* FROM campus_training.chapter c LEFT JOIN campus_training.course p ON p.id = c.course_id WHERE p.id IS NULL;
SELECT l.* FROM campus_training.lesson l LEFT JOIN campus_training.chapter c ON c.id = l.chapter_id WHERE c.id IS NULL;
SELECT t.id FROM campus_training.training t LEFT JOIN campus_enrollment.enrollment e ON e.training_id = t.id AND e.status = 1 GROUP BY t.id, t.enrolled_count HAVING t.enrolled_count <> COUNT(e.id);
