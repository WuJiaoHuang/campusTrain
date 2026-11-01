"""Create isolated benchmark users for Stage B/C/D without touching production data.

The benchmark password is supplied through BENCHMARK_PASSWORD and is never
written to this repository. All created users share the dedicated benchmark
account's BCrypt hash so login-based token preparation stays reproducible.
"""

import os

import pymysql

TRAINING_ID = 199_001
LESSON_ID = 199_001
LEARN_USER_IDS = range(1_990_100, 1_991_100)
COMM_USER_IDS = range(1_992_001, 2_003_001)
BENCHMARK_USER_ID = 1_990_001


def insert_users(cursor, password_hash, user_ids, prefix):
    rows = [
        (user_id, f"{prefix}{index:05d}", password_hash, "Sentinel benchmark user", "STUDENT", 1)
        for index, user_id in enumerate(user_ids, 1)
    ]
    cursor.executemany(
        "INSERT IGNORE INTO campus_user.sys_user"
        "(id,username,password_hash,nickname,role,status) VALUES(%s,%s,%s,%s,%s,%s)",
        rows,
    )
    return len(rows)


def main():
    if not os.environ.get("BENCHMARK_PASSWORD"):
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
            cursor.execute(
                "SELECT password_hash FROM campus_user.sys_user WHERE id=%s",
                (BENCHMARK_USER_ID,),
            )
            row = cursor.fetchone()
            if not row:
                raise RuntimeError("Stage A benchmark user 1990001 is missing")
            password_hash = row[0]

            insert_users(cursor, password_hash, LEARN_USER_IDS, "benchmark_learn_")
            insert_users(cursor, password_hash, COMM_USER_IDS, "benchmark_comm_")

            cursor.executemany(
                "INSERT IGNORE INTO campus_enrollment.enrollment(user_id,training_id,status) VALUES(%s,%s,1)",
                [(user_id, TRAINING_ID) for user_id in LEARN_USER_IDS],
            )
            cursor.execute(
                "SELECT COUNT(*) FROM campus_enrollment.enrollment WHERE training_id=%s AND status=1",
                (TRAINING_ID,),
            )
            active_count = cursor.fetchone()[0]
            cursor.execute(
                "UPDATE campus_training.training SET capacity=GREATEST(capacity,%s), enrolled_count=%s WHERE id=%s",
                (active_count + 200, active_count, TRAINING_ID),
            )

            cursor.execute(
                "SELECT COUNT(*) FROM campus_user.sys_user WHERE id BETWEEN %s AND %s",
                (min(LEARN_USER_IDS), max(COMM_USER_IDS)),
            )
            created_users = cursor.fetchone()[0]
            cursor.execute(
                "SELECT COUNT(*) FROM campus_enrollment.enrollment WHERE training_id=%s AND status=1",
                (TRAINING_ID,),
            )
            enrolled = cursor.fetchone()[0]
        connection.commit()
        print(
            f"benchmarkUsers={created_users} learningUsers={len(LEARN_USER_IDS)} "
            f"communityUsers={len(COMM_USER_IDS)} activeEnrollments={enrolled} trainingId={TRAINING_ID} "
            f"lessonId={LESSON_ID}"
        )
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


if __name__ == "__main__":
    main()
