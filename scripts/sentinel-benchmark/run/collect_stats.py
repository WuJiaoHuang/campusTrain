"""Collect VM CPU/memory and Docker container CPU samples over SSH.

The SSH password is read from VM_PASSWORD and never written. Output is a CSV
under the Git-ignored results directory, one row per sampling interval.
"""

import argparse
import csv
import os
import re
import time
from pathlib import Path

import paramiko

RESULTS = Path(__file__).resolve().parents[1] / "results"
CPU_LINE = re.compile(r"^cpu\s+(.+)$", re.MULTILINE)
CONTAINERS = (
    "campustrain-nginx-1",
    "campustrain-campus-gateway-1",
    "campustrain-auth-service-1",
    "campustrain-learning-service-1",
    "campustrain-community-service-1",
    "campustrain-training-service-1",
    "campustrain-enrollment-service-1",
    "campustrain-growth-service-1",
    "campustrain-user-service-1",
    "campustrain-mysql-1",
    "campustrain-redis-1",
    "campustrain-nacos-1",
)


def read_cpu(client):
    _, stdout, _ = client.exec_command("cat /proc/stat | head -1")
    fields = [int(value) for value in stdout.read().decode().split()[1:]]
    idle = fields[3] + (fields[4] if len(fields) > 4 else 0)
    return sum(fields), idle


def read_mem(client):
    _, stdout, _ = client.exec_command("cat /proc/meminfo | head -3; cat /proc/loadavg")
    lines = stdout.read().decode().splitlines()
    values = {}
    for line in lines[:3]:
        name, rest = line.split(":", 1)
        values[name] = int(rest.strip().split()[0])
    load = lines[3].split()[0] if len(lines) > 3 else ""
    return values, load


def read_docker(client):
    names = " ".join(CONTAINERS)
    command = (
        "docker stats --no-stream --format '{{.Name}},{{.CPUPerc}},{{.MemUsage}}' "
        + names
    )
    _, stdout, _ = client.exec_command(command, timeout=30)
    result = {}
    for line in stdout.read().decode().splitlines():
        parts = line.strip().split(",")
        if len(parts) < 3:
            continue
        result[parts[0]] = (parts[1].rstrip("%"), parts[2])
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--label", required=True)
    parser.add_argument("--seconds", type=int, required=True)
    parser.add_argument("--interval", type=float, default=5.0)
    parser.add_argument(
        "--containers",
        action="store_true",
        help="also sample docker stats; expensive on the 2-core benchmark VM",
    )
    args = parser.parse_args()
    password = os.environ.get("VM_PASSWORD")
    if not password:
        raise SystemExit("Set VM_PASSWORD for SSH access")

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(
        os.environ.get("VM_HOST", "192.168.10.102"),
        port=int(os.environ.get("VM_PORT", "22")),
        username=os.environ.get("VM_USER", "root"),
        password=password,
        timeout=20,
    )
    RESULTS.mkdir(parents=True, exist_ok=True)
    output = RESULTS / f"stats-{args.label}-{time.strftime('%Y%m%d-%H%M%S')}.csv"
    columns = ["epoch", "machineCpuPercent", "load1", "memUsedMiB", "memTotalMiB"]
    if args.containers:
        for name in CONTAINERS:
            columns.extend([f"{name}.cpuPercent", f"{name}.mem"])
    previous = read_cpu(client)
    started = time.time()
    try:
        with output.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=columns)
            writer.writeheader()
            while time.time() - started < args.seconds:
                interval_start = time.time()
                current = read_cpu(client)
                total_delta = current[0] - previous[0]
                idle_delta = current[1] - previous[1]
                previous = current
                mem, load = read_mem(client)
                containers = read_docker(client) if args.containers else {}
                row = {
                    "epoch": int(interval_start),
                    "machineCpuPercent": round(100 * (1 - idle_delta / total_delta), 2) if total_delta else "",
                    "load1": load,
                    "memUsedMiB": round((mem["MemTotal"] - mem["MemAvailable"]) / 1024),
                    "memTotalMiB": round(mem["MemTotal"] / 1024),
                }
                if args.containers:
                    for name in CONTAINERS:
                        cpu, usage = containers.get(name, ("", ""))
                        row[f"{name}.cpuPercent"] = cpu
                        row[f"{name}.mem"] = usage
                writer.writerow(row)
                stream.flush()
                elapsed = time.time() - interval_start
                time.sleep(max(0.0, args.interval - elapsed))
    finally:
        client.close()
    print(f"wrote {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
