-- Stage14 only. Run before each pressure run, then run scripts/stage14/reset-redis.ps1.
DELETE FROM campus_enrollment.enrollment WHERE training_id = 14001;
UPDATE campus_training.training
SET enrolled_count = 0,
    status = 1,
    enroll_start_time = DATE_SUB(NOW(), INTERVAL 1 DAY),
    enroll_end_time = DATE_ADD(NOW(), INTERVAL 7 DAY)
WHERE id = 14001;
