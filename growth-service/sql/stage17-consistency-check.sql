USE campus_growth;
-- Each query must return zero rows.
SELECT a.user_id,a.total_points,COALESCE(SUM(f.points),0) AS flow_points FROM points_account a LEFT JOIN points_flow f ON f.user_id=a.user_id GROUP BY a.user_id,a.total_points HAVING a.total_points<>COALESCE(SUM(f.points),0);
SELECT event_id,COUNT(*) FROM points_flow GROUP BY event_id HAVING COUNT(*)>1;
SELECT season_id,user_id,COUNT(*) FROM ranking_history GROUP BY season_id,user_id HAVING COUNT(*)>1;
-- Settled seasons must match the immutable MySQL flow snapshot, including tie-break user_id ASC.
WITH aggregated AS (
  SELECT DATE_FORMAT(occurred_at,'%Y-%m') AS season_code,user_id,SUM(points) AS points
  FROM points_flow GROUP BY DATE_FORMAT(occurred_at,'%Y-%m'),user_id
), expected AS (
  SELECT season_code,user_id,points,
    ROW_NUMBER() OVER (PARTITION BY season_code ORDER BY points DESC,user_id ASC) AS rank_no
  FROM aggregated
)
SELECT s.season_code,e.user_id,e.points AS expected_points,e.rank_no AS expected_rank,
       h.points AS actual_points,h.rank_no AS actual_rank
FROM ranking_season s JOIN expected e ON e.season_code=s.season_code
LEFT JOIN ranking_history h ON h.season_id=s.id AND h.user_id=e.user_id
WHERE s.status='SETTLED' AND (h.user_id IS NULL OR h.points<>e.points OR h.rank_no<>e.rank_no);
