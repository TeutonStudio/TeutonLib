#!/usr/bin/env python3
"""Verify server-controlled live hose modes and their real client rendering.

Starts disposable local server/client instances on the hardware OpenGL display.
Never opens personal saves. Run outside the filesystem/network sandbox.
"""

from pathlib import Path
import json
import os
import re
import secrets
import subprocess
import time
import traceback

from verify_train_portal_client import Rcon, available_ports, load_json, stop_process, wait_until


ROOT = Path(__file__).resolve().parents[1]


def main():
    run_id = time.strftime("%Y%m%d-%H%M%S") + "-" + secrets.token_hex(4)
    instance = ROOT / "run/smoke/hose-pulley-client" / run_id
    server_dir, client_dir = instance / "server", instance / "client"
    report_dir = ROOT / "build/reports/hose-pulley-client" / run_id
    for directory in (server_dir, client_dir, report_dir):
        directory.mkdir(parents=True, exist_ok=True)
    game_port, rcon_port = available_ports()
    password = secrets.token_hex(16)
    (server_dir / "eula.txt").write_text("eula=true\n")
    (server_dir / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={game_port}\nonline-mode=false\n"
        "level-name=hose-visual-verification-world\nlevel-type=minecraft:flat\n"
        "generate-structures=false\nview-distance=6\nsimulation-distance=4\n"
        "spawn-protection=0\ndifficulty=peaceful\nallow-flight=true\n"
        f"enable-rcon=true\nrcon.port={rcon_port}\nrcon.password={password}\n"
        "broadcast-rcon-to-ops=false\n"
    )
    (client_dir / "options.txt").write_text(
        "onboardAccessibility:false\nskipMultiplayerWarning:true\n"
        "pauseOnLostFocus:false\nfullscreen:false\nenableVsync:false\n"
        "renderDistance:8\nsimulationDistance:5\nmaxFps:60\nfov:0.0\n"
    )
    (client_dir / "config").mkdir()
    (client_dir / "config/fml.toml").write_text("earlyWindowControl = false\n")
    init_path = report_dir / "isolated-client.gradle"
    init_path.write_text("""allprojects {
    afterEvaluate {
        neoForge.runs.getByName('client').with {
            gameDirectory = file(%s)
            systemProperty('teutonlib.hosePulleyClientTests', 'true')
            programArgument('--quickPlayMultiplayer')
            programArgument(%s)
        }
    }
}
""" % (json.dumps(str(client_dir)), json.dumps(f"127.0.0.1:{game_port}")))
    common = ["-PcsoRuntime=all", "-PhoseTests", "--offline", "--console=plain", "--no-daemon"]
    server_args = ["./gradlew", "runSmokeServer", *common, f"-PperipheralTestDir={server_dir}"]
    client_args = ["./gradlew", "runClient", *common, "-I", str(init_path)]
    environment = dict(os.environ)
    environment.setdefault("DISPLAY", ":0")
    processes = {}
    connection = None
    result = {"passed": False, "instance": str(instance), "reportDirectory": str(report_dir),
              "limits": "Both client paths are transformed. The selected backend is checked through actual BER draws or existing Flywheel magnet/rope instances; only that backend executes native draws."}
    captures = []
    result["captures"] = captures
    output = client_dir / "hose-pulley-verification"
    with (report_dir / "server.log").open("w") as server_log, (report_dir / "client.log").open("w") as client_log:
        try:
            processes["server"] = subprocess.Popen(server_args, cwd=ROOT, env=environment,
                                                    stdout=server_log, stderr=subprocess.STDOUT)
            wait_until(lambda: "Done (" in (report_dir / "server.log").read_text(errors="replace"),
                       processes, 240, "waiting for disposable hose server")
            connection = Rcon(rcon_port, password)
            for command in ("gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                            "time set 6000", "weather clear", "gamerule doTileDrops false",
                            "forceload add 0 0 16 16",
                            "fill 0 210 0 16 223 16 minecraft:air",
                            "fill 0 210 0 16 210 16 minecraft:smooth_stone",
                            "fill 0 211 4 16 222 4 minecraft:white_concrete"):
                connection.request(command)
            fixture = connection.request("teutonlib_hose_test checkpoint")
            assert fixture.startswith("PASS hose checkpoint"), fixture
            result["fixture"] = fixture
            processes["client"] = subprocess.Popen(client_args, cwd=ROOT, env=environment,
                                                    stdout=client_log, stderr=subprocess.STDOUT)

            def ready_or_failure():
                failed = load_json(output / "report.json")
                assert not failed or failed.get("passed"), failed
                return load_json(output / "ready.json")

            ready = wait_until(ready_or_failure, processes, 300, "waiting for isolated hose client")
            player = ready["player"]
            assert re.fullmatch(r"[A-Za-z0-9_]{1,16}", player), player
            assert len(ready["appliedMixins"]) == 2 and all(ready["appliedMixins"].values()), ready
            result["player"] = player
            connection.request(f"gamemode spectator {player}")
            connection.request(f"tp {player} 8.5 216 17 180 -8")
            for command, mode, physical, name in (
                    ("mode-always", "ALWAYS", True, "always-initial"),
                    ("mode-never", "NEVER", False, "never"),
                    ("mode-always", "ALWAYS", True, "always-restored"),
                    ("mode-sable-only", "SABLE_ONLY", False, "sable-only-world"),
                    ("mode-always", "ALWAYS", True, "always-final")):
                response = connection.request("teutonlib_hose_test " + command)
                assert response.startswith("PASS hose mode="), response

                def mode_settled():
                    status = connection.request("teutonlib_hose_test mode-status")
                    match = re.fullmatch(r"PASS hose mode=([A-Z_]+) heads=(\d+)", status.strip())
                    assert match, status
                    return status if match[1] == mode and int(match[2]) == int(physical) else None

                wait_until(mode_settled, processes, 20, f"settling server hose mode {mode}")
                connection.request("tellraw " + player + " " + json.dumps({
                    "text": f"TEUTONLIB_HOSE_CAPTURE {name} {str(physical).lower()}"}))

                def captured():
                    final = load_json(output / "report.json")
                    assert not final or not final.get("failure"), final
                    return load_json(output / f"{name}.json")

                capture = wait_until(captured, processes, 90, f"capturing live hose mode {name}")
                assert capture.get("passed") and capture["physicalHeadEnabled"] == physical, capture
                assert capture["headCount"] == int(physical), capture
                assert (capture["headRenderInvocations"] > 0) == physical, capture
                assert Path(capture["screenshot"]).is_file(), capture
                native = capture["native"]
                if native["backend"] == "Flywheel":
                    assert native["sameVisual"] and native["magnetVisible"] != physical, capture
                    assert (native["ropeInstances"] > 0) != physical, capture
                    assert native["nativeBerDraws"] == 0, capture
                    magnet_state = native["magnetState"]
                    assert magnet_state["activeSlot"] != physical, capture
                    assert not physical or magnet_state["hidden"], capture
                    assert len(native["ropeStates"]) == native["ropeInstances"], capture
                    assert all(state["activeSlot"] for state in native["ropeStates"]), capture
                    for state in [magnet_state, *native["ropeStates"]]:
                        if "page" in state:
                            assert all(state[key] for key in
                                       ("pageRegistered", "slotValid", "sameInstance", "sameHandle")), capture
                else:
                    assert (native["nativeBerDraws"] > 0) != physical, capture
                captures.append(capture)
                print(f"PASS {name}: synced={physical}, heads={capture['headCount']}, backend={native['backend']}", flush=True)
            response = connection.request("teutonlib_hose_connector_test visual")
            assert response.startswith("PASS connector visual"), response
            connection.request("tellraw " + player + " " + json.dumps({"text": "TEUTONLIB_HOSE_CAPTURE connector-seated true"}))
            def seated_capture():
                final = load_json(output / "report.json")
                assert not final or not final.get("failure"), final
                return load_json(output / "connector-seated.json")
            seated = wait_until(seated_capture, processes, 90, "capturing seated hose connector")
            assert seated.get("passed") and seated.get("docked"), seated
            captures.append(seated)
            print("PASS connector-seated: synchronized socket/head, real block model and renderer", flush=True)
            visual_ids = [capture["native"]["visualIdentity"] for capture in captures
                          if capture["native"]["backend"] == "Flywheel"]
            assert not visual_ids or len(set(visual_ids)) == 1, "Mode change recreated the existing Flywheel visual"
            result["capture"] = captures[-1]
            connection.request("tellraw " + player + " " + json.dumps({"text": "TEUTONLIB_HOSE_FINISH"}))
            processes["client"].wait(timeout=90)
            assert processes["client"].returncode == 0, "Client did not shut down normally"
            client_report = load_json(output / "report.json")
            assert client_report and client_report.get("passed") and len(client_report["captures"]) == 6, client_report
            connection.request("stop")
            processes["server"].wait(timeout=90)
            assert processes["server"].returncode == 0, "Server did not shut down normally"
            for log_path in (report_dir / "server.log", report_dir / "client.log"):
                content = log_path.read_text(errors="replace")
                for failure in ("Mixin apply failed", "Mixin transformation of", "InjectionError"):
                    assert failure not in content, f"{failure} in {log_path}"
            result["passed"] = True
            print(f"Hose pulley client verification PASS: {report_dir}", flush=True)
            print(f"Screenshots: {len(captures)} live mode transitions", flush=True)
        except BaseException:
            result["failure"] = traceback.format_exc()
            raise
        finally:
            if connection is not None:
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
