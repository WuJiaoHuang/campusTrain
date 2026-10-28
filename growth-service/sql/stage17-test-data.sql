USE campus_growth;
-- Used only to reproduce MySQL-to-Redis rebuild. It is idempotent by event_id.
INSERT IGNORE INTO points_account(user_id,total_points,update_time) VALUES
  (17001,30,NOW()),(17002,20,NOW()),(17003,10,NOW());
INSERT IGNORE INTO points_flow(event_id,user_id,source_type,source_id,points,occurred_at,create_time) VALUES
  ('stage17-test-a1',17001,'LEARNING_COMPLETED',17001,10,'2026-10-15 10:00:00',NOW()),
  ('stage17-test-a2',17001,'LEARNING_COMPLETED',17002,10,'2026-10-15 10:01:00',NOW()),
  ('stage17-test-a3',17001,'LEARNING_COMPLETED',17003,10,'2026-10-15 10:02:00',NOW()),
  ('stage17-test-b1',17002,'LEARNING_COMPLETED',17004,10,'2026-10-15 10:03:00',NOW()),
  ('stage17-test-b2',17002,'LEARNING_COMPLETED',17005,10,'2026-10-15 10:04:00',NOW()),
  ('stage17-test-c1',17003,'LEARNING_COMPLETED',17006,10,'2026-10-15 10:05:00',NOW());
