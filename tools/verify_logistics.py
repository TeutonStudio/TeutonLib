#!/usr/bin/env python3
"""Run the real logistics mixins in a disposable local server and restart its world.

python3 tools/verify_logistics.py PROFILE
Profiles: both, packaged, fluid, factory, none, bare, baseline,
          pipes, pipes-packaged, pipes-baseline, pipes-all, pipes-fluid, pipes-tfmg
Needs the original JARs listed in docs/logistics-compatibility.md in dependencies/compile.
The baseline excludes TeutonLib and is expected to fail with the original descriptor conflict.
"""
from pathlib import Path
import json
import secrets
import socket
import struct
import subprocess
import sys
import time

root = Path(__file__).resolve().parents[1]
profile = sys.argv[1] if len(sys.argv) > 1 else "both"
restart_only = "--restart-only" in sys.argv[2:]
profiles = ("both", "packaged", "fluid", "factory", "none", "bare", "baseline",
            "pipes", "pipes-packaged", "pipes-baseline", "pipes-all", "pipes-fluid", "pipes-tfmg")
if profile not in profiles:
    raise SystemExit("Profile must be one of " + ", ".join(profiles))
pipe_profile = profile.startswith("pipes")
pipe_pair = pipe_profile and profile not in ("pipes-fluid", "pipes-tfmg")
baseline = profile in ("baseline", "pipes-baseline")
packaged = profile in ("packaged", "pipes-packaged", "pipes-all")
has_restart = profile in ("both", "packaged") or (pipe_pair and not baseline)
if restart_only and not has_restart:
    raise SystemExit("--restart-only requires a profile with an existing restart fixture")
instance = root / "run/smoke/logistics" / profile
report = root / "build/reports/logistics"
instance.mkdir(parents=True, exist_ok=True)
report.mkdir(parents=True, exist_ok=True)
(report / f"{profile}.json").unlink(missing_ok=True)
with socket.socket() as available:
    available.bind(("127.0.0.1", 0))
    rcon_port = available.getsockname()[1]
rcon_password = secrets.token_hex(16)
(instance / "eula.txt").write_text("eula=true\n")
(instance / "server.properties").write_text(
    "server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n"
    "level-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\n"
    "simulation-distance=2\nspawn-protection=0\n"
    f"enable-rcon=true\nrcon.port={rcon_port}\nrcon.password={rcon_password}\n"
    "broadcast-rcon-to-ops=false\n"
)
runtime = "both" if profile in ("baseline", "packaged") else "none" if profile == "bare" else profile
if pipe_profile:
    runtime = "both" if profile == "pipes-all" else "none" if profile == "pipes-tfmg" else "fluid"
args = ["./gradlew", "runSmokeServer", f"-PcsoRuntime={'none' if profile == 'bare' else 'create'}",
        f"-PlogisticsRuntime={runtime}",
        f"-PperipheralTestDir={instance}", "--offline", "--console=plain"]
if profile in ("both", "packaged", "baseline", "pipes-all"):
    args.append("-PlogisticsTests")
if pipe_profile:
    args.extend(["-PfluidPropagationTests", f"-PtfmgRuntime={'false' if profile == 'pipes-fluid' else 'true'}"])
if baseline or packaged:
    init = report / f"{profile}.gradle"
    packaged_jar = " + files(tasks.jar.archiveFile)" if packaged else ""
    test_mods = ["teutonlib_fluid_test"] if pipe_profile else ["teutonlib_logistics_test"]
    if profile == "pipes-all":
        test_mods.append("teutonlib_logistics_test")
    init.write_text("""allprojects {
    afterEvaluate {
        def baseline = sourceSets.create('logisticsBaseline')
        baseline.runtimeClasspath = sourceSets.main.runtimeClasspath - sourceSets.main.outputPACKAGE
        neoForge.runs.getByName('smokeServer').with {
            sourceSet = baseline
            loadedMods.set([TEST_MODS])
        }
        tasks.named('runSmokeServer') { dependsOn tasks.jar }
    }
}
""".replace("PACKAGE", packaged_jar).replace("TEST_MODS", ", ".join(
        f"neoForge.mods.getByName('{mod}')" for mod in test_mods)))
    args.extend(["-I", str(init)])


def run(label, command=None):
    path = report / f"{profile}-{label}.log"
    with path.open("w") as log:
        proc = subprocess.Popen(args, cwd=root, stdin=subprocess.PIPE, stdout=log,
                                stderr=subprocess.STDOUT, text=True)
        connection = None

        def read_exact(size):
            data = b""
            while len(data) < size:
                chunk = connection.recv(size - len(data))
                if not chunk:
                    raise RuntimeError("RCON closed")
                data += chunk
            return data

        def request(kind, body):
            packet = struct.pack("<ii", 42, kind) + body.encode() + b"\0\0"
            connection.sendall(struct.pack("<i", len(packet)) + packet)
            size = struct.unpack("<i", read_exact(4))[0]
            response = read_exact(size)
            identifier, response_type = struct.unpack("<ii", response[:8])
            assert identifier == 42, "RCON rejected request"
            return response[8:-2].decode()

        try:
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline:
                content = path.read_text(errors="replace")
                if proc.poll() is not None:
                    if baseline:
                        if pipe_profile:
                            assert "cannot inject into" in content and "com.drmangotea.tfmg.mixin.FluidPropagatorMixin" in content, content[-12000:]
                            print("Original fluid propagation priority conflict reproduced", flush=True)
                        else:
                            assert "Invalid descriptor" in content and "RequestPromiseQueueMixin" in content, content[-12000:]
                            print("Original descriptor conflict reproduced", flush=True)
                        return
                    raise RuntimeError(f"Server exited: {path}\n{content[-10000:]}")
                if "Done (" in content:
                    break
                time.sleep(0.25)
            else:
                raise RuntimeError(f"Server startup timed out: {path}")
            if baseline:
                raise AssertionError("Baseline unexpectedly started successfully")
            connection = socket.create_connection(("127.0.0.1", rcon_port), timeout=30)
            request(3, rcon_password)
            def checked(command, expected):
                response = request(2, command)
                assert expected in response, response + "\n" + path.read_text(errors="replace")[-12000:]
                print(response, flush=True)
                return response

            def sample():
                response = checked("teutonlib_fluid_test sample", "Fluid sample PASS")
                return [tuple(map(int, pair.split("=")[1].split(","))) for pair in response.split("PASS ")[1].strip().split(";")]

            def wait_transfer(previous):
                deadline = time.monotonic() + 35
                while time.monotonic() < deadline:
                    values = sample()
                    assert all(source + dest == 8000 for source, dest in values), values
                    if all(dest > old for (_, dest), old in zip(values, previous)):
                        return values
                    time.sleep(0.75)
                raise AssertionError("No fluid transfer in one or more lanes: " + str(values))

            if pipe_profile:
                if not pipe_pair:
                    checked("teutonlib_fluid_test transform", "adapter=false")
                else:
                    if profile == "pipes-all":
                        checked("teutonlib_logistics_test" + (" restart" if label == "restart" else ""),
                                "Logistics restart PASS" if label == "restart" else "Logistics verification PASS")
                    if label == "first":
                        checked("teutonlib_fluid_test verify", "Fluid verify PASS")
                        checked("teutonlib_fluid_test prepare", "Fluid prepare PASS")
                        values = wait_transfer([0, 0, 0])
                        checked("teutonlib_fluid_test break", "Fluid break PASS")
                        time.sleep(2)
                        stopped = sample()
                        time.sleep(2)
                        assert sample() == stopped, "Disconnected pipes kept transferring fluid"
                        checked("teutonlib_fluid_test restore", "Fluid restore PASS")
                        # Refill restores source+destination total to >8000; compare destination deltas here.
                        deadline = time.monotonic() + 35
                        while time.monotonic() < deadline:
                            values = sample()
                            if all(dest > old for (_, dest), (_, old) in zip(values, stopped)):
                                break
                            time.sleep(0.75)
                        else:
                            raise AssertionError("Restored pipes did not resume transfer: " + str(values))
                    else:
                        saved = sample()
                        checked("teutonlib_fluid_test restore", "Fluid restore PASS")
                        deadline = time.monotonic() + 35
                        while time.monotonic() < deadline:
                            values = sample()
                            if all(dest > old for (_, dest), (_, old) in zip(values, saved)):
                                break
                            time.sleep(0.75)
                        else:
                            raise AssertionError("No resumed transfer after world restart: " + str(values) + "\n" +
                                                 checked("teutonlib_fluid_test status", "Fluid status PASS"))
                    print("Fluid networks " + label + " PASS", flush=True)
            if command:
                expected = "Logistics restart PASS" if label == "restart" else "Logistics verification PASS"
                response = request(2, command)
                assert expected in response, response + "\n" + path.read_text(errors="replace")[-10000:]
                print(expected, flush=True)
            request(2, "stop")
            proc.wait(timeout=60)
            assert proc.returncode == 0, path
            print(f"{profile} {label}: clean shutdown", flush=True)
        finally:
            if proc.poll() is None:
                try:
                    if connection:
                        request(2, "stop")
                    else:
                        proc.terminate()
                    proc.wait(timeout=30)
                except (OSError, RuntimeError, subprocess.TimeoutExpired):
                    proc.terminate()
                    proc.wait(timeout=10)
            if connection:
                connection.close()


if not restart_only:
    run("first", "teutonlib_logistics_test" if profile in ("both", "packaged") else None)
if has_restart:
    run("restart", "teutonlib_logistics_test restart" if profile in ("both", "packaged") else None)
(report / f"{profile}.json").write_text(json.dumps({"profile": profile, "passed": True}, indent=2) + "\n")
