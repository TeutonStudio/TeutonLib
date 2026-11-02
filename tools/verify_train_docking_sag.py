#!/usr/bin/env python3
"""Measure native docking sag on identical Sable trains, with hybrid mode on/off.

Every primary case uses a fresh disposable world with the same seed, two native
Create/CTF carriages, and the same free Sable docking platform. The test samples
real server physics; it does not move the platform or render a synthetic video.
The final phase changes only hybrid mode on the existing train and constraint.
An additional --mode native case asks the test fixture for a plain Create
CarriageContraption before CTF's first conversion, with hybrid mode disabled.
"""

import argparse
import json
import math
import secrets
import subprocess
import time
import traceback

from verify_train_portal_client import ROOT, Rcon, available_ports, load_json, stop_process, wait_until


COMMON = ["-PcsoRuntime=all", "-PtrainPortalTests", "-PhybridTrainTests", "--offline", "--console=plain", "--no-daemon"]
SEED = "547420261102"


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def run_case(mode, run_dir, instance, args):
    report_dir = run_dir / mode
    server_dir = instance / mode / "server"
    report_dir.mkdir(parents=True)
    server_dir.mkdir(parents=True)
    game_port, rcon_port = available_ports()
    password = secrets.token_hex(16)
    (server_dir / "eula.txt").write_text("eula=true\n")
    (server_dir / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={game_port}\nonline-mode=false\n"
        f"level-name=demo-world\nlevel-type=minecraft:flat\nlevel-seed={SEED}\n"
        "generate-structures=false\nview-distance=8\nsimulation-distance=4\n"
        "spawn-protection=0\ndifficulty=peaceful\nallow-flight=true\n"
        f"enable-rcon=true\nrcon.port={rcon_port}\nrcon.password={password}\nbroadcast-rcon-to-ops=false\n")
    processes, rcon = {}, None
    evidence = {"mode": mode, "seed": SEED, "instance": str(server_dir), "phases": {}}

    with (report_dir / "server.log").open("w") as log:
        try:
            processes["server"] = subprocess.Popen(
                ["./gradlew", "runSmokeServer", *COMMON, f"-PperipheralTestDir={server_dir}"],
                cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
            wait_until(lambda: "Done (" in (report_dir / "server.log").read_text(errors="replace"),
                       processes, 240, f"starting {mode} server")
            rcon = Rcon(rcon_port, password)

            def command(text):
                answer = rcon.request(text)
                if any(error in answer for error in (
                        "FAIL", "Unknown", "Incorrect", "unexpected error", "Check failed", "Index:")):
                    raise RuntimeError(f"{text}: {answer}")
                return answer

            def demo(action, verbose=True):
                answer = command("teutonlib_train_demo " + action)
                if verbose:
                    print(f"[{mode}] {action}: {answer}", flush=True)
                return answer

            def alive_sleep(seconds):
                deadline = time.monotonic() + seconds
                while time.monotonic() < deadline:
                    if processes["server"].poll() is not None:
                        raise RuntimeError(f"{mode} server exited while waiting")
                    time.sleep(min(.2, max(0, deadline - time.monotonic())))

            def building_settled():
                wait_until(lambda: "queued=0" in demo("status", False), processes, 30,
                           "building identical native wagon blocks")

            def train_stopped():
                wait_until(lambda: "travel=0.0 " in demo("status", False), processes, 40,
                           "completing the native rail movement")

            def sample(label, ticks, movement=None):
                phase_file = server_dir / "demo-world/train-docking-verification" / f"sag-{label}.json"
                demo(f"dock-sag-start {label} {ticks}")
                if movement is not None:
                    demo(f"move {movement}")
                capture = wait_until(lambda: load_json(phase_file), processes,
                                     max(30, ticks / 10), f"sampling {label} native physics")
                assert capture["samples"], capture
                assert len(capture["samples"]) == ticks, (label, len(capture["samples"]), ticks)
                expected_hybrid = mode == "hybrid"
                if label in ("toggle-settling", "same-body-toggle"):
                    expected_hybrid = not expected_hybrid
                assert capture["hybridEnabled"] == expected_hybrid, capture
                assert capture["carriages"] == evidence["initialStatus"]["carriages"], (label, capture["carriages"])
                # Numeric values are observed evidence, never corrected by this runner.
                for observation in capture["samples"]:
                    assert observation["hybridEnabled"] == expected_hybrid, (label, observation)
                    assert observation["sameDimension"], (label, observation)
                    assert observation["trainLocked"] and observation["platformLocked"], (label, observation)
                    assert observation["validConstraints"] == 1, (label, observation)
                    for key, value in observation.items():
                        if isinstance(value, (int, float)) and not isinstance(value, bool):
                            assert math.isfinite(value), (label, key, value)
                evidence["phases"][label] = capture
                write_json(report_dir / f"sag-{label}.json", capture)
                write_json(report_dir / "evidence.json", evidence)
                print(f"[{mode}] {label} summary: {json.dumps(capture['summary'], ensure_ascii=False)}", flush=True)
                return capture

            for text in (
                    "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "gamerule commandModificationBlockLimit 1000000",
                    "gamerule doTileDrops false", "time set 6000", "weather clear"):
                command(text)
            # Identical flat fixture geometry; all measured movement remains in the
            # source dimension, comfortably before the custom portal at X=100.
            for dimension, left, right, floor in (
                    ("minecraft:overworld", 20, 125, "smooth_stone"),
                    ("minecraft:the_nether", 136, 260, "polished_blackstone_bricks")):
                command(f"execute in {dimension} run forceload add {left} -64 {right} 64")
                command(f"execute in {dimension} run fill {left} 99 -48 {right} 125 48 air")
                command(f"execute in {dimension} run fill {left} 99 -24 {right} 99 24 {floor}")
            command("execute in minecraft:the_nether run fill 150 100 0 255 100 0 create:track[shape=xo]")
            # Station-first construction requires hybrid mode. Both cases use the
            # same construction sequence, then select the measured mode before
            # creating the docking body and native fixed joint.
            demo("prepare"); alive_sleep(1)
            demo("enter"); alive_sleep(.25)
            for offset in (1, 7, 17, 23):
                demo(f"bogey {offset}")
            demo("deck 0"); demo("deck 1"); building_settled()
            demo("machines"); building_settled()
            demo("run"); alive_sleep(3)
            demo("assemble-native" if mode == "native" else "assemble"); alive_sleep(3)
            demo("dock-hybrid " + str(mode == "hybrid").lower())
            demo("dock wide")
            alive_sleep(4)
            evidence["sourceVerification"] = json.loads(demo("dock-verify source"))
            demo("route"); alive_sleep(2)
            demo("verify")
            evidence["initialStatus"] = json.loads(demo("dock-status"))
            if mode == "native":
                assert all(carriage["class"] == "com.simibubi.create.content.trains.entity.CarriageContraption"
                           for carriage in evidence["initialStatus"]["carriages"]), evidence["initialStatus"]
            sample("standstill", args.standstill_ticks)
            sample("moving", args.motion_ticks, args.motion_blocks)
            train_stopped()
            sample("settling", args.settle_ticks)
            sample("after-movement", args.standstill_ticks)
            evidence["finalStatus"] = json.loads(demo("dock-status"))
            demo("verify")
            if not args.no_same_body_toggle and mode != "native":
                demo("dock-hybrid " + str(mode != "hybrid").lower())
                sample("toggle-settling", args.settle_ticks)
                sample("same-body-toggle", args.standstill_ticks)
                evidence["toggleStatus"] = json.loads(demo("dock-status"))
            elif mode == "native":
                evidence["sameBodyToggleSkipped"] = (
                    "The native baseline keeps hybrid mode disabled throughout; "
                    "same-body mode toggles are covered by the hybrid/sable cases.")
            for status_name in ("finalStatus", "toggleStatus"):
                if status_name not in evidence:
                    continue
                status, initial = evidence[status_name], evidence["initialStatus"]
                assert status["carriages"] == initial["carriages"], (status_name, "carriages", status)
                assert status["wagon"]["uuid"] == initial["wagon"]["uuid"], (status_name, "wagon", status)
                assert status["platform"]["uuid"] == initial["platform"]["uuid"], (status_name, "platform", status)
                assert status["cargo"] == initial["cargo"], (status_name, "cargo", status)
            evidence["identitiesAndCargoPreserved"] = True
            evidence["physicsExecutionVerified"] = True
            evidence["note"] = (
                "This checks native physics execution and records geometric error; "
                "execution success does not assert that the docking connection is rigid. "
                "The native case uses exact Create CarriageContraption instances before CTF conversion; "
                "the hybrid/sable cases use the same hybrid station assembly path. "
                "Reported carriage classes identify the exact native or hybrid type.")
            write_json(report_dir / "evidence.json", evidence)
            command("stop")
            print(f"[{mode}] MEASURED {report_dir}", flush=True)
            return evidence
        except BaseException as error:
            evidence["physicsExecutionVerified"] = False
            evidence["exception"] = repr(error)
            evidence["traceback"] = traceback.format_exc()
            if rcon:
                try:
                    evidence["failureStatus"] = rcon.request("teutonlib_train_demo dock-status")
                except (OSError, RuntimeError) as diagnostic:
                    evidence["failureStatusError"] = repr(diagnostic)
            write_json(report_dir / "evidence.json", evidence)
            raise
        finally:
            if rcon:
                try:
                    rcon.request("stop")
                except (OSError, RuntimeError):
                    pass
                rcon.close()
            for process in reversed(list(processes.values())):
                stop_process(process)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("hybrid", "sable", "native", "both"), default="both")
    parser.add_argument("--standstill-ticks", type=int, default=120)
    parser.add_argument("--motion-ticks", type=int, default=150)
    parser.add_argument("--motion-blocks", type=float, default=24)
    parser.add_argument("--settle-ticks", type=int, default=100)
    parser.add_argument("--no-same-body-toggle", action="store_true")
    args = parser.parse_args()
    for ticks in (args.standstill_ticks, args.motion_ticks, args.settle_ticks):
        if not 1 <= ticks <= 1200:
            parser.error("sample ticks must be between 1 and 1200")
    if not 0 < args.motion_blocks <= 24:
        parser.error("motion must remain before the portal: use 0 < blocks <= 24")
    run_id = time.strftime("%Y%m%d-%H%M%S") + "-" + args.mode
    report_dir = ROOT / "build/reports/train-docking-sag" / run_id
    instance = ROOT / "run/smoke/train-docking-sag" / run_id
    report_dir.mkdir(parents=True)
    (report_dir.parent / "latest.txt").write_text(str(report_dir))
    modes = ("hybrid", "sable") if args.mode == "both" else (args.mode,)
    aggregate = {
        "description": "Native docking geometry with hybrid mode enabled and disabled",
        "seed": SEED, "settings": vars(args), "cases": {},
    }
    try:
        for mode in modes:
            evidence = run_case(mode, report_dir, instance, args)
            aggregate["cases"][mode] = {
                "physicsExecutionVerified": evidence["physicsExecutionVerified"],
                "report": str(report_dir / mode / "evidence.json"),
                "phases": {label: {
                    "hybridEnabled": phase["hybridEnabled"],
                    "carriages": phase.get("carriages"),
                    "summary": phase["summary"],
                } for label, phase in evidence["phases"].items()},
            }
            write_json(report_dir / "comparison.json", aggregate)
        aggregate["physicsExecutionVerified"] = True
    except BaseException as error:
        aggregate["physicsExecutionVerified"] = False
        aggregate["exception"] = repr(error)
        raise
    finally:
        write_json(report_dir / "comparison.json", aggregate)
        print(f"REPORT {report_dir}", flush=True)


if __name__ == "__main__":
    main()
