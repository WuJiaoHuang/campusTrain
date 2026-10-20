-- Dedicated Stage12 fixtures. Run cleanup first only after confirming these IDs
-- are not used by anything other than Stage12. Existing rows are never updated.
START TRANSACTION;
INSERT INTO campus_user.sys_user (id, username, password_hash, nickname, role, status) VALUES
(912001, 'stage12_student', '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy', 'Stage12 student', 'STUDENT', 1),
(912002, 'stage12_teacher', '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy', 'Stage12 teacher', 'TEACHER', 1),
(912003, 'stage12_admin', '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy', 'Stage12 admin', 'ADMIN', 1),
(912004, 'stage12_other_teacher', '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy', 'Stage12 other teacher', 'TEACHER', 1),
(912005, 'stage12_disabled', '$2a$10$jH9SrhYFNjhFHSvTEIthqeroR2mkiP2nrPP6M61SE7.dA.EBw2rSy', 'Stage12 disabled', 'STUDENT', 0);
INSERT INTO campus_training.training (id, title, teacher_id, capacity, enrolled_count, status, enroll_start_time, enroll_end_time) VALUES
(912001, 'stage12_open', 912002, 1, 0, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY)),
(912002, 'stage12_closed', 912002, 10, 0, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY)),
(912003, 'stage12_not_started', 912002, 10, 0, 1, DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY)),
(912004, 'stage12_ended', 912002, 10, 0, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(912005, 'stage12_full', 912002, 0, 0, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY));
COMMIT;
