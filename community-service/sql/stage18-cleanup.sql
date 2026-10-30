-- DEVELOPMENT ONLY. Only deletes the reserved Stage18 sample and its relations.
USE campus_community;
DELETE l FROM like_relation l JOIN question q ON q.id=l.target_id
WHERE l.target_type='QUESTION' AND q.id=18001 AND q.title='Stage18 示例问题';
DELETE c FROM comment c JOIN question q ON q.id=c.target_id
WHERE c.target_type='QUESTION' AND q.id=18001 AND q.title='Stage18 示例问题';
DELETE FROM question WHERE id=18001 AND title='Stage18 示例问题';
