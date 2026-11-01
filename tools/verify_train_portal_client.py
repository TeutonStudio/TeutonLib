#!/usr/bin/env python3
"""Capture real Sable train portal rendering in disposable server/client instances.

Uses the original optional mods, a hardware/OpenGL display, and the opt-in testmod.
No personal Minecraft instance or world is opened. Run outside the filesystem/network
sandbox so the two local game processes can connect and the client can access OpenGL.
"""

from pathlib import Path
import hashlib
import json
import math
import re
import secrets
import socket
import struct
import subprocess
import sys
import time
import traceback


ROOT = Path(__file__).resolve().parents[1]


def verify_aether_release():
    releases = {
        "aether-1.21.1-1.5.10-neoforge.jar": "a999a9265eb550a46a0f8eedfee7c3c75371d7f6cf34b7c09ff800e48633e9f8",
        "owo-lib-neoforge-0.12.15.5-beta.1+1.21.jar": "de6ed336bd80154b7241a7b3276694befc1c94550add8bcdfe7f82e5172fd13d",
    }
    for filename, expected in releases.items():
        actual = hashlib.sha256((ROOT / "dependencies/verification" / filename).read_bytes()).hexdigest()
        assert actual == expected, f"Unexpected verification release: {filename}: {actual}"
    return {"version": "1.5.10", "jars": releases}


def verify_native_aether_evidence(evidence):
    tag = "aether:water_bucket_create_aether_portal_to"
    assert evidence["generation"] == tag and evidence["portalCount"] == 4, evidence
    assert evidence["definitionResource"] == "aether:custom_portal_generation/water_bucket_create_aether_portal_to.json", evidence
    assert evidence["definitionSha256"] == "524d55924256dd983d6d2252558312ac7d8a9ce6c401a4f0d36cae54e1f717ad", evidence
    assert evidence["sourcePackId"], evidence
    assert evidence["source"] == "minecraft:overworld" and evidence["destination"] == "aether:the_aether", evidence
    assert evidence["frame"] == "glowstone" and evidence["placeholderCountPerSide"] == 144, evidence
    assert evidence["tracks"] == "bidirectional-bound", evidence
    portals = evidence["portals"]
    assert len(portals) == len({portal["uuid"] for portal in portals}) == 4, portals
    for portal in portals:
        assert portal["tag"] == tag and portal["class"].endswith(".GeneralBreakablePortal"), portal
        source = portal["world"] == evidence["source"]
        assert portal["world"] in (evidence["source"], evidence["destination"]), portal
        assert portal["destinationWorld"] == (evidence["destination"] if source else evidence["source"]), portal
        assert abs(portal["origin"][0] - evidence["sourceX" if source else "targetX"]) < 1e-6, portal
        assert abs(portal["destination"][0] - evidence["targetX" if source else "sourceX"]) < 1e-6, portal


def available_ports():
    """Reserve both choices together so they cannot accidentally be equal."""
    with socket.socket() as game, socket.socket() as rcon:
        game.bind(("127.0.0.1", 0))
        rcon.bind(("127.0.0.1", 0))
        return game.getsockname()[1], rcon.getsockname()[1]


class Rcon:
    def __init__(self, port, password):
        self.socket = socket.create_connection(("127.0.0.1", port), timeout=20)
        self.sequence = 0
        self.request(password, 3)

    def exact(self, count):
        data = b""
        while len(data) < count:
            part = self.socket.recv(count - len(data))
            if not part:
                raise RuntimeError("Test server closed RCON")
            data += part
        return data

    def request(self, command, kind=2):
        self.sequence += 1
        body = struct.pack("<ii", self.sequence, kind) + command.encode() + b"\0\0"
        self.socket.sendall(struct.pack("<i", len(body)) + body)
        size = struct.unpack("<i", self.exact(4))[0]
        if not 10 <= size <= 1_000_000:
            raise RuntimeError(f"Invalid RCON response length: {size}")
        packet = self.exact(size)
        response_id = struct.unpack("<i", packet[:4])[0]
        if response_id != self.sequence:
            raise RuntimeError("Test server rejected or desynchronized RCON")
        return packet[8:-2].decode()

    def close(self):
        self.socket.close()


def wait_until(predicate, processes, timeout, description):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        result = predicate()
        if result:
            return result
        for name, process in processes.items():
            code = process.poll()
            if code is not None:
                raise RuntimeError(f"{name} exited ({code}) while {description}")
        time.sleep(0.25)
    raise TimeoutError(description)


def load_json(path):
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text())
    except json.JSONDecodeError:
        return None  # The game thread may still be finishing the small report.


def stop_process(process):
    if process is None or process.poll() is not None:
        return
    try:
        process.wait(timeout=20)
        return
    except subprocess.TimeoutExpired:
        process.terminate()
    try:
        process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=10)


def verify_capture(snapshot, ship_id, dimension, mode, owner_dimension=None, build_count=2, verify_flywheel=False):
    assert snapshot["dimension"] == dimension, snapshot
    properties = snapshot["dimensionProperties"][dimension]
    has_sky = properties["hasSkyLight"]
    assert has_sky == (dimension != "minecraft:the_nether"), properties
    if dimension == "aether:the_aether":
        assert properties["minY"] == 0 and properties["height"] == 256, properties
        assert properties["effectsLocation"] == "aether:the_aether", properties
    assert Path(snapshot["screenshot"]).is_file(), snapshot
    assert snapshot["frameTimes"]["samples"] > 0, snapshot
    claims = snapshot["ctfClaims"]
    plots = [claim["plot"] for claim in claims]
    assert len(plots) == len(set(plots)), f"Two client owners claim one plot: {claims}"
    assert len(claims) <= build_count, f"More CTF client owners than wagons: {claims}"
    sessions = [session for session in snapshot["sessions"] if session["ship"] == ship_id]
    draws = [draw for draw in snapshot["draws"] if draw["ship"] == ship_id]
    lighting = [sample for sample in snapshot.get("lighting", []) if sample["ship"] == ship_id
                and sample["renderingDimension"] == dimension and not sample["portalPass"]
                and sample["projected"] == (mode == "projected")]
    points = [point for sample in lighting for point in sample["samples"]]
    assert points, f"No visible world/plot light samples for {ship_id}: {lighting}"
    if has_sky:
        assert any(point["worldSky"] > 0 for point in points), f"Open sky fixture was not illuminated: {points}"
    else:
        assert all(point["worldSky"] == 0 for point in points), f"Skyless fixture unexpectedly has skylight: {points}"
    assert any(point["worldBlock"] > 0 for point in points), f"Fixture lamps did not illuminate the wagon: {points}"
    if mode == "clear":
        assert not sessions, f"Stale portal session after complete withdrawal/passage: {sessions}"
        assert any(not draw["projected"] and not draw["portalPass"] for draw in draws), \
            f"No normal Sable draw after portal passage: {draws}"
        return
    assert sessions and all(session["sourceReady"] and session["clipPlane"] for session in sessions), \
        f"Portal render source or clipping unavailable: {sessions}"
    if owner_dimension == dimension:
        assert claims, f"Local build has no CTF client owner: {claims}"
    assert any(session["source"] == owner_dimension for session in sessions), sessions
    projected = mode == "projected"
    assert any(draw["renderingDimension"] == dimension and draw["projected"] == projected
               and draw["clipping"] and not draw["portalPass"] and draw["count"] > 0 for draw in draws), \
        f"No actual clipped {'projected' if projected else 'source'} draw in main world: {draws}"
    applied = [sample for sample in snapshot.get("appliedLighting", []) if sample["ship"] == ship_id
               and sample["dimension"] == dimension and sample["projected"] == projected and not sample["portalPass"]]
    assert applied and all(sample["vertices"] > 0 and sample["count"] > 0 for sample in applied), \
        f"No relit UV2 observed after an actual VBO draw: {ship_id}; {snapshot.get('appliedLighting')}"
    assert all(sample["skyScale"] == 15 for sample in applied), \
        f"Cached source skylight multiplied the already relit lightmap: {applied}"
    if has_sky:
        assert any(sample["sky"] > 0 for sample in applied), \
            f"Daylit wagon remained dark in {dimension}: {applied}"
    else:
        assert all(sample["sky"] == 0 for sample in applied), f"Source skylight leaked into Nether draw: {applied}"
    assert any(sample["block"] > 0 for sample in applied), f"Target lamps did not reach actual wagon UV2: {applied}"
    if verify_flywheel:
        instances = [sample for sample in snapshot.get("flywheelLighting", []) if sample["ship"] == ship_id
                     and sample["dimension"] == dimension and sample["projected"] == projected and not sample["portalPass"]]
        assert instances and all(sample["samples"] > 0 and sample["count"] > 0 and sample["backend"] for sample in instances), \
            f"No actual Flywheel lighting pass for kinetic wagon: {ship_id}; {snapshot.get('flywheelLighting')}"
        if has_sky:
            assert any(sample["sky"] > 0 for sample in instances), f"Daylit kinetic parts remained dark: {instances}"
        else:
            assert all(sample["sky"] == 0 for sample in instances), f"Source sky leaked into kinetic parts: {instances}"
            if projected and owner_dimension == "minecraft:overworld":
                assert any(sample["clearedSourceSkyInstances"] > 0 for sample in instances), \
                    f"No source instance skylight was overridden for the Nether projection: {instances}"
        assert any(sample["block"] > 0 for sample in instances), f"Target lamps did not illuminate kinetic parts: {instances}"


def verify_couplings(snapshot, source_x=80.0, target_x=160.0):
    diagnostics = snapshot.get("couplingDiagnostics", {})
    assert diagnostics.get("trainCount", 0) > 0, f"Native client train was never synchronized: {diagnostics}"
    segments = snapshot.get("couplings", [])
    for segment in segments:
        assert math.isfinite(segment["length"]) and 0 < segment["length"] <= 32, \
            f"Coupling spans unrelated world/plot coordinates: {segment}"
    if snapshot["name"] not in ("source-cross", "target-cross"):
        return
    local = [segment for segment in segments if segment["dimension"] == snapshot["dimension"]
             and not segment["portalPass"]]
    assert local, f"No actual coupling segment for the split train: {snapshot['name']}; {diagnostics}"
    clipped = [segment for segment in local if not segment["firstHead"] or not segment["secondHead"]]
    assert clipped, f"Coupling was not cut at the portal: {local}"
    portal_x = source_x if snapshot["dimension"] == "minecraft:overworld" else target_x
    for segment in clipped:
        for endpoint, head in (("first", "firstHead"), ("second", "secondHead")):
            if not segment[head]:
                assert abs(segment[endpoint][0] - portal_x) < 0.01, \
                    f"Cut coupling endpoint missed its portal plane: {segment}"


def main():
    aether = "--aether" in sys.argv[1:]
    destination = "aether:the_aether" if aether else "minecraft:the_nether"
    flywheel_backend = next((arg.split("=", 1)[1] for arg in sys.argv[1:]
                             if arg.startswith("--flywheel-backend=")), None)
    if flywheel_backend is not None and flywheel_backend not in ("instancing", "indirect"):
        raise ValueError("--flywheel-backend must be instancing or indirect")
    run_id = time.strftime("%Y%m%d-%H%M%S") + "-" + secrets.token_hex(4)
    instance = ROOT / "run/smoke/train-portal-client" / run_id
    server_dir, client_dir = instance / "server", instance / "client"
    report_dir = ROOT / "build/reports/train-portal-client" / run_id
    for directory in (server_dir, client_dir, report_dir):
        directory.mkdir(parents=True, exist_ok=True)
    game_port, rcon_port = available_ports()
    password = secrets.token_hex(16)
    (server_dir / "eula.txt").write_text("eula=true\n")
    (server_dir / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={game_port}\nonline-mode=false\n"
        "level-name=visual-verification-world\nlevel-type=minecraft:flat\n"
        "generate-structures=false\nview-distance=8\nsimulation-distance=4\n"
        "spawn-protection=0\ndifficulty=peaceful\nallow-flight=true\n"
        f"enable-rcon=true\nrcon.port={rcon_port}\nrcon.password={password}\n"
        "broadcast-rcon-to-ops=false\n"
    )
    (client_dir / "options.txt").write_text(
        "onboardAccessibility:false\nskipMultiplayerWarning:true\n"
        "pauseOnLostFocus:false\nfullscreen:false\nenableVsync:false\n"
        "renderDistance:8\nsimulationDistance:5\nmaxFps:120\nfov:0.0\n"
    )
    if flywheel_backend is not None:
        (client_dir / "config").mkdir(exist_ok=True)
        (client_dir / "config/flywheel-client.toml").write_text(
            f'backend = "flywheel:{flywheel_backend}"\nlimitUpdates = true\nworkerThreads = -1\n'
            '[flw_backends]\nlightSmoothness = "SMOOTH"\n'
        )
    init_path = report_dir / "isolated-client.gradle"
    # JSON string quoting is also valid for these Groovy string literals. The values
    # are generated local paths/ports, never shell fragments or user input.
    init_path.write_text("""allprojects {
    afterEvaluate {
        neoForge.runs.getByName('client').with {
            gameDirectory = file(%s)
            systemProperty('teutonlib.trainPortalClientTests', 'true')
            programArgument('--quickPlayMultiplayer')
            programArgument(%s)
        }
    }
}
""" % (json.dumps(str(client_dir)), json.dumps(f"127.0.0.1:{game_port}")))
    common = ["-PcsoRuntime=all", "-PtrainPortalTests", "--offline", "--console=plain", "--no-daemon"]
    if "--sodium" in sys.argv[1:]:
        common.append("-PtrainPortalSodium")
    if aether:
        common.append("-PtrainPortalAether")
    if "--rider-diagnostics" in sys.argv[1:]:
        common.append("-PtrainPortalRiderDiagnostics")
    server_args = ["./gradlew", "runSmokeServer", *common, f"-PperipheralTestDir={server_dir}"]
    client_args = ["./gradlew", "runClient", *common, "-I", str(init_path)]
    processes = {}
    connection = None
    checks = []
    result = {"passed": False, "instance": str(instance), "reportDirectory": str(report_dir), "captures": checks,
              "sodium": "--sodium" in sys.argv[1:],
              "verifyFlywheel": "--verify-flywheel" in sys.argv[1:],
              "flywheelBackend": flywheel_backend,
              "destination": destination,
              "limits": "Native draw calls and screenshots; inspect images for visual artifacts. Frame timings are observations, not a zero-cost guarantee."}
    client_output = client_dir / "train-portal-verification"
    if aether:
        result["aetherRelease"] = verify_aether_release()
    with (report_dir / "server.log").open("w") as server_log, (report_dir / "client.log").open("w") as client_log:
        try:
            processes["server"] = subprocess.Popen(server_args, cwd=ROOT, stdout=server_log, stderr=subprocess.STDOUT)
            wait_until(lambda: "Done (" in (report_dir / "server.log").read_text(errors="replace"),
                       processes, 240, "waiting for disposable test server")
            connection = Rcon(rcon_port, password)
            connection.request("gamerule doDaylightCycle false")
            connection.request("gamerule doWeatherCycle false")
            connection.request("gamerule commandModificationBlockLimit 1000000")
            connection.request("time set 6000")
            connection.request("weather clear")
            # Clearing generated Nether vegetation is fixture preparation, not a wagon drop.
            connection.request("gamerule doTileDrops false")
            for dimension, left, right in (("minecraft:overworld", 48, 100), (destination, 136, 192)):
                connection.request(f"execute in {dimension} run forceload add {left} -16 {right} 16")
                connection.request(f"execute in {dimension} run fill {left} 96 -16 {right} 110 16 minecraft:air")
                connection.request(f"execute in {dimension} run fill {left} 96 -16 {right} 96 16 minecraft:stone")
                connection.request(f"execute in {dimension} run kill @e[type=minecraft:item,x={left},y=94,z=-16,dx={right-left},dy=18,dz=32]")
            # The final camera follows the fully passed front wagon past x=192.
            connection.request(f"execute in {destination} run fill 193 96 -16 208 110 16 minecraft:air")
            connection.request(f"execute in {destination} run fill 193 96 -16 208 96 16 minecraft:stone")
            connection.request(f"execute in {destination} run kill @e[type=minecraft:item,x=136,y=94,z=-16,dx=72,dy=18,dz=32]")
            if aether:
                # This dimension has islands above the fixture. Expose the test
                # wagons to actual Aether skylight rather than an accidental roof.
                connection.request(f"execute in {destination} run fill 136 111 -16 208 255 16 minecraft:air")
            # Deliberately combine open Overworld skylight with a Nether room
            # that has no sky but does have real block light next to the wagons.
            # A black projected wagon can no longer pass merely because the
            # destination happens to be unlit.
            for dimension, left, right in (("minecraft:overworld", 48, 100), (destination, 136, 208)):
                for side in (-3, 3):
                    # Native water-bucket generation requires an empty frame
                    # interior. Keep the lamp rows outside its actual block plane.
                    plane = 80 if dimension == "minecraft:overworld" else 160
                    ranges = ((left, plane - 1), (plane + 1, right)) if aether else ((left, right),)
                    for start_x, end_x in ranges:
                        connection.request(f"execute in {dimension} run fill {start_x} 101 {side} {end_x} 101 {side} minecraft:glowstone")
            connection.request("gamerule doTileDrops true")
            processes["client"] = subprocess.Popen(client_args, cwd=ROOT, stdout=client_log, stderr=subprocess.STDOUT)
            ready = wait_until(lambda: load_json(client_output / "ready.json"), processes, 300,
                               "waiting for isolated client to join the server")
            player = ready["player"]
            assert re.fullmatch(r"[A-Za-z0-9_]{1,16}", player), player
            result["player"] = player
            connection.request(f"gamemode spectator {player}")
            source_x, target_x = 80.0, 160.0
            if aether:
                prepared = connection.request("execute in minecraft:overworld run teutonlib_train_visual aether-prepare")
                assert prepared.startswith(("PREPARING", "READY")), prepared

                def native_aether_ready():
                    state = connection.request("teutonlib_train_visual aether-status")
                    assert not state.startswith("FAIL"), state
                    return state if state.startswith("READY") else None

                evidence = wait_until(native_aether_ready, processes, 90, "generating native water-bucket Aether portals and rail links")
                evidence = json.loads(evidence[evidence.index("{"):])
                verify_native_aether_evidence(evidence)
                result["aetherPortalEvidence"] = evidence
                source_x, target_x = evidence["sourceX"], evidence["targetX"]
            start = connection.request("execute in minecraft:overworld run teutonlib_train_visual start")
            assert "FAIL" not in start.upper(), start

            def stationary():
                status = connection.request("teutonlib_train_visual status")
                assert "FAIL" not in status.upper(), status
                return status if status.startswith("READY") else None

            status = wait_until(stationary, processes, 45, "waiting for visual fixture initialization")
            match = re.search(r"ships=([0-9a-fA-F,-]+)", start)
            assert match, f"Fixture did not publish its ship UUIDs: {start}; {status}"
            ship_ids = match.group(1).split(",")
            assert len(ship_ids) == 2 and len(set(ship_ids)) == 2, ship_ids
            result["ships"] = ship_ids

            def step(distance):
                response = connection.request(f"teutonlib_train_visual step {distance}")
                assert "FAIL" not in response.upper(), response
                return wait_until(stationary, processes, 60, f"moving train by {distance} blocks")

            def capture(name, dimension, expectations):
                if dimension == "minecraft:overworld":
                    camera = "72 103 9 -138.4 17"
                elif name == "target-clear":
                    camera = "198 107 15 125 22"
                else:
                    camera = "168 103 9 138.4 17"
                position, rotation = " ".join(camera.split()[:3]), " ".join(camera.split()[3:])
                # Use IP's multi-world teleport; vanilla dimension changes deliberately
                # tear down every client world/session, unlike stepping through a portal.
                # IP 2.6.4 uses the command source's world even in its explicit
                # dimension overload. Select that world before invoking its teleport.
                response = connection.request(f"execute in {dimension} run portal tp {player} {position}")
                assert "Unknown" not in response and "Incorrect" not in response, response
                connection.request(f"execute in {dimension} run tp {player} {position} {rotation}")
                connection.request("tellraw " + player + " " + json.dumps({"text": "TEUTONLIB_VISUAL_CAPTURE " + name}))

                def captured():
                    report = load_json(client_output / "report.json")
                    if report and not report.get("passed"):
                        raise AssertionError(report)
                    return load_json(client_output / f"{name}.json")

                snapshot = wait_until(captured, processes, 60, f"capturing {name}")
                for ship_id, mode, owner_dimension in expectations:
                    verify_capture(snapshot, ship_id, dimension, mode, owner_dimension, len(ship_ids),
                                   "--verify-flywheel" in sys.argv[1:])
                verify_couplings(snapshot, source_x, target_x)
                if flywheel_backend is not None:
                    expected_backend = "InstancedDrawManager" if flywheel_backend == "instancing" else "IndirectDrawManager"
                    assert all(sample["backend"] == expected_backend for sample in snapshot.get("flywheelLighting", [])), \
                        f"Requested Flywheel backend was not used: {snapshot.get('flywheelLighting')}"
                checks.append(snapshot)
                print(f"PASS {name}: {snapshot['frameTimes']}", flush=True)

            source = "minecraft:overworld"
            front, rear = ship_ids
            capture("baseline", source, [(ship, "clear", source) for ship in ship_ids])
            if not any(flag in sys.argv[1:] for flag in ("--rider-only", "--seated-only", "--seated-rollback-only")):
                step(4)
                capture("source-before", source, [(front, "real", source), (rear, "clear", source)])
                capture("target-before", destination, [(front, "projected", source)])
                step(-4)
                capture("source-return", source, [(ship, "clear", source) for ship in ship_ids])
                step(15)
                capture("source-cross", source, [(front, "projected", destination), (rear, "real", source)])
                capture("target-cross", destination, [(front, "real", destination), (rear, "projected", source)])
                step(25)
                capture("target-clear", destination, [(ship, "clear", destination) for ship in ship_ids])
            connection.request("teutonlib_train_visual end")
            ride_cases = (("seat-rollback", "seated rollback", "seatedRollback"),) if "--seated-rollback-only" in sys.argv[1:] else (
                ("rider", "standing", "standingPlayer"), ("seated", "seated", "seatedPlayer"),
                ("seat-rollback", "seated rollback", "seatedRollback"))
            if "--render-only" in sys.argv[1:]:
                ride_cases = ()
            for command, kind, result_key in ride_cases:
                if "--seated-only" in sys.argv[1:] and command != "seated":
                    continue
                if "--standing-only" in sys.argv[1:] and command != "rider":
                    continue
                started = connection.request(
                    f"execute in minecraft:overworld run teutonlib_train_visual {command} {player}")
                assert "RUNNING" in started, started

                def rider_finished():
                    status = connection.request("teutonlib_train_visual status")
                    assert not status.upper().startswith("FAIL"), status
                    return status if status.startswith(f"PASS real player {kind}") else None

                result[result_key] = wait_until(rider_finished, processes, 90,
                    f"riding the train outbound and back with a real {kind} player")
                print(result[result_key], flush=True)
            connection.request("tellraw " + player + " " + json.dumps({"text": "TEUTONLIB_VISUAL_FINISH"}))
            processes["client"].wait(timeout=90)
            assert processes["client"].returncode == 0, "Client did not shut down normally"
            client_report = load_json(client_output / "report.json")
            assert client_report and client_report.get("passed"), client_report
            connection.request("stop")
            # The full rider fixture creates and retires several distant Sable
            # plots. Its normal final save can exceed 90 seconds on a busy disk.
            processes["server"].wait(timeout=180)
            assert processes["server"].returncode == 0, "Server did not shut down normally"
            for log_path in (report_dir / "server.log", report_dir / "client.log"):
                content = log_path.read_text(errors="replace")
                for failure in ("Mixin apply failed", "Mixin transformation of", "[CTF FAILSAFE]", "TWO LIVE CLAIMS ON ONE PLOT"):
                    assert failure not in content, f"{failure} in {log_path}"
            result["passed"] = True
            print(f"Train portal client verification PASS: {report_dir}", flush=True)
        except BaseException:
            result["failure"] = traceback.format_exc()
            raise
        finally:
            if connection is not None:
                if processes.get("client") and processes["client"].poll() is None:
                    try:
                        connection.request("tellraw @a " + json.dumps({"text": "TEUTONLIB_VISUAL_FINISH"}))
                    except (OSError, RuntimeError):
                        pass
                if processes.get("server") and processes["server"].poll() is None:
                    try:
                        connection.request("stop")
                    except (OSError, RuntimeError):
                        pass
                connection.close()
            for process in reversed(list(processes.values())):
                stop_process(process)
            (report_dir / "report.json").write_text(json.dumps(result, indent=2) + "\n")


if __name__ == "__main__":
    main()
