#!/usr/bin/env python3
"""Measure actual Sable docking geometry for three masses and HALF/FULL arms.

Each case runs a newly created, ordinary Create/CTF train in a disposable server
world. Connector physics owns the companion platform throughout the experiment.
The runner never teleports that platform or modifies its physical properties.
Mass profiles use real block states in the test fixture. The distinction between
initial build distance and the measured distance after docking is retained.
Three/four occupied blocks including both bases mean two/three blocks between
base centers, respectively (native HALF/FULL connector extension).
"""

import argparse
import itertools
import json
import math
import os
import secrets
import signal
import struct
import subprocess
import time
import traceback

from verify_train_portal_client import ROOT, Rcon, available_ports, load_json, wait_until


COMMON = ["-PcsoRuntime=all", "-PtrainPortalTests", "-PhybridTrainTests", "--offline", "--console=plain", "--no-daemon"]
SEED = "547420261102"
NATIVE_CARRIAGE = "com.simibubi.create.content.trains.entity.CarriageContraption"


class CompleteRcon(Rcon):
    """Join Minecraft's 4096-byte response chunks using a read-only delimiter."""

    def request(self, command, kind=2):
        if kind != 2 or command == "stop":
            return super().request(command, kind)
        self.sequence += 1
        command_id = self.sequence
        self.sequence += 1
        delimiter_id = self.sequence
        def send(request_id, text):
            body = struct.pack("<ii", request_id, 2) + text.encode() + b"\0\0"
            self.socket.sendall(struct.pack("<i", len(body)) + body)

        send(command_id, command)
        chunks = []
        delimiter_sent = False
        while True:
            size = struct.unpack("<i", self.exact(4))[0]
            if not 10 <= size <= 1_000_000:
                raise RuntimeError(f"Invalid RCON response length: {size}")
            packet = self.exact(size)
            response_id = struct.unpack("<i", packet[:4])[0]
            if response_id == delimiter_id:
                return b"".join(chunks).decode()
            if response_id != command_id:
                raise RuntimeError("Test server rejected or desynchronized RCON")
            chunks.append(packet[8:-2])
            if not delimiter_sent:
                # Minecraft's RCON implementation does not reliably parse two
                # requests coalesced in one socket read. Wait for the first
                # response before sending the read-only delimiter command.
                send(delimiter_id, "list")
                delimiter_sent = True


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n")


def stop_case_process(process):
    """Stop only the isolated process group created for this disposable case."""
    try:
        process.wait(timeout=20)
    except subprocess.TimeoutExpired:
        pass
    try:
        os.killpg(process.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        try:
            os.killpg(process.pid, 0)
        except ProcessLookupError:
            process.wait(timeout=10)
            return
        time.sleep(.1)
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    process.wait(timeout=10)


def run_case(profile, distance, replicate, run_dir, instance, args):
    case_id = f"{profile}-{distance}-r{replicate}"
    report_dir = run_dir / case_id
    server_dir = instance / case_id / "server"
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
    evidence = {
        "profile": profile, "initialBaseDistanceBlocks": distance,
        "connectionLengthIncludingBasesBlocks": distance + 1, "replicate": replicate,
        "seed": SEED, "instance": str(server_dir), "phases": {},
        "floorClearance": {"mode": "legacy" if args.legacy_floor else "cleared",
                           "removed": None if args.legacy_floor else {"x": [20, 125], "y": [0, 99], "z": [args.clear_from_z, 32]},
                           "trackFloorPreserved": args.legacy_floor or args.clear_from_z > 0,
                           "trackFloorPreservedAtZ": 0 if args.legacy_floor or args.clear_from_z > 0 else None},
    }

    with (report_dir / "server.log").open("w") as log:
        try:
            processes["server"] = subprocess.Popen(
                ["./gradlew", "runSmokeServer", *COMMON, f"-PperipheralTestDir={server_dir}"],
                cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            wait_until(lambda: "Done (" in (report_dir / "server.log").read_text(errors="replace"),
                       processes, 240, f"starting {case_id} server")
            rcon = CompleteRcon(rcon_port, password)

            def command(text):
                answer = rcon.request(text)
                if any(error in answer for error in (
                        "FAIL", "Unknown", "Incorrect", "unexpected error", "Check failed", "Index:")):
                    raise RuntimeError(f"{text}: {answer}")
                return answer

            def demo(action, verbose=True):
                answer = command("teutonlib_train_demo " + action)
                if verbose:
                    print(f"[{case_id}] {action}: {answer}", flush=True)
                return answer

            def alive_sleep(seconds):
                deadline = time.monotonic() + seconds
                while time.monotonic() < deadline:
                    if processes["server"].poll() is not None:
                        raise RuntimeError(f"{case_id} server exited while waiting")
                    time.sleep(min(.2, max(0, deadline - time.monotonic())))

            def building_settled():
                wait_until(lambda: "queued=0" in demo("status", False), processes, 30,
                           "building identical native wagon blocks")

            def train_stopped():
                wait_until(lambda: "travel=0.0 " in demo("status", False), processes, 40,
                           "completing the native rail movement")

            def validate_study(status):
                study = status["weightStudy"]
                assert study["profile"] == profile, study
                assert study["initialBaseCenterSeparation"] == distance, study
                assert study["connectionLengthIncludingBasesBlocks"] == distance + 1, study
                assert study["expectedEffectiveLength"] == ("HALF" if distance == 2 else "FULL"), study
                for body in ("platform", "wagon"):
                    assert math.isfinite(study[body]["nativeMass"]) and study[body]["nativeMass"] > 0, study
                return study

            def sample(label, ticks, movement=None, expect_connection=True):
                phase_file = server_dir / "demo-world/train-docking-verification" / f"sag-{label}.json"
                demo(f"dock-sag-start {label} {ticks}")
                if movement is not None:
                    demo(f"move {movement}")
                capture = wait_until(lambda: load_json(phase_file), processes,
                                     max(30, ticks / 10), f"sampling {label} native physics")
                assert len(capture["samples"]) == ticks, (label, len(capture["samples"]), ticks)
                assert capture["hybridEnabled"] is False, capture
                assert capture["carriages"] == evidence["initialStatus"]["carriages"], (label, capture["carriages"])
                study = validate_study(capture)
                for body in ("platform", "wagon"):
                    assert math.isclose(study[body]["nativeMass"],
                                        evidence["initialStatus"]["weightStudy"][body]["nativeMass"], rel_tol=1e-7), (label, body, study)
                locked_ticks = 0
                for observation in capture["samples"]:
                    assert observation["hybridEnabled"] is False and observation["sameDimension"], (label, observation)
                    locked = observation["trainLocked"] and observation["platformLocked"] and observation["validConstraints"] == 1
                    locked_ticks += int(locked)
                    for prefix in ("train", "platform"):
                        assert observation[prefix + "Signal"] == (8 if distance == 2 else 15), (label, observation)
                        assert observation[prefix + "EffectiveLength"] == ("HALF" if distance == 2 else "FULL"), (label, observation)
                        if locked:
                            assert math.isclose(observation[prefix + "Extension"], .5 if distance == 2 else 1, abs_tol=1e-3), (label, observation)
                    for key, value in observation.items():
                        if isinstance(value, (int, float)) and not isinstance(value, bool):
                            assert math.isfinite(value), (label, key, value)
                capture["expectedConnection"] = expect_connection
                capture["lockedTicks"] = locked_ticks
                capture["unlockedOrInvalidConstraintTicks"] = ticks - locked_ticks
                capture["nativeJointPersisted"] = locked_ticks == ticks
                evidence["phases"][label] = capture
                write_json(report_dir / f"sag-{label}.json", capture)
                write_json(report_dir / "evidence.json", evidence)
                print(f"[{case_id}] {label} summary: {json.dumps(capture['summary'], ensure_ascii=False)}", flush=True)
                return capture

            for text in (
                    "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "gamerule commandModificationBlockLimit 1000000",
                    "gamerule doTileDrops false", "time set 6000", "weather clear"):
                command(text)
            for dimension, left, right, floor in (
                    ("minecraft:overworld", 20, 125, "smooth_stone"),
                    ("minecraft:the_nether", 136, 260, "polished_blackstone_bricks")):
                command(f"execute in {dimension} run forceload add {left} -64 {right} 64")
                command(f"execute in {dimension} run fill {left} 99 -48 {right} 125 48 air")
                command(f"execute in {dimension} run fill {left} 99 -24 {right} 99 24 {floor}")
            command("execute in minecraft:the_nether run fill 150 100 0 255 100 0 create:track[shape=xo]")
            # Clear all blocks beneath the free platform; a fixture floor must
            # not support its far edge or limit the measured pitch/sag.
            if not args.legacy_floor:
                command(f"fill 20 0 {args.clear_from_z} 125 99 32 air")
            demo("prepare"); alive_sleep(1)
            demo("enter"); alive_sleep(.25)
            for offset in (1, 7, 17, 23):
                demo(f"bogey {offset}")
            demo("deck 0"); demo("deck 1"); building_settled()
            demo("machines"); building_settled()
            demo("run"); alive_sleep(3)
            demo("assemble-native"); alive_sleep(3)
            demo("dock-hybrid false")
            demo(f"dock-fixture {profile} {distance}")
            if not args.legacy_floor:
                command(f"fill 20 0 {args.clear_from_z} 125 99 32 air")
            track_check = command("execute if block 50 100 0 create:track")
            evidence["trackAfterFloorClear"] = {"position": [50, 100, 0], "result": track_check}
            assert "Test passed" in track_check, ("Native track missing after floor clear", track_check)
            evidence["immediatelyAfterAssembly"] = json.loads(demo("dock wide"))

            def has_native_joint(status):
                sag = status.get("sag", {})
                return bool(sag.get("trainLocked") and sag.get("platformLocked") and sag.get("validConstraints") == 1)

            evidence["initialNativeDockLocked"] = has_native_joint(evidence["immediatelyAfterAssembly"])
            # Optional early sampling makes any weight-induced loss visible
            # before a later acceptance check could reject the unlocked pair.
            early_lock_loss = False
            if args.observe_approach_ticks:
                evidence["initialStatus"] = evidence["immediatelyAfterAssembly"]
                validate_study(evidence["initialStatus"])
                approach = sample("approach", args.observe_approach_ticks,
                                  expect_connection=evidence["initialNativeDockLocked"])
                early_lock_loss = evidence["initialNativeDockLocked"] and not approach["nativeJointPersisted"]
            # Retain native magnetic approach and report what it actually does,
            # without forcing a connection outside the acceptance distance.
            lock_observations = []
            deadline = time.monotonic() + (0 if early_lock_loss else args.lock_timeout)
            while True:
                status = json.loads(demo("dock-status", False))
                lock_observations.append(status)
                if has_native_joint(status):
                    break
                if time.monotonic() >= deadline:
                    break
                alive_sleep(.25)
            evidence["lockApproachObservations"] = lock_observations
            evidence["initialStatus"] = status
            assert all(carriage["class"] == NATIVE_CARRIAGE for carriage in status["carriages"]), status
            validate_study(status)
            locked = has_native_joint(status)
            if locked:
                alive_sleep(4)
                evidence["postLockSettlingStatus"] = json.loads(demo("dock-status"))
                locked = has_native_joint(evidence["postLockSettlingStatus"])
            evidence["nativeDockLocked"] = locked
            if locked:
                evidence["sourceVerification"] = json.loads(demo("dock-verify source"))
                demo("route"); alive_sleep(2)
                demo("verify")
                evidence["initialStatus"] = json.loads(demo("dock-status"))
                sample("standstill", args.standstill_ticks)
                sample("moving", args.motion_ticks, args.motion_blocks)
                train_stopped()
                sample("settling", args.settle_ticks)
                sample("after-movement", args.standstill_ticks)
                demo("verify")
            else:
                # An unconnected platform is not a sagging fixed joint. Keep
                # this outcome separate from the locked-connection experiment.
                if not args.observe_approach_ticks:
                    sample("unlocked-observation", args.standstill_ticks, expect_connection=False)
                evidence["motionSkippedReason"] = "No native locked constraint after the configured approach interval"
            evidence["finalStatus"] = json.loads(demo("dock-status"))
            final, initial = evidence["finalStatus"], evidence["initialStatus"]
            assert final["carriages"] == initial["carriages"], ("carriages", final)
            assert final["wagon"]["uuid"] == initial["wagon"]["uuid"], ("wagon", final)
            assert final["platform"]["uuid"] == initial["platform"]["uuid"], ("platform", final)
            assert final["cargo"] == initial["cargo"], ("cargo", final)
            evidence["identitiesAndCargoPreserved"] = True
            evidence["nativeJointPersisted"] = locked and all(phase["nativeJointPersisted"] for phase in evidence["phases"].values())
            evidence["physicsExecutionVerified"] = True
            evidence["note"] = (
                "Fresh ordinary Create CarriageContraption instances, native CTF conversion and Sable/Rapier physics. "
                "Hybrid mode is disabled. Real block-state mass profiles retain the same external platform shape. "
                "Initial build distance is not asserted to equal the actual connector distance after magnetic docking. "
                "Execution success verifies observations, not that the joint remains geometrically rigid.")
            write_json(report_dir / "evidence.json", evidence)
            command("stop")
            print(f"[{case_id}] MEASURED {report_dir}", flush=True)
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
                stop_case_process(process)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profiles", choices=("light", "normal", "heavy"), nargs="+", default=["light", "normal", "heavy"])
    parser.add_argument("--distances", type=int, choices=(2, 3), nargs="+", default=[2, 3],
                        help="base-center distance: 2 = three occupied blocks including bases/HALF; 3 = four/FULL")
    parser.add_argument("--replicates", type=int, default=1)
    parser.add_argument("--standstill-ticks", type=int, default=200)
    parser.add_argument("--motion-ticks", type=int, default=150)
    parser.add_argument("--motion-blocks", type=float, default=24)
    parser.add_argument("--settle-ticks", type=int, default=100)
    parser.add_argument("--lock-timeout", type=float, default=20)
    parser.add_argument("--observe-approach-ticks", type=int, default=0,
                        help="optional capture immediately after assembly to record any native joint loss")
    parser.add_argument("--legacy-floor", action="store_true", help="optional control with the original platform floor at Y=99")
    parser.add_argument("--clear-from-z", type=int, choices=(0, 1, 2, 3), default=1,
                        help="first cleared lateral floor column; 0 also removes the floor below native floating track")
    args = parser.parse_args()
    for ticks in (args.standstill_ticks, args.motion_ticks, args.settle_ticks):
        if not 1 <= ticks <= 1200:
            parser.error("sample ticks must be between 1 and 1200")
    if not 0 <= args.observe_approach_ticks <= 1200:
        parser.error("approach sample ticks must be between 0 and 1200")
    if not 0 < args.motion_blocks <= 24:
        parser.error("motion must remain before the portal: use 0 < blocks <= 24")
    if not 1 <= args.replicates <= 10 or not 0 <= args.lock_timeout <= 120:
        parser.error("use 1..10 replicates and 0..120 seconds for native locking")
    run_id = time.strftime("%Y%m%d-%H%M%S")
    report_dir = ROOT / "build/reports/train-docking-weight" / run_id
    instance = ROOT / "run/smoke/train-docking-weight" / run_id
    report_dir.mkdir(parents=True)
    (report_dir.parent / "latest.txt").write_text(str(report_dir))
    aggregate = {
        "description": "Real Sable docking mass and 3/4 occupied-block connection comparison (native HALF/FULL)",
        "seed": SEED, "settings": vars(args), "cases": {},
        "interpretation": "Three/four occupied blocks including both bases = two/three blocks between base centers (HALF/FULL). Actual locked distance and tip error are measured separately.",
    }
    try:
        for replicate, distance, profile in itertools.product(range(1, args.replicates + 1), args.distances, args.profiles):
            case_id = f"{profile}-{distance}-r{replicate}"
            try:
                evidence = run_case(profile, distance, replicate, report_dir, instance, args)
            except Exception as error:
                aggregate["cases"][case_id] = {"physicsExecutionVerified": False, "exception": repr(error),
                                                "report": str(report_dir / case_id / "evidence.json")}
                write_json(report_dir / "comparison.json", aggregate)
                print(f"[{case_id}] FAILED; failure evidence preserved, continuing matrix: {error}", flush=True)
                continue
            aggregate["cases"][case_id] = {
                "profile": profile, "initialBaseDistanceBlocks": distance,
                "connectionLengthIncludingBasesBlocks": distance + 1, "replicate": replicate,
                "physicsExecutionVerified": evidence["physicsExecutionVerified"],
                "nativeDockLocked": evidence["nativeDockLocked"],
                "nativeJointPersisted": evidence["nativeJointPersisted"],
                "report": str(report_dir / case_id / "evidence.json"),
                "initialStatus": evidence["initialStatus"], "finalStatus": evidence["finalStatus"],
                "phases": {label: {"hybridEnabled": phase["hybridEnabled"],
                                   "nativeJointPersisted": phase["nativeJointPersisted"],
                                   "lockedTicks": phase["lockedTicks"],
                                   "unlockedOrInvalidConstraintTicks": phase["unlockedOrInvalidConstraintTicks"],
                                   "carriages": phase.get("carriages"), "summary": phase["summary"]}
                           for label, phase in evidence["phases"].items()},
            }
            write_json(report_dir / "comparison.json", aggregate)
        aggregate["physicsExecutionVerified"] = all(case["physicsExecutionVerified"] for case in aggregate["cases"].values())
    except BaseException as error:
        aggregate["physicsExecutionVerified"] = False
        aggregate["exception"] = repr(error)
        raise
    finally:
        write_json(report_dir / "comparison.json", aggregate)
        print(f"REPORT {report_dir}", flush=True)
    if not aggregate["physicsExecutionVerified"]:
        raise SystemExit("One or more cases failed; the complete matrix and failure evidence were preserved")


if __name__ == "__main__":
    main()
