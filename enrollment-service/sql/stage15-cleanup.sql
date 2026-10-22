-- DEVELOPMENT ONLY. Removes Stage15 verification records only; does not touch enrollment or Stage14 data.
USE campus_enrollment;
DELETE FROM notification_record WHERE message LIKE 'Stage15%';
DELETE FROM learning_event_audit WHERE event_id LIKE 'stage15-%';
DELETE FROM mq_consume_record WHERE event_id LIKE 'stage15-%';
DELETE FROM event_outbox WHERE event_id LIKE 'stage15-%';
