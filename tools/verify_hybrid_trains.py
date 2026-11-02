#!/usr/bin/env python3
"""Exercise hybrid carriage modes, real Radar machinery, rail portals, rollback and a full restart.

Uses original CTF/IP/Radar jars in dependencies/compile and CBC 5.11.7 / RPL 2.1.2
in dependencies/verification. The localhost-only world is disposable. --packaged tests the release jar.
"""
from pathlib import Path
import json
import secrets
import socket
import struct
import subprocess
import sys
import time
import traceback

ROOT = Path(__file__).resolve().parents[1]
PACKAGED = "--packaged" in sys.argv
REPORT = ROOT / "build/reports/hybrid-trains"
REPORT.mkdir(parents=True, exist_ok=True)
LABEL = "packaged" if PACKAGED else "sources"
INSTANCE = ROOT / "run/smoke/hybrid-trains" / LABEL
INSTANCE.mkdir(parents=True, exist_ok=True)
with socket.socket() as available:
    available.bind(("127.0.0.1", 0))
    port = available.getsockname()[1]
password = secrets.token_hex(16)
world = "world-" + secrets.token_hex(8)
(INSTANCE / "eula.txt").write_text("eula=true\n")
(INSTANCE / "server.properties").write_text(
    "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n"
    "level-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\n"
    f"level-name={world}\nenable-rcon=true\nrcon.port={port}\nrcon.password={password}\n"
    "broadcast-rcon-to-ops=false\nspawn-protection=0\n")
args = ["./gradlew", "runSmokeServer", "-PcsoRuntime=all", "-PtrainPortalTests", "-PhybridTrainTests",
        f"-PperipheralTestDir={INSTANCE}", "--offline", "--console=plain"]
if PACKAGED:
    init = REPORT / "packaged.gradle"
    init.write_text("""allprojects { afterEvaluate {
        def packagedRun = sourceSets.create('hybridTrainPackaged')
        packagedRun.runtimeClasspath = sourceSets.main.runtimeClasspath - sourceSets.main.output + files(tasks.jar.archiveFile)
        neoForge.runs.getByName('smokeServer').with {
            sourceSet = packagedRun
            loadedMods.set([neoForge.mods.getByName('teutonlib_train_portal_test')])
        }
        tasks.named('runSmokeServer') { dependsOn tasks.jar }
    } }
""")
    args += ["-I", str(init)]

checks = []
process = None
connection = None


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
    response = exact(struct.unpack("<i", exact(4))[0])
    assert struct.unpack("<i", response[:4])[0] == 42, "RCON authentication rejected"
    return response[8:-2].decode().strip()


def poll(command, timeout):
    previous = None
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        status = request(2, command)
        if status != previous:
            print(status, flush=True)
            previous = status
        assert status.startswith(("RUNNING", "PASS")), status
        if status.startswith("PASS"):
            checks.append(status)
            return
        time.sleep(.25)
    raise AssertionError("Test timed out: " + str(previous))


try:
    for restart in (False, True):
        log_path = REPORT / f"{LABEL}{'-restart' if restart else ''}.log"
        with log_path.open("w") as log:
            process = subprocess.Popen(args, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 180
            while time.monotonic() < deadline:
                assert process.poll() is None, f"Server exited; see {log_path}"
                if "Done (" in log_path.read_text(errors="replace"):
                    break
                time.sleep(.5)
            else:
                raise AssertionError(f"Server startup timed out; see {log_path}")
            connection = socket.create_connection(("127.0.0.1", port), timeout=60)
            request(3, password)
            if restart:
                poll("teutonlib_station_build_test loaded", 45)
                poll("teutonlib_hybrid_train_test loaded", 45)
            else:
                response = request(2, "teutonlib_station_build_test start")
                assert response.startswith("RUNNING"), response
                poll("teutonlib_station_build_test status", 60)
                response = request(2, "teutonlib_hybrid_train_test start")
                assert response.startswith("RUNNING"), response
                poll("teutonlib_hybrid_train_test status", 60)
            request(2, "stop")
            connection.close()
            connection = None
            process.wait(timeout=180)
            assert process.returncode == 0, f"Server failed; see {log_path}"
            content = log_path.read_text(errors="replace")
            for failure in ("Mixin apply failed", "Mixin transformation of", "[CTF FAILSAFE]", "A SECOND ENTITY FOR"):
                assert failure not in content, failure
    (REPORT / f"{LABEL}.json").write_text(json.dumps({"passed": True, "checks": checks,
        "world": str(INSTANCE / world)}, indent=2) + "\n")
except BaseException:
    (REPORT / f"{LABEL}.json").write_text(json.dumps({"passed": False, "checks": checks,
        "world": str(INSTANCE / world), "failure": traceback.format_exc()}, indent=2) + "\n")
    raise
finally:
    if process and process.poll() is None:
        if connection:
            try:
                request(2, "stop")
                process.wait(timeout=45)
            except (OSError, RuntimeError, subprocess.TimeoutExpired):
                pass
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
    if connection:
        connection.close()
