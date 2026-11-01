#!/usr/bin/env python3
"""Verify CTF/IP safety in a disposable local server with original optional binaries.

python3 tools/verify_train_portals.py
Use --packaged to exercise the release JAR instead of the development source output.
Needs the original CTF 0.1.47, IP Aeronautics 2.6.4 and Cloth Config 15.0.140
JARs in dependencies/compile, plus the normal all-profile runtime dependencies.
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
from verify_train_portal_client import verify_aether_release, verify_native_aether_evidence

root = Path(__file__).resolve().parents[1]
packaged = "--packaged" in sys.argv[1:]
restart_only = "--restart-only" in sys.argv[1:]
aether = "--aether" in sys.argv[1:]
destination = "aether:the_aether" if aether else "minecraft:the_nether"
aether_release = verify_aether_release() if aether else None
label = "packaged" if packaged else "sources"
sable_version = "2.0.6"
label += "-" + sable_version
if aether:
    label += "-aether"
instance = root / "run/smoke/train-portals" / label
report = root / "build/reports/train-portals"
instance.mkdir(parents=True, exist_ok=True)
report.mkdir(parents=True, exist_ok=True)
result_path = report / f"{label}.json"
result_path.unlink(missing_ok=True)
with socket.socket() as available:
    available.bind(("127.0.0.1", 0))
    port = available.getsockname()[1]
password = secrets.token_hex(16)
if restart_only:
    world = next(line.split("=", 1)[1] for line in (instance / "server.properties").read_text().splitlines()
                 if line.startswith("level-name="))
else:
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
args = ["./gradlew", "runSmokeServer", "-PcsoRuntime=all", "-PtrainPortalTests",
        f"-PtrainPortalSableVersion={sable_version}",
        f"-PperipheralTestDir={instance}", "--offline", "--console=plain"]
if aether:
    args.append("-PtrainPortalAether")
if packaged:
    init = report / "packaged.gradle"
    init.write_text("""allprojects {
    afterEvaluate {
        def packagedRun = sourceSets.create('trainPortalPackaged')
        packagedRun.runtimeClasspath = sourceSets.main.runtimeClasspath - sourceSets.main.output + files(tasks.jar.archiveFile)
        neoForge.runs.getByName('smokeServer').with {
            sourceSet = packagedRun
            loadedMods.set([neoForge.mods.getByName('teutonlib_train_portal_test')])
        }
        tasks.named('runSmokeServer') { dependsOn tasks.jar }
    }
}
""")
    args.extend(["-I", str(init)])

log_path = report / f"{label}.log"
connection = None
response = None
checks = []
native_aether = None
with log_path.open("w") as log:
    process = subprocess.Popen(args, cwd=root, stdout=log, stderr=subprocess.STDOUT)

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
        packet = exact(struct.unpack("<i", exact(4))[0])
        assert struct.unpack("<i", packet[:4])[0] == 42, "RCON rejected request"
        return packet[8:-2].decode()

    try:
        deadline = time.monotonic() + 240
        while time.monotonic() < deadline:
            content = log_path.read_text(errors="replace")
            if process.poll() is not None:
                raise RuntimeError(f"Server exited: {log_path}\n{content[-10000:]}")
            if "Done (" in content:
                break
            time.sleep(0.5)
        else:
            raise RuntimeError(f"Server startup timed out: {log_path}")
        connection = socket.create_connection(("127.0.0.1", port), timeout=60)
        request(3, password)
        if restart_only:
            deadline = time.monotonic() + 35
            previous = None
            while time.monotonic() < deadline:
                status = request(2, "teutonlib_train_traversal loaded")
                if status != previous:
                    print(status, flush=True)
                    previous = status
                if "PASS restart" in status:
                    request(2, "stop")
                    process.wait(timeout=180)
                    raise SystemExit(0)
                assert "RUNNING" in status, status
                time.sleep(0.25)
            raise AssertionError("Saved train did not relink: " + status)
        if aether:
            prepared = request(2, "execute in minecraft:overworld run teutonlib_train_visual aether-prepare")
            assert prepared.startswith(("PREPARING", "READY")), prepared
            deadline = time.monotonic() + 90
            while time.monotonic() < deadline:
                state = request(2, "teutonlib_train_visual aether-status")
                assert not state.startswith("FAIL"), state
                if state.startswith("READY"):
                    native_aether = json.loads(state[state.index("{"):])
                    verify_native_aether_evidence(native_aether)
                    break
                time.sleep(0.25)
            assert native_aether is not None, "Native Aether generation timed out: " + state
            (report / f"{label}-native-portals.json").write_text(json.dumps(native_aether, indent=2) + "\n")
            print("PASS native Aether portal generation and reciprocal rail link", flush=True)
        decouple = request(2, "teutonlib_train_traversal decouple")
        assert "RUNNING" in decouple, decouple
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            decouple = request(2, "teutonlib_train_traversal status")
            if "PASS real Railways splitTrain" in decouple:
                break
            assert "RUNNING" in decouple, decouple
            time.sleep(0.25)
        print(decouple, flush=True)
        assert "PASS real Railways splitTrain" in decouple, decouple
        checks.append(decouple)
        response = request(2, "teutonlib_train_portal_test")
        print(response, flush=True)
        assert "Train portals PASS" in response, response
        checks.append(response)
        # Freshly forced chunks become entity-accessible on a later server tick.
        # Warm both regions before the synchronous real-entity transfer fixture.
        request(2, "execute in minecraft:overworld run forceload add 16 48 127 95")
        request(2, f"execute in {destination} run forceload add 16 48 127 95")
        time.sleep(1.0)
        riders = request(2, "teutonlib_train_riders_test")
        print(riders, flush=True)
        assert "Train riders PASS" in riders, riders
        checks.append(riders)
        for mode in ("cross", "same", "multi", "checkpoint"):
            if aether and mode != "same":
                # Synthetic transfer probes invalidate CTF's chunk caches. IP's
                # saved portal entities must be accessible again before starting
                # a native traversal; require the original cluster, never regenerate it.
                deadline = time.monotonic() + 15
                while time.monotonic() < deadline:
                    state = request(2, "teutonlib_train_visual aether-status")
                    assert not state.startswith("FAIL"), state
                    if state.startswith("READY"):
                        ready_evidence = json.loads(state[state.index("{"):])
                        verify_native_aether_evidence(ready_evidence)
                        assert {portal["uuid"] for portal in ready_evidence["portals"]} == {
                            portal["uuid"] for portal in native_aether["portals"]}, ready_evidence
                        break
                    time.sleep(0.25)
                else:
                    raise AssertionError("Original native Aether cluster was not accessible: " + state)
            started = request(2, "teutonlib_train_traversal " + mode)
            assert "RUNNING" in started, started
            deadline = time.monotonic() + 45
            while time.monotonic() < deadline:
                status = request(2, "teutonlib_train_traversal status")
                if "FAIL" in status:
                    raise AssertionError(status)
                if "PASS" in status:
                    print(status, flush=True)
                    checks.append(status)
                    break
                time.sleep(0.25)
            else:
                raise AssertionError("Traversal timed out: " + status)
        request(2, "stop")
        process.wait(timeout=180)
        assert process.returncode == 0, process.returncode
        content = log_path.read_text(errors="replace")
        assert "Train portal guard enabled" in content
        for failure in ("Train portals FAIL", "Mixin apply failed", "Mixin transformation of", "[CTF FAILSAFE]"):
            assert failure not in content, failure
        connection.close()
        connection = None
        restart_path = report / f"{label}-restart.log"
        with restart_path.open("w") as restart_log:
            process = subprocess.Popen(args, cwd=root, stdout=restart_log, stderr=subprocess.STDOUT)
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline:
                restart_content = restart_path.read_text(errors="replace")
                if process.poll() is not None:
                    raise RuntimeError(f"Restart exited: {restart_path}\n{restart_content[-10000:]}")
                if "Done (" in restart_content:
                    break
                time.sleep(0.5)
            else:
                raise RuntimeError("Restart timed out")
            connection = socket.create_connection(("127.0.0.1", port), timeout=60)
            request(3, password)
            deadline = time.monotonic() + 35
            while time.monotonic() < deadline:
                status = request(2, "teutonlib_train_traversal loaded")
                if "PASS restart" in status:
                    checks.append(status)
                    print(status, flush=True)
                    break
                assert "RUNNING" in status, status
                time.sleep(0.25)
            else:
                raise AssertionError("Saved train did not relink: " + status)
            request(2, "stop")
            process.wait(timeout=180)
            assert process.returncode == 0, process.returncode
            restart_content = restart_path.read_text(errors="replace")
            for failure in ("Mixin apply failed", "Mixin transformation of", "[CTF FAILSAFE]", "A SECOND ENTITY FOR"):
                assert failure not in restart_content, failure
        result_path.write_text(json.dumps({"passed": True, "profile": label,
            "response": response, "checks": checks, "world": str(instance / world), "log": str(log_path),
            "restartLog": str(restart_path), "destination": destination, "aetherPortalEvidence": native_aether,
            "aetherRelease": aether_release}, indent=2) + "\n")
    except BaseException as failure:
        if isinstance(failure, SystemExit) and failure.code == 0:
            raise
        result_path.write_text(json.dumps({"passed": False, "profile": label,
            "response": response, "checks": checks, "world": str(instance / world), "log": str(log_path),
            "destination": destination, "aetherPortalEvidence": native_aether,
            "aetherRelease": aether_release, "failure": traceback.format_exc()}, indent=2) + "\n")
        raise
    finally:
        if process.poll() is None and connection:
            try:
                request(2, "stop")
                process.wait(timeout=30)
            except (OSError, RuntimeError, subprocess.TimeoutExpired):
                pass
        if connection:
            connection.close()
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
