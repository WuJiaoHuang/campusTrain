USE campus_training;

INSERT INTO training (
    id,
    title,
    description,
    cover_url,
    teacher_id,
    capacity,
    enrolled_count,
    status,
    enroll_start_time,
    enroll_end_time,
    start_time,
    end_time,
    create_time,
    update_time
)
VALUES
    (90001, '阶段6-正常可报名', 'OpenFeign 报名链路测试：当前可报名。', NULL, 1, 30, 0, 1,
        DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY),
        DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY), NOW(), NOW()),
    (90002, '阶段6-非报名状态', 'OpenFeign 报名链路测试：草稿状态。', NULL, 1, 30, 0, 0,
        DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY),
        DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY), NOW(), NOW()),
    (90003, '阶段6-名额已满', 'OpenFeign 报名链路测试：名额已满。', NULL, 1, 30, 30, 1,
        DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY),
        DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY), NOW(), NOW()),
    (90004, '阶段6-尚未开始报名', 'OpenFeign 报名链路测试：报名尚未开始。', NULL, 1, 30, 0, 1,
        DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 2 DAY),
        DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY), NOW(), NOW()),
    (90005, '阶段6-已结束报名', 'OpenFeign 报名链路测试：报名已经结束。', NULL, 1, 30, 0, 1,
        DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY),
        DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 8 DAY), NOW(), NOW())
ON DUPLICATE KEY UPDATE
    title = VALUES(title),
    description = VALUES(description),
    cover_url = VALUES(cover_url),
    teacher_id = VALUES(teacher_id),
    capacity = VALUES(capacity),
    enrolled_count = VALUES(enrolled_count),
    status = VALUES(status),
    enroll_start_time = VALUES(enroll_start_time),
    enroll_end_time = VALUES(enroll_end_time),
    start_time = VALUES(start_time),
    end_time = VALUES(end_time),
    update_time = NOW();
