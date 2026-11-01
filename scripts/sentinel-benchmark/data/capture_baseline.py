"""Read the benchmark rows from each development database; no mutations."""

import json
import os
from pathlib import Path

import pymysql


def main():
    connection = pymysql.connect(
        host=os.environ.get("MYSQL_HOST", "192.168.10.102"),
        port=int(os.environ.get("MYSQL_PORT", "3306")),
        user=os.environ.get("MYSQL_USERNAME", "root"),
        password=os.environ.get("MYSQL_PASSWORD", "123"),
        charset="utf8mb4",
        cursorclass=pymysql.cursors.DictCursor,
    )
    try:
        queries = [query.strip() for query in Path(__file__).with_name("baseline.sql").read_text(encoding="utf-8").split(";") if query.strip()]
        result = {}
        with connection.cursor() as cursor:
            for index, query in enumerate(queries, 1):
                cursor.execute(query)
                result[f"check{index}"] = cursor.fetchall()
        print(json.dumps(result, ensure_ascii=False, default=str, indent=2))
    finally:
        connection.close()


if __name__ == "__main__":
    main()
