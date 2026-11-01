"""Obtain benchmark JWTs through the real login API.

Tokens are written only under the Git-ignored results directory. The password
is read from BENCHMARK_PASSWORD and never persisted.
"""

import csv
import os
import sys
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import requests

BASE_URL = os.environ.get("BENCHMARK_BASE_URL", "http://192.168.10.102")
PASSWORD = os.environ.get("BENCHMARK_PASSWORD")
ROOT = Path(__file__).resolve().parents[1] / "results" / "tokens"
LEARNING_IDS = range(1_990_100, 1_991_100)
COMMUNITY_IDS = range(1_992_001, 2_003_001)


def login(username):
    last_error = None
    for _ in range(3):
        try:
            response = requests.post(
                f"{BASE_URL}/auth/login",
                json={"username": username, "password": PASSWORD},
                timeout=30,
            )
            payload = response.json()
            token = (payload.get("data") or {}).get("token")
            if response.status_code == 200 and payload.get("code") == 200 and token:
                return username, token
            last_error = f"{response.status_code} {payload}"
        except Exception as exc:  # noqa: BLE001 - retried below
            last_error = repr(exc)
    raise RuntimeError(f"login failed for {username}: {last_error}")


def build(prefix, user_ids, filename):
    usernames = [f"{prefix}{index:05d}" for index, _ in enumerate(user_ids, 1)]
    tokens = []
    with ThreadPoolExecutor(max_workers=16) as executor:
        futures = [executor.submit(login, username) for username in usernames]
        for completed, future in enumerate(as_completed(futures), 1):
            tokens.append(future.result())
            if completed % 500 == 0:
                print(f"{filename}: {completed}/{len(futures)}", flush=True)
    tokens.sort(key=lambda item: item[0])
    output = ROOT / filename
    with output.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["username", "token"])
        writer.writerows(tokens)
    print(f"wrote {len(tokens)} tokens to {output}", flush=True)


def main():
    if not PASSWORD:
        raise SystemExit("Set BENCHMARK_PASSWORD in the environment")
    ROOT.mkdir(parents=True, exist_ok=True)
    build("benchmark_learn_", LEARNING_IDS, "learning-tokens.csv")
    build("benchmark_comm_", COMMUNITY_IDS, "community-tokens.csv")
    return 0


if __name__ == "__main__":
    sys.exit(main())
