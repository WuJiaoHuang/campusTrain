-- Stop traffic to these fixtures first. Never truncate or touch unrelated records.
USE campus_enrollment;
START TRANSACTION;
UPDATE campus_training.training t
JOIN (SELECT training_id, COUNT(*) AS n FROM campus_enrollment.enrollment
      WHERE user_id BETWEEN 912001 AND 912005 AND training_id BETWEEN 912001 AND 912005 AND status = 1
      GROUP BY training_id) active ON active.training_id = t.id
SET t.enrolled_count = GREATEST(0, t.enrolled_count - active.n)
WHERE t.title LIKE 'stage12\_%';
DELETE e FROM campus_enrollment.enrollment e
JOIN campus_training.training t ON t.id = e.training_id AND t.title LIKE 'stage12\_%'
WHERE e.user_id BETWEEN 912001 AND 912005 AND e.training_id BETWEEN 912001 AND 912005;
DELETE FROM campus_training.training
WHERE id BETWEEN 912001 AND 912005 AND title LIKE 'stage12\_%'
  AND NOT EXISTS (SELECT 1 FROM campus_enrollment.enrollment e WHERE e.training_id = training.id);
DELETE FROM campus_user.sys_user
WHERE id BETWEEN 912001 AND 912005 AND username LIKE 'stage12\_%'
  AND NOT EXISTS (SELECT 1 FROM campus_enrollment.enrollment e WHERE e.user_id = sys_user.id)
  AND NOT EXISTS (SELECT 1 FROM campus_training.training t WHERE t.teacher_id = sys_user.id);
COMMIT;
