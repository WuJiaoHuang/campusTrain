-- Stage14 only. Requires the Stage13 development data (teacher01 = 1101).
-- The script is repeatable and does not truncate any table.

DELETE FROM campus_enrollment.enrollment WHERE training_id = 14001;
DELETE FROM campus_training.training WHERE id = 14001;
DELETE FROM campus_user.sys_user WHERE username LIKE 'stage14_student_%';

INSERT INTO campus_training.training
    (id, title, description, teacher_id, capacity, enrolled_count, status, enroll_start_time, enroll_end_time, start_time, end_time)
VALUES
    (14001, 'Stage14 Redis Lua 并发报名', '仅用于 Redis Lua 与 Redisson 并发报名验证', 1101, 100, 0, 1,
     DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY));

INSERT INTO campus_user.sys_user (id, username, password_hash, nickname, role, status)
WITH RECURSIVE sequence AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM sequence WHERE n < 1000
)
SELECT 140000 + n,
       CONCAT('stage14_student_', LPAD(n, 4, '0')),
       '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy',
       CONCAT('Stage14 学生 ', LPAD(n, 4, '0')),
       'STUDENT',
       1
FROM sequence;

-- Password for every Stage14 account: 123456.
SELECT COUNT(*) AS stage14_student_count FROM campus_user.sys_user WHERE username LIKE 'stage14_student_%';
SELECT id, capacity, enrolled_count, status FROM campus_training.training WHERE id = 14001;
