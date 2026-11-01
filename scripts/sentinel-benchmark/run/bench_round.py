"""Run one JMeter benchmark round and persist a normalized summary.

Token files and JTL output stay under the Git-ignored results directory. No
credential is written here; auth plans read BENCHMARK_USERNAME/PASSWORD from
the process environment.
"""

import argparse
import csv
import json
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESULTS = ROOT / "results"
sys.path.insert(0, str(ROOT))
from summarize import summarize  # noqa: E402


def default_jmeter():
    explicit = os.environ.get("JMETER_HOME")
    if explicit:
        return Path(explicit) / "bin" / "jmeter.bat"
    return Path("D:/apache-jmeter-5.6.3/bin/jmeter.bat")


def slice_tokens(master, offset, count, label):
    source = Path(master)
    with source.open(encoding="utf-8", newline="") as stream:
        rows = list(csv.DictReader(stream))
    selected = rows[offset:offset + count]
    if len(selected) < count:
        raise SystemExit(f"token pool too small: requested offset={offset} count={count}, available={len(rows)}")
    target = RESULTS / "tokens" / f"{label}-{offset}-{count}.csv"
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["username", "token"])
        writer.writeheader()
        writer.writerows(selected)
    return target


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--plan", required=True)
    parser.add_argument("--label", required=True)
    parser.add_argument("--qps", type=int, required=True)
    parser.add_argument("--threads", type=int, required=True)
    parser.add_argument("--duration", type=int, default=60)
    parser.add_argument("--ramp", type=int, default=5)
    parser.add_argument("--lesson-id", type=int, default=199001)
    parser.add_argument("--target-type", default="QUESTION")
    parser.add_argument("--target-id", type=int, default=199000001)
    parser.add_argument("--token-file", default="")
    parser.add_argument("--token-offset", type=int)
    parser.add_argument("--token-count", type=int)
    parser.add_argument("--jmeter", default=str(default_jmeter()))
    args = parser.parse_args()

    plan = ROOT / "jmeter" / args.plan
    if not plan.exists():
        raise SystemExit(f"plan missing: {plan}")
    timestamp = time.strftime("%Y%m%d-%H%M%S")
    output = RESULTS / f"{args.label}-{timestamp}"
    output.mkdir(parents=True, exist_ok=True)
    jtl = output / "samples.jtl"

    token_file = args.token_file
    if args.token_offset is not None:
        if args.token_count is None:
            raise SystemExit("--token-count is required with --token-offset")
        token_file = str(slice_tokens(args.token_file, args.token_offset, args.token_count, args.label))

    command = [
        "cmd", "/c", args.jmeter, "-n", "-t", str(plan), "-l", str(jtl),
        "-JBASE_URL=http://192.168.10.102",
        f"-JTARGET_QPS={args.qps}", f"-JTHREADS={args.threads}",
        f"-JDURATION={args.duration}", f"-JRAMP_UP={args.ramp}",
        f"-JLESSON_ID={args.lesson_id}", f"-JTARGET_TYPE={args.target_type}",
        f"-JTARGET_ID={args.target_id}",
        "-Jjmeter.save.saveservice.output_format=csv",
    ]
    if token_file:
        command.append(f"-JTOKEN_FILE={Path(token_file).as_posix()}")

    started = int(time.time())
    completed = subprocess.run(command, cwd=ROOT, check=False)
    ended = int(time.time())
    if completed.returncode != 0:
        raise SystemExit(f"JMeter failed with exit {completed.returncode}; see {output}")

    summary = summarize(jtl)
    topology = {
        "resource": args.label, "plan": args.plan, "baseUrl": "http://192.168.10.102",
        "targetQps": args.qps, "threads": args.threads, "durationSeconds": args.duration,
        "rampUpSeconds": args.ramp, "lessonId": args.lesson_id,
        "targetType": args.target_type, "targetId": args.target_id,
        "access": "nginx-gateway", "startEpoch": started, "endEpoch": ended,
        "tokenFile": Path(token_file).name if token_file else "",
    }
    (output / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "topology.json").write_text(json.dumps(topology, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    compact = {
        "output": str(output), "label": args.label, "targetQps": args.qps,
        "threads": args.threads, "startEpoch": started, "endEpoch": ended, **summary,
    }
    print(json.dumps(compact, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
