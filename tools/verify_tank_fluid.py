#!/usr/bin/env python3
"""Check real tank-window draws in an isolated copy of the supplied Tank Test save.

The source world is only read. The development client changes tank poses/fill levels
in its own copy, captures the actual renderer hook and writes screenshots and JSON.
Run with access to the graphical display. No existing game process is controlled.
"""

from pathlib import Path
import argparse
import hashlib
import json
import os
import secrets
import shutil
import signal
import subprocess
import time
import traceback


ROOT = Path(__file__).resolve().parents[1]


def manifest(directory):
    return {
        str(path.relative_to(directory)): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(directory.rglob("*")) if path.is_file()
    }


def load_json(path):
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text())
    except json.JSONDecodeError:
        return None


def stop_child(process):
    """Only stop the process group created by this invocation."""
    if process is None or process.poll() is not None:
        return
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=20)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=10)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--world", type=Path, default=ROOT / "run/client/saves/Tank Test")
    parser.add_argument("--timeout", type=int, default=420)
    parser.add_argument("--prepare-only", action="store_true", help="Create the isolated copy and launch command without starting Minecraft")
    options = parser.parse_args()
    source = options.world.resolve(strict=True)
    assert (source / "level.dat").is_file() and (source / "sublevels").is_dir(), source
    run_id = time.strftime("%Y%m%d-%H%M%S") + "-" + secrets.token_hex(4)
    instance = ROOT / "run/smoke/tank-fluid-client" / run_id
    client = instance / "client"
    save_name = "tank-fluid-verification-copy"
    copied_save = client / "saves" / save_name
    report = ROOT / "build/reports/tank-fluid-client" / run_id
    report.mkdir(parents=True)
    before = manifest(source)
    shutil.copytree(source, copied_save)
    assert manifest(copied_save) == before, "World copy differs before launch"
    (client / "tank-fluid-copy.marker").write_text(json.dumps({"source": str(source), "copy": str(copied_save)}))
    (client / "options.txt").write_text(
        "onboardAccessibility:false\npauseOnLostFocus:false\nfullscreen:false\n"
        "enableVsync:false\nrenderDistance:8\nsimulationDistance:5\nmaxFps:60\nfov:0.0\n"
    )
    (client / "config").mkdir(exist_ok=True)
    (client / "config/fml.toml").write_text("earlyWindowControl = false\n")
    init_path = report / "isolated-client.gradle"
    init_path.write_text("""allprojects {
    afterEvaluate {
        neoForge.runs.getByName('client').with {
            gameDirectory = file(%s)
            systemProperty('teutonlib.tankFluidClientTests', 'true')
            programArgument('--quickPlaySingleplayer')
            programArgument(%s)
        }
    }
}
""" % (json.dumps(str(client)), json.dumps(save_name)))
    command = ["./gradlew", "runClient", "-PcsoRuntime=all", "-PtankFluidTests",
               "--offline", "--console=plain", "--no-daemon", "-I", str(init_path)]
    result = {"passed": False, "source": str(source), "copiedSave": str(copied_save),
              "instance": str(instance), "reportDirectory": str(report), "command": command,
              "sourceManifest": before, "preparedOnly": options.prepare_only}
    (report / "launch.json").write_text(json.dumps(result, indent=2) + "\n")
    if options.prepare_only:
        result["sourceUnchanged"] = manifest(source) == before
        (report / "report.json").write_text(json.dumps(result, indent=2) + "\n")
        print(f"Prepared disposable Tank Test copy: {copied_save}")
        print(f"Launch instructions: {report / 'launch.json'}")
        return
    environment = dict(os.environ)
    environment.setdefault("DISPLAY", ":0")
    process = None
    output = client / "tank-fluid-verification"
    try:
        with (report / "client.log").open("w") as log:
            process = subprocess.Popen(command, cwd=ROOT, env=environment, stdout=log,
                                       stderr=subprocess.STDOUT, start_new_session=True)
            deadline = time.monotonic() + options.timeout
            previous = 0
            while time.monotonic() < deadline:
                captures = sorted(output.glob("*.json")) if output.exists() else []
                if len(captures) != previous:
                    previous = len(captures)
                    print(f"Tank fluid client: {max(0, previous - 1)} capture reports", flush=True)
                status = load_json(output / "report.json")
                if status:
                    result["client"] = status
                    assert status.get("passed"), status
                    process.wait(timeout=90)
                    assert process.returncode == 0, f"Client exited with {process.returncode}"
                    break
                code = process.poll()
                if code is not None:
                    raise RuntimeError(f"Tank fluid client exited ({code}) before writing a report: {report / 'client.log'}")
                time.sleep(.5)
            else:
                raise TimeoutError(f"Tank fluid client exceeded {options.timeout}s: {report / 'client.log'}")
        captures = result["client"]["captures"]
        expected = {"saved-chocolate", "tilted-90-cached", "translated-90"}
        expected.update(f"{pose}-{percent}" for pose in ("upright", "sideways", "tilted") for percent in (10, 50, 90))
        assert {capture["name"] for capture in captures} == expected, captures
        for capture in captures:
            assert capture["passed"] and Path(capture["screenshot"]).is_file(), capture
            assert capture["draw"]["planeWorldYDeviation"] < 1e-5, capture
            if capture["name"] != "saved-chocolate":
                assert capture["draw"]["meshReused"], capture
            assert abs(capture["volume"] / capture["capacityVolume"] - capture["draw"]["fraction"]) < 2e-5, capture
        contents = (report / "client.log").read_text(errors="replace")
        for failure in ("Mixin apply failed", "Mixin transformation of", "InjectionError"):
            assert failure not in contents, f"{failure} in client.log"
        result["passed"] = True
    except BaseException:
        result["failure"] = traceback.format_exc()
        raise
    finally:
        stop_child(process)
        if output.exists():
            shutil.copytree(output, report / "client-evidence", dirs_exist_ok=True)
        result["sourceUnchanged"] = manifest(source) == before
        if not result["sourceUnchanged"]:
            result["passed"] = False
            result["sourceChangeWarning"] = "The source changed during the test; verify no user client was independently saving it."
        (report / "report.json").write_text(json.dumps(result, indent=2) + "\n")
        assert result["sourceUnchanged"], "Original save changed during verification"
    print(f"Tank fluid verification PASS: {report}", flush=True)
    print(f"Screenshots: {len(result['client']['captures'])}; original save unchanged", flush=True)


if __name__ == "__main__":
    main()
