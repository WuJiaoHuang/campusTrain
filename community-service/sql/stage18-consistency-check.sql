-- Each query should return zero rows after dirty likes are flushed.
USE campus_community;
SELECT q.id FROM question q LEFT JOIN answer a ON a.question_id=q.id AND a.status=1
GROUP BY q.id,q.answer_count HAVING q.answer_count <> COUNT(a.id);
SELECT q.id FROM question q LEFT JOIN like_relation l ON l.target_type='QUESTION' AND l.target_id=q.id AND l.status=1
GROUP BY q.id,q.like_count HAVING q.like_count <> COUNT(l.id);
SELECT a.id FROM answer a LEFT JOIN like_relation l ON l.target_type='ANSWER' AND l.target_id=a.id AND l.status=1
GROUP BY a.id,a.like_count HAVING a.like_count <> COUNT(l.id);
SELECT target_type,target_id,user_id FROM like_relation GROUP BY target_type,target_id,user_id HAVING COUNT(*)>1;
SELECT a.id FROM answer a LEFT JOIN question q ON q.id=a.question_id WHERE q.id IS NULL;
SELECT c.id FROM comment c LEFT JOIN question q ON c.target_type='QUESTION' AND q.id=c.target_id
LEFT JOIN answer a ON c.target_type='ANSWER' AND a.id=c.target_id
WHERE q.id IS NULL AND a.id IS NULL;
SELECT o.event_id FROM event_outbox o GROUP BY o.event_id HAVING COUNT(*)>1;
