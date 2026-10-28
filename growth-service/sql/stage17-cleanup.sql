USE campus_growth;
-- DEVELOPMENT ONLY. Do not remove a settled season or its historical snapshot.
-- The October fixture is removed only if no October settlement has been finalized.
DELETE FROM points_flow WHERE event_id LIKE 'stage17-test-%'
  AND NOT EXISTS (SELECT 1 FROM ranking_season WHERE season_code='2026-10' AND status='SETTLED');
DELETE FROM points_account WHERE user_id IN (17001,17002,17003,17004)
  AND NOT EXISTS (SELECT 1 FROM ranking_season WHERE season_code='2026-10' AND status='SETTLED');
DELETE FROM ranking_history WHERE season_code LIKE '2099-%';
DELETE FROM ranking_season WHERE season_code LIKE '2099-%';
DELETE FROM job_execution_record WHERE biz_key LIKE '2099-%';
