#!/usr/bin/env python3
"""CI proof: the resilient demo passes an invariant and the vulnerable demo fails it."""
from __future__ import annotations

import os
import socket
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PORT = 8080
SCENARIO = ROOT / "examples" / "payment" / "state-invariant.yml"


def wait_for_server() -> None:
    deadline = time.time() + 10
    while time.time() < deadline:
        try:
            with socket.create_connection(("localhost", PORT), timeout=0.5):
                return
        except OSError:
            time.sleep(0.1)
    raise RuntimeError("demo server did not start")


def cli_command() -> list[str]:
    binary = ROOT / "build" / "install" / "deliverydrill" / "bin" / "deliverydrill"
    if os.name == "nt": binary = binary.with_suffix(".bat")
    return [str(binary), "run", str(SCENARIO), "--seed", "42"]


def run_mode(mode: str, expected: int) -> None:
    environment = os.environ.copy()
    environment["WEBHOOK_SECRET"] = "dev-secret"
    server = subprocess.Popen([sys.executable, str(ROOT / "examples" / "demo_server.py"), "--mode", mode, "--port", str(PORT)], cwd=ROOT, env=environment, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    try:
        wait_for_server()
        completed = subprocess.run(cli_command(), cwd=ROOT, env=environment, capture_output=True, text=True, timeout=30)
        output = completed.stdout + completed.stderr
        if (expected == 0 and completed.returncode != 0) or (expected != 0 and completed.returncode == 0):
            raise AssertionError(f"{mode} demo returned {completed.returncode}, expected {expected}\n{output}")
        if mode == "vulnerable" and "state regressed" not in output:
            raise AssertionError("vulnerable demo did not report the expected regression\n" + output)
        print(f"{mode}: expected result observed")
    finally:
        server.terminate()
        try: server.wait(timeout=5)
        except subprocess.TimeoutExpired: server.kill()


if __name__ == "__main__":
    run_mode("resilient", 0)
    run_mode("vulnerable", 1)
    print("demo smoke checks passed")

