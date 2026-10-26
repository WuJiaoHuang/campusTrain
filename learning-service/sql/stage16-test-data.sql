-- DEVELOPMENT ONLY: reserved Stage16 IDs. Verify they are not used by unrelated data before execution.
-- Uses the existing student01 (1001) and teacher01 (1101); no video object is fabricated.
INSERT INTO campus_training.training
    (id,title,teacher_id,capacity,enrolled_count,status,enroll_start_time,enroll_end_time,start_time,end_time)
SELECT 16001,'Stage16 learning test',1101,20,1,1,DATE_SUB(NOW(),INTERVAL 1 DAY),DATE_ADD(NOW(),INTERVAL 30 DAY),NOW(),DATE_ADD(NOW(),INTERVAL 60 DAY)
WHERE NOT EXISTS (SELECT 1 FROM campus_training.training WHERE id=16001);
INSERT INTO campus_training.course (id,training_id,title,sort)
SELECT 16001,16001,'Stage16 test course',1 WHERE NOT EXISTS (SELECT 1 FROM campus_training.course WHERE id=16001);
INSERT INTO campus_training.chapter (id,course_id,title,sort)
SELECT 16001,16001,'Stage16 test chapter',1 WHERE NOT EXISTS (SELECT 1 FROM campus_training.chapter WHERE id=16001);
INSERT INTO campus_training.lesson (id,chapter_id,title,lesson_type,sort)
SELECT 16001,16001,'Stage16 text lesson 1',1,1 WHERE NOT EXISTS (SELECT 1 FROM campus_training.lesson WHERE id=16001);
INSERT INTO campus_training.lesson (id,chapter_id,title,lesson_type,sort)
SELECT 16002,16001,'Stage16 text lesson 2',1,2 WHERE NOT EXISTS (SELECT 1 FROM campus_training.lesson WHERE id=16002);
INSERT INTO campus_training.lesson (id,chapter_id,title,lesson_type,sort)
SELECT 16003,16001,'Stage16 unbound video',0,3 WHERE NOT EXISTS (SELECT 1 FROM campus_training.lesson WHERE id=16003);
INSERT INTO campus_enrollment.enrollment (user_id,training_id,status,enroll_time,create_time,update_time)
SELECT 1001,16001,1,NOW(),NOW(),NOW() WHERE NOT EXISTS
    (SELECT 1 FROM campus_enrollment.enrollment WHERE user_id=1001 AND training_id=16001);
