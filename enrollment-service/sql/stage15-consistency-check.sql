-- All queries should return zero rows after the Stage15 verification.
USE campus_enrollment;

SELECT event_id FROM event_outbox GROUP BY event_id HAVING COUNT(*) > 1;
SELECT event_id, consumer_name FROM mq_consume_record GROUP BY event_id, consumer_name HAVING COUNT(*) > 1;
SELECT event_id FROM notification_record GROUP BY event_id HAVING COUNT(*) > 1;
SELECT event_id FROM learning_event_audit GROUP BY event_id HAVING COUNT(*) > 1;

-- Every consumer side effect must have an idempotency record for its named consumer.
SELECT n.event_id
FROM notification_record n
LEFT JOIN mq_consume_record r ON r.event_id = n.event_id AND r.consumer_name = 'enrollment-success-notification'
WHERE r.id IS NULL;
SELECT a.event_id
FROM learning_event_audit a
LEFT JOIN mq_consume_record r ON r.event_id = a.event_id AND r.consumer_name = 'learning-completed-points'
WHERE r.id IS NULL;
