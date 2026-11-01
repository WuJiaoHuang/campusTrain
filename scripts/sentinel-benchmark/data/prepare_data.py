"""Create isolated, idempotent Stage A data. Requires pymysql and bcrypt."""

import os

import bcrypt
import pymysql

USER_ID = 1_990_001
TRAINING_ID = 199_001
COURSE_ID = 199_001
CHAPTER_ID = 199_001
LESSON_ID = 199_001
QUESTION_ID = 199_000_001
ANSWER_ID = 199_000_001
USERNAME = "benchmark_sentinel_student"
SOURCE_VIDEO_LESSON_ID = 16_003


def ensure_row(cursor, database, table, row_id, expected_column, expected_value):
    cursor.execute(
        f"SELECT `{expected_column}` FROM `{database}`.`{table}` WHERE id=%s",
        (row_id,),
    )
    row = cursor.fetchone()
    if row is not None and row[0] != expected_value:
        raise RuntimeError(f"Reserved benchmark ID {database}.{table}:{row_id} belongs to other data")
    return row is not None


def main():
    password = os.environ.get("BENCHMARK_PASSWORD")
    if not password:
        raise SystemExit("Set BENCHMARK_PASSWORD in the environment; it is never stored in the repository")
    connection = pymysql.connect(
        host=os.environ.get("MYSQL_HOST", "192.168.10.102"),
        port=int(os.environ.get("MYSQL_PORT", "3306")),
        user=os.environ.get("MYSQL_USERNAME", "root"),
        password=os.environ.get("MYSQL_PASSWORD", "123"),
        charset="utf8mb4",
        autocommit=False,
    )
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT id FROM campus_user.sys_user WHERE username=%s", (USERNAME,))
            existing_user = cursor.fetchone()
            if existing_user and existing_user[0] != USER_ID:
                raise RuntimeError("Benchmark username belongs to another ID")
            if not ensure_row(cursor, "campus_user", "sys_user", USER_ID, "username", USERNAME):
                password_hash = bcrypt.hashpw(password.encode(), bcrypt.gensalt()).decode()
                cursor.execute(
                    "INSERT INTO campus_user.sys_user(id,username,password_hash,nickname,role,status) "
                    "VALUES(%s,%s,%s,'Sentinel benchmark student','STUDENT',1)",
                    (USER_ID, USERNAME, password_hash),
                )
            else:
                cursor.execute(
                    "SELECT password_hash FROM campus_user.sys_user WHERE id=%s", (USER_ID,)
                )
                stored_hash = cursor.fetchone()[0]
                if not bcrypt.checkpw(password.encode(), stored_hash.encode()):
                    raise RuntimeError("Existing benchmark account has another password; do not overwrite it")

            cursor.execute(
                "SELECT video_object_key,video_duration,video_size,video_content_type "
                "FROM campus_training.lesson WHERE id=%s AND video_object_key IS NOT NULL",
                (SOURCE_VIDEO_LESSON_ID,),
            )
            video = cursor.fetchone()
            if not video:
                raise RuntimeError("Real Stage16 video binding is missing; no OSS key will be fabricated")
            if not ensure_row(cursor, "campus_training", "training", TRAINING_ID, "title", "benchmark_sentinel_training"):
                cursor.execute(
                    "INSERT INTO campus_training.training(id,title,description,teacher_id,capacity,enrolled_count,status,"
                    "enroll_start_time,enroll_end_time,start_time,end_time) "
                    "VALUES(%s,'benchmark_sentinel_training','Isolated Sentinel smoke data',1101,20,1,1,"
                    "NOW()-INTERVAL 1 DAY,NOW()+INTERVAL 365 DAY,NOW()+INTERVAL 366 DAY,NOW()+INTERVAL 730 DAY)",
                    (TRAINING_ID,),
                )
            if not ensure_row(cursor, "campus_training", "course", COURSE_ID, "title", "benchmark_sentinel_course"):
                cursor.execute(
                    "INSERT INTO campus_training.course(id,training_id,title,sort) VALUES(%s,%s,'benchmark_sentinel_course',1)",
                    (COURSE_ID, TRAINING_ID),
                )
            if not ensure_row(cursor, "campus_training", "chapter", CHAPTER_ID, "title", "benchmark_sentinel_chapter"):
                cursor.execute(
                    "INSERT INTO campus_training.chapter(id,course_id,title,sort) VALUES(%s,%s,'benchmark_sentinel_chapter',1)",
                    (CHAPTER_ID, COURSE_ID),
                )
            if not ensure_row(cursor, "campus_training", "lesson", LESSON_ID, "title", "benchmark_sentinel_video"):
                cursor.execute(
                    "INSERT INTO campus_training.lesson(id,chapter_id,title,lesson_type,sort,video_object_key,"
                    "video_duration,video_size,video_content_type) VALUES(%s,%s,'benchmark_sentinel_video',0,1,%s,%s,%s,%s)",
                    (LESSON_ID, CHAPTER_ID, *video),
                )
            cursor.execute(
                "SELECT status FROM campus_enrollment.enrollment WHERE user_id=%s AND training_id=%s",
                (USER_ID, TRAINING_ID),
            )
            enrollment = cursor.fetchone()
            if enrollment is None:
                cursor.execute(
                    "INSERT INTO campus_enrollment.enrollment(user_id,training_id,status) VALUES(%s,%s,1)",
                    (USER_ID, TRAINING_ID),
                )
            elif enrollment[0] != 1:
                raise RuntimeError("Benchmark enrollment is cancelled; restore it through the business API")
            if not ensure_row(cursor, "campus_community", "question", QUESTION_ID, "title", "benchmark_sentinel_question"):
                cursor.execute(
                    "INSERT INTO campus_community.question(id,training_id,course_id,lesson_id,user_id,title,content,"
                    "answer_count,comment_count,like_count,status,create_time,update_time) "
                    "VALUES(%s,%s,%s,%s,%s,'benchmark_sentinel_question','Isolated benchmark question',1,0,0,1,NOW(),NOW())",
                    (QUESTION_ID, TRAINING_ID, COURSE_ID, LESSON_ID, USER_ID),
                )
            if not ensure_row(cursor, "campus_community", "answer", ANSWER_ID, "content", "Isolated benchmark answer"):
                cursor.execute(
                    "INSERT INTO campus_community.answer(id,question_id,user_id,content,comment_count,like_count,status,"
                    "create_time,update_time) VALUES(%s,%s,%s,'Isolated benchmark answer',0,0,1,NOW(),NOW())",
                    (ANSWER_ID, QUESTION_ID, USER_ID),
                )
            cursor.execute(
                "SELECT enrolled_count FROM campus_training.training WHERE id=%s", (TRAINING_ID,)
            )
            recorded_count = cursor.fetchone()[0]
            cursor.execute(
                "SELECT COUNT(*) FROM campus_enrollment.enrollment WHERE training_id=%s AND status=1",
                (TRAINING_ID,),
            )
            active_count = cursor.fetchone()[0]
            if recorded_count != active_count:
                raise RuntimeError("Benchmark training enrollment count is inconsistent")
        connection.commit()
        print(
            f"username={USERNAME} userId={USER_ID} trainingId={TRAINING_ID} "
            f"lessonId={LESSON_ID} questionId={QUESTION_ID} answerId={ANSWER_ID} "
            f"activeEnrollments={active_count}"
        )
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


if __name__ == "__main__":
    main()
