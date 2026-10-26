-- DEVELOPMENT ONLY. Run only when the IDs and titles are confirmed to be Stage16 test data.
-- Redis progress keys/dirty members require separate targeted removal after stopping learning-service.
DELETE FROM campus_learning.event_outbox WHERE user_id=1001 AND lesson_id IN (16001,16002,16003);
DELETE FROM campus_learning.learning_record WHERE user_id=1001 AND lesson_id IN (16001,16002,16003);
DELETE FROM campus_enrollment.enrollment WHERE user_id=1001 AND training_id=16001;
DELETE FROM campus_training.lesson WHERE chapter_id=16001 AND id IN (16001,16002,16003) AND title LIKE 'Stage16 %';
DELETE FROM campus_training.chapter WHERE id=16001 AND course_id=16001 AND title='Stage16 test chapter';
DELETE FROM campus_training.course WHERE id=16001 AND training_id=16001 AND title='Stage16 test course';
DELETE FROM campus_training.training WHERE id=16001 AND title='Stage16 learning test';
