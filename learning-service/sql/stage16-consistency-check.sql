-- Expected: zero rows for each query.
USE campus_learning;
SELECT user_id, lesson_id, COUNT(*) AS copies FROM learning_record
GROUP BY user_id, lesson_id HAVING COUNT(*) > 1;
SELECT user_id, lesson_id, event_type, COUNT(*) AS copies FROM event_outbox
GROUP BY user_id, lesson_id, event_type HAVING COUNT(*) > 1;
SELECT id, user_id, lesson_id FROM learning_record
WHERE progress_seconds < 0 OR duration_seconds < 0 OR completed NOT IN (0, 1)
   OR (completed = 1 AND status <> 2);
SELECT r.user_id, r.lesson_id FROM learning_record r
LEFT JOIN event_outbox o ON o.user_id = r.user_id AND o.lesson_id = r.lesson_id
  AND o.event_type = 'LEARNING_COMPLETED'
WHERE r.completed = 1 AND o.id IS NULL;
