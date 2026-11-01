"""Read Redis and MySQL like state for a dedicated benchmark target over SSH.

Passwords are read from VM_PASSWORD, REDIS_PASSWORD and MYSQL_PASSWORD; they
are never written to disk. Output is JSON.
"""

import argparse
import json
import os
import shlex
import time

import paramiko

REDIS_PREFIX = "campustrain:community:like"


def run(client, command, timeout=30):
    _, stdout, stderr = client.exec_command(command, timeout=timeout)
    return stdout.read().decode().strip(), stderr.read().decode().strip()


def redis_value(client, redis_password, *args):
    quoted = " ".join(shlex.quote(arg) for arg in args)
    output, _ = run(
        client,
        "docker exec campustrain-redis-1 redis-cli -a "
        + shlex.quote(redis_password)
        + " --no-auth-warning "
        + quoted,
    )
    return output


def mysql_value(client, mysql_username, mysql_password, sql):
    output, _ = run(
        client,
        "docker exec campustrain-mysql-1 mysql --user="
        + shlex.quote(mysql_username)
        + " --password="
        + shlex.quote(mysql_password)
        + " -N -e "
        + shlex.quote(sql),
    )
    return output


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--target-type", default="QUESTION")
    parser.add_argument("--target-id", type=int, required=True)
    parser.add_argument("--wait-dirty-zero", type=int, default=0)
    args = parser.parse_args()
    password = os.environ.get("VM_PASSWORD")
    if not password:
        raise SystemExit("Set VM_PASSWORD for SSH access")
    redis_password = os.environ.get("REDIS_PASSWORD")
    if not redis_password:
        raise SystemExit("Set REDIS_PASSWORD for Redis access")
    mysql_password = os.environ.get("MYSQL_PASSWORD")
    if not mysql_password:
        raise SystemExit("Set MYSQL_PASSWORD for MySQL access")
    mysql_username = os.environ.get("MYSQL_USERNAME", "root")

    target_type = args.target_type.upper()
    key_suffix = f":{target_type}:{args.target_id}"
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(
        os.environ.get("VM_HOST", "192.168.10.102"),
        port=int(os.environ.get("VM_PORT", "22")),
        username=os.environ.get("VM_USER", "root"),
        password=password,
        timeout=20,
    )
    deadline = time.time() + args.wait_dirty_zero
    dirty = None
    try:
        while True:
            dirty = int(
                redis_value(
                    client, redis_password, "SCARD", f"{REDIS_PREFIX}:dirty"
                )
                or 0
            )
            if dirty == 0 or time.time() >= deadline:
                break
            time.sleep(2)
        count = redis_value(
            client, redis_password, "GET", f"{REDIS_PREFIX}:count{key_suffix}"
        )
        users = redis_value(
            client, redis_password, "SCARD", f"{REDIS_PREFIX}:users{key_suffix}"
        )
        revision = redis_value(
            client, redis_password, "GET", f"{REDIS_PREFIX}:revision{key_suffix}"
        )
        initialized = redis_value(
            client, redis_password, "EXISTS", f"{REDIS_PREFIX}:init{key_suffix}"
        )
        mysql_count = mysql_value(
            client,
            mysql_username,
            mysql_password,
            f"SELECT like_count FROM campus_community.{'question' if target_type == 'QUESTION' else 'answer'} "
            f"WHERE id={args.target_id}",
        )
        active = mysql_value(
            client,
            mysql_username,
            mysql_password,
            "SELECT COUNT(*) FROM campus_community.like_relation "
            f"WHERE target_type='{target_type}' AND target_id={args.target_id} AND status=1",
        )
        inactive = mysql_value(
            client,
            mysql_username,
            mysql_password,
            "SELECT COUNT(*) FROM campus_community.like_relation "
            f"WHERE target_type='{target_type}' AND target_id={args.target_id} AND status=0",
        )
        result = {
            "targetType": target_type,
            "targetId": args.target_id,
            "redisCount": count,
            "redisUsers": users,
            "redisRevision": revision,
            "redisInitialized": initialized,
            "dirty": dirty,
            "mysqlLikeCount": mysql_count,
            "mysqlActiveRelations": active,
            "mysqlInactiveRelations": inactive,
        }
        print(json.dumps(result, ensure_ascii=False, indent=2))
    finally:
        client.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
