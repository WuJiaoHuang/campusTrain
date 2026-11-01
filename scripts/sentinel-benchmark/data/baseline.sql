-- Read-only Stage A safety baseline. Each query targets benchmark data only.
SELECT id, username, role, status FROM campus_user.sys_user WHERE id = 1990001;
SELECT id, capacity, enrolled_count, status FROM campus_training.training WHERE id = 199001;
SELECT COUNT(*) AS active_enrollments FROM campus_enrollment.enrollment
 WHERE training_id = 199001 AND status = 1;
SELECT id, user_id, training_id, status FROM campus_enrollment.enrollment
 WHERE user_id = 1990001 AND training_id = 199001;
SELECT user_id, lesson_id, progress_seconds, completed FROM campus_learning.learning_record
 WHERE user_id = 1990001 AND lesson_id = 199001;
SELECT target_type, target_id, user_id, status FROM campus_community.like_relation
 WHERE user_id = 1990001 AND
       ((target_type = 'QUESTION' AND target_id = 199000001)
        OR (target_type = 'ANSWER' AND target_id = 199000001));
SELECT id, like_count FROM campus_community.question WHERE id = 199000001;
SELECT id, like_count FROM campus_community.answer WHERE id = 199000001;
