-- Run in one session; reserve a dedicated hierarchy without touching existing data.
INSERT INTO training (title, teacher_id, capacity, enrolled_count, status)
VALUES ('Stage11 OSS Test Training', 1, 1, 0, 0);
SET @stage11_training_id = LAST_INSERT_ID();
INSERT INTO course (training_id, title, sort) VALUES (@stage11_training_id, 'Stage11 OSS Test Course', 0);
SET @stage11_course_id = LAST_INSERT_ID();
INSERT INTO chapter (course_id, title, sort) VALUES (@stage11_course_id, 'Stage11 OSS Test Chapter', 0);
SET @stage11_chapter_id = LAST_INSERT_ID();
INSERT INTO lesson (chapter_id, title, lesson_type, sort) VALUES (@stage11_chapter_id, 'Stage11 OSS Test Lesson', 0, 0);
SELECT @stage11_training_id AS training_id, @stage11_course_id AS course_id,
       @stage11_chapter_id AS chapter_id, LAST_INSERT_ID() AS lesson_id;
