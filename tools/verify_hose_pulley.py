#!/usr/bin/env python3
"""Verify transformed Create/Sable hoses and a real save/restart in a disposable server."""
from pathlib import Path
import json
import re
import secrets
import socket
import struct
import subprocess
import time

root = Path(__file__).resolve().parents[1]
instance = root / "run/smoke/hose"
report = root / "build/reports/hose"
instance.mkdir(parents=True, exist_ok=True)
report.mkdir(parents=True, exist_ok=True)
result_path = report / "sources.json"
result_path.unlink(missing_ok=True)
with socket.socket() as available:
    available.bind(("127.0.0.1", 0))
    port = available.getsockname()[1]
password = secrets.token_hex(16)
world = "world-" + secrets.token_hex(8)
(instance / "eula.txt").write_text("eula=true\n")
(instance / "server.properties").write_text(
    "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n"
    "level-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\n"
    "simulation-distance=2\nspawn-protection=0\n"
    f"level-name={world}\n"
    f"enable-rcon=true\nrcon.port={port}\nrcon.password={password}\n"
    "broadcast-rcon-to-ops=false\n"
)
args = ["./gradlew", "runSmokeServer", "-PcsoRuntime=all", "-PhoseTests",
        f"-PperipheralTestDir={instance}", "--offline", "--console=plain"]
connection = None
process = None
checks = []


def exact(size):
    data = b""
    while len(data) < size:
        part = connection.recv(size - len(data))
        if not part:
            raise RuntimeError("RCON closed")
        data += part
    return data


def request(kind, body):
    packet = struct.pack("<ii", 42, kind) + body.encode() + b"\0\0"
    connection.sendall(struct.pack("<i", len(packet)) + packet)
    size = struct.unpack("<i", exact(4))[0]
    if not 10 <= size <= 1024 * 1024:
        raise RuntimeError(f"Invalid RCON response size {size}")
    packet = exact(size)
    assert struct.unpack("<i", packet[:4])[0] == 42, "RCON rejected request"
    return packet[8:-2].decode()


def wait_started(log_path):
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        content = log_path.read_text(errors="replace")
        if process.poll() is not None:
            raise RuntimeError(f"Server exited: {log_path}\n{content[-12000:]}")
        if "Done (" in content:
            return
        time.sleep(.5)
    raise RuntimeError(f"Server startup timed out: {log_path}")


def assert_log(log_path):
    content = log_path.read_text(errors="replace")
    for failure in ("Hose verification failed", "Mixin apply failed", "Mixin transformation of", "A SECOND ENTITY FOR"):
        assert failure not in content, f"{failure}: {log_path}"


def wait_mode(mode, heads):
    deadline = time.monotonic() + 20
    response = ""
    while time.monotonic() < deadline:
        response = request(2, "teutonlib_hose_test mode-status").strip()
        assert not response.startswith("FAIL"), response
        if response == f"PASS hose mode={mode} heads={heads}":
            return response
        time.sleep(.25)
    raise AssertionError(f"Config mode/head state did not settle: expected {mode}/{heads}, got {response}")


def reload_mode_file(mode, heads):
    # Mutate the actual existing NeoForge world SERVER config, so this exercises its
    # file watcher/cache invalidation rather than the verification command's Value.set.
    config = instance / world / "serverconfig/teutonlib-server.toml"
    assert config.is_file(), f"World server config was not created: {config}"
    content = config.read_text()
    updated, count = re.subn(r'(?m)^([ \t]*entityHeadMode[ \t]*=[ \t]*)"[A-Z_]+"[ \t]*$',
                            lambda match: match[1] + f'"{mode}"', content)
    assert count == 1, f"Expected one entityHeadMode in {config}"
    assert updated != content, f"File reload must change the persisted value to {mode}"
    config.write_text(updated)
    response = wait_mode(mode, heads)
    print(f"PASS hose config file reload: {mode}, heads={heads}", flush=True)
    checks.append(f"PASS hose config file reload: {mode}, heads={heads}")
    return response


try:
    log_path = report / "sources.log"
    with log_path.open("w") as log:
        process = subprocess.Popen(args, cwd=root, stdout=log, stderr=subprocess.STDOUT)
        wait_started(log_path)
        connection = socket.create_connection(("127.0.0.1", port), timeout=60)
        request(3, password)
        request(2, "forceload add 0 0 63 15")
        time.sleep(1.0)
        default_mode = wait_mode("ALWAYS", 0)
        checks.append("PASS hose default config mode ALWAYS")
        response = request(2, "teutonlib_hose_test run")
        print(response, flush=True)
        assert "PASS hose:" in response, response
        checks.append(response)
        response = request(2, "teutonlib_hose_test checkpoint")
        print(response, flush=True)
        assert "PASS hose checkpoint" in response, response
        checks.append(response)
        wait_mode("ALWAYS", 1)
        reload_mode_file("NEVER", 0)
        reload_mode_file("SABLE_ONLY", 0)
        reload_mode_file("ALWAYS", 1)
        # Live mode changes recreate loaded heads. Persist the final ALWAYS identity
        # for the existing save/restart assertions below.
        response = request(2, "teutonlib_hose_test checkpoint")
        assert "PASS hose checkpoint" in response, response
        checks.append(response)
        request(2, "save-all flush")
        request(2, "stop")
        process.wait(timeout=180)
        assert process.returncode == 0, process.returncode
        connection.close()
        connection = None
    assert_log(log_path)
    restart_path = report / "sources-restart.log"
    with restart_path.open("w") as log:
        process = subprocess.Popen(args, cwd=root, stdout=log, stderr=subprocess.STDOUT)
        wait_started(restart_path)
        connection = socket.create_connection(("127.0.0.1", port), timeout=60)
        request(3, password)
        wait_mode("ALWAYS", 1)
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            response = request(2, "teutonlib_hose_test loaded")
            if "PASS hose restart" in response:
                print(response, flush=True)
                checks.append(response)
                break
            assert "RUNNING hose restart" in response, response
            time.sleep(.25)
        else:
            raise AssertionError("Saved head did not restore: " + response)
        request(2, "teutonlib_hose_test cleanup")
        request(2, "stop")
        process.wait(timeout=180)
        assert process.returncode == 0, process.returncode
    assert_log(restart_path)
    result_path.write_text(json.dumps({"passed": True, "checks": checks,
        "world": str(instance / world), "log": str(log_path), "restartLog": str(restart_path)}, indent=2) + "\n")
finally:
    if process is not None and process.poll() is None and connection is not None:
        try:
            request(2, "stop")
            process.wait(timeout=30)
        except (OSError, RuntimeError, subprocess.TimeoutExpired):
            pass
    if connection is not None:
        connection.close()
    if process is not None and process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)
