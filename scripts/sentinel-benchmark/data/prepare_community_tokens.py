"""Prepare community benchmark credentials and obtain JWTs through the real login API.

A fresh random password is generated in memory and only used to update the
dedicated community benchmark users; it is never written to disk or Git. The
resulting token CSV stays under the Git-ignored results directory.
"""

import argparse
import csv
import os
import secrets
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import bcrypt
import pymysql
import requests

BASE_URL = os.environ.get("BENCHMARK_BASE_URL", "http://192.168.10.102")
ROOT = Path(__file__).resolve().parents[1] / "results" / "tokens"


def update_passwords(start_id, count, password):
    mysql_password = os.environ.get("MYSQL_PASSWORD")
    if not mysql_password:
        raise SystemExit("Set MYSQL_PASSWORD for MySQL access")
    password_hash = bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt(rounds=10)).decode("utf-8")
    connection = pymysql.connect(
        host=os.environ.get("MYSQL_HOST", "192.168.10.102"),
        port=int(os.environ.get("MYSQL_PORT", "3306")),
        user=os.environ.get("MYSQL_USERNAME", "root"),
        password=mysql_password,
        charset="utf8mb4",
        autocommit=False,
    )
    try:
        with connection.cursor() as cursor:
            cursor.executemany(
                "UPDATE campus_user.sys_user SET password_hash=%s WHERE id=%s AND username LIKE 'benchmark_comm_%%'",
                [(password_hash, user_id) for user_id in range(start_id, start_id + count)],
            )
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def login(username, password):
    last_error = None
    for attempt in range(10):
        try:
            response = requests.post(
                f"{BASE_URL}/auth/login",
                json={"username": username, "password": password},
                timeout=30,
            )
            payload = response.json()
            token = (payload.get("data") or {}).get("token")
            if response.status_code == 200 and payload.get("code") == 200 and token:
                return username, token
            last_error = f"{response.status_code} {payload}"
        except Exception as exc:  # noqa: BLE001 - retried below
            last_error = repr(exc)
        time.sleep(1)
    raise RuntimeError(f"login failed for {username}: {last_error}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--start-id", type=int, default=1_992_001)
    parser.add_argument("--count", type=int, default=6_000)
    parser.add_argument("--workers", type=int, default=16)
    parser.add_argument("--output", default="community-tokens.csv")
    args = parser.parse_args()

    password = secrets.token_urlsafe(18)
    update_passwords(args.start_id, args.count, password)
    print(f"updated {args.count} benchmark password hashes", flush=True)

    usernames = [f"benchmark_comm_{index:05d}" for index in range(1, args.count + 1)]
    tokens = []
    started = time.time()
    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        futures = [executor.submit(login, username, password) for username in usernames]
        for completed, future in enumerate(as_completed(futures), 1):
            tokens.append(future.result())
            if completed % 250 == 0:
                elapsed = time.time() - started
                print(f"{completed}/{len(futures)} in {elapsed:.0f}s", flush=True)
    tokens.sort(key=lambda item: item[0])
    ROOT.mkdir(parents=True, exist_ok=True)
    output = ROOT / args.output
    with output.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["username", "token"])
        writer.writerows(tokens)
    print(f"wrote {len(tokens)} tokens to {output} in {time.time() - started:.0f}s", flush=True)


if __name__ == "__main__":
    main()
