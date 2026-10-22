-- Stage14 only. It removes only the dedicated pressure-test training and accounts.
DELETE FROM campus_enrollment.enrollment WHERE training_id = 14001;
DELETE FROM campus_training.training WHERE id = 14001;
DELETE FROM campus_user.sys_user WHERE username LIKE 'stage14_student_%';
