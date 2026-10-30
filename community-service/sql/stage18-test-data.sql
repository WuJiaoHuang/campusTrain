-- DEVELOPMENT ONLY. Isolated sample in campus_community; requires Stage13 user 1001 and training 2003.
USE campus_community;
INSERT INTO question(id,training_id,course_id,lesson_id,user_id,title,content,answer_count,comment_count,like_count,status,create_time,update_time)
VALUES(18001,2003,NULL,NULL,1001,'Stage18 示例问题','用于问答和点赞验收',0,0,0,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE id=id;
