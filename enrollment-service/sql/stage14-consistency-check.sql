-- Replace nothing: this script targets the Stage14 pressure-test training (14001).
SELECT
    t.id AS training_id,
    t.capacity,
    t.enrolled_count,
    COUNT(e.id) AS active_enrollment_count,
    CASE WHEN t.enrolled_count = COUNT(e.id) THEN 'OK' ELSE 'MISMATCH' END AS db_consistency
FROM campus_training.training t
LEFT JOIN campus_enrollment.enrollment e ON e.training_id = t.id AND e.status = 1
WHERE t.id = 14001
GROUP BY t.id, t.capacity, t.enrolled_count;

SELECT user_id, training_id, COUNT(*) AS duplicate_rows
FROM campus_enrollment.enrollment
WHERE training_id = 14001
GROUP BY user_id, training_id
HAVING COUNT(*) > 1;
