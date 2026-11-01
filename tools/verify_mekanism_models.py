#!/usr/bin/env python3
"""Compare native Mekanism pipe modes and rendered models before/after CTF assembly.

Uses disposable local server/client directories and original Mekanism, Sodium,
Sable and Create:Mekanism Compatibility binaries. Never opens personal worlds.
"""
from pathlib import Path
import json
import re
import secrets
import subprocess
import time
import traceback

from verify_train_portal_client import Rcon, available_ports, load_json, stop_process, wait_until


ROOT = Path(__file__).resolve().parents[1]


def verify_capture(snapshot, mounted, swapped):
    assert Path(snapshot["screenshot"]).is_file(), snapshot
    for name, reversed_modes in (("world", False), ("mounted", swapped)):
        tile = snapshot[name]
        expected = {"west": "PUSH" if reversed_modes else "PULL",
                    "east": "PULL" if reversed_modes else "PUSH"}
        for key in ("raw", "effective", "tileModel", "managerModel"):
            assert tile[key] == expected, f"{name} {key}: {tile[key]} != {expected}"
        assert tile["dataMatches"], f"{name} tile/manager/region model data diverged: {tile}"
    if mounted:
        probe = snapshot["mounted"]["probe"]
        for key in ("actualCompileObserved", "actualCompileDataMatches", "actualCompileQuadsMatch",
                    "actualNonemptyUnculledObserved", "actualSpecialEndGeometryMatches"):
            assert probe[key], f"Mounted {key} failed: {probe}"
        assert probe["actualUnculledCutoutCount"] == 44, probe
        if snapshot["name"] in ("swapped", "moving"):
            expected = {"WEST": "PUSH" if swapped else "PULL", "EAST": "PULL" if swapped else "PUSH"}
            addressed = [packet for packet in probe["connectionPackets"]
                         if packet["position"] == snapshot["mounted"]["position"]
                         and all(packet["payloadModes"].get(side) == mode for side, mode in expected.items())]
            assert any(any(tile["position"] == snapshot["mounted"]["position"]
                           for tile in packet["appliedTiles"]) for packet in addressed), \
                "No mode-change packet applied directly to the stable mounted pipe address"
    assert snapshot["passed"], snapshot


def main():
    run_id = time.strftime("%Y%m%d-%H%M%S") + "-" + secrets.token_hex(4)
    instance = ROOT / "run/smoke/mekanism-models" / run_id
    server_dir, client_dir = instance / "server", instance / "client"
    report_dir = ROOT / "build/reports/mekanism-models" / run_id
    for path in (server_dir, client_dir, report_dir):
        path.mkdir(parents=True, exist_ok=True)
    (client_dir / "config").mkdir()
    (client_dir / "config/fml.toml").write_text("earlyWindowControl=false\n")
    game_port, rcon_port = available_ports()
    password = secrets.token_hex(16)
    (server_dir / "eula.txt").write_text("eula=true\n")
    (server_dir / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={game_port}\nonline-mode=false\n"
        "level-name=mekanism-model-verification\nlevel-type=minecraft:flat\n"
        "generate-structures=false\nview-distance=8\nsimulation-distance=4\n"
        "spawn-protection=0\ndifficulty=peaceful\nallow-flight=true\n"
        f"enable-rcon=true\nrcon.port={rcon_port}\nrcon.password={password}\n"
        "broadcast-rcon-to-ops=false\n")
    (client_dir / "options.txt").write_text(
        "onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\n"
        "fullscreen:false\nenableVsync:false\nrenderDistance:8\nsimulationDistance:5\nmaxFps:120\nfov:0.0\n")
    init = report_dir / "isolated-client.gradle"
    init.write_text("""allprojects {
    afterEvaluate {
        neoForge.runs.getByName('client').with {
            gameDirectory = file(%s)
            systemProperty('teutonlib.mekanismModelClientTests', 'true')
            programArgument('--quickPlayMultiplayer')
            programArgument(%s)
        }
    }
}
""" % (json.dumps(str(client_dir)), json.dumps(f"127.0.0.1:{game_port}")))
    common = ["-PcsoRuntime=all", "-PmekanismModelTests", "--offline", "--console=plain", "--no-daemon"]
    server_args = ["./gradlew", "runSmokeServer", *common, f"-PperipheralTestDir={server_dir}"]
    client_args = ["./gradlew", "runClient", *common, "-I", str(init)]
    processes = {}
    connection = None
    captures = []
    result = {"passed": False, "instance": str(instance), "captures": captures}
    output = client_dir / "mekanism-model-verification"
    with (report_dir / "server.log").open("w") as server_log, (report_dir / "client.log").open("w") as client_log:
        try:
            processes["server"] = subprocess.Popen(server_args, cwd=ROOT, stdout=server_log, stderr=subprocess.STDOUT)
            wait_until(lambda: "Done (" in (report_dir / "server.log").read_text(errors="replace"),
                       processes, 240, "starting the isolated Mekanism test server")
            connection = Rcon(rcon_port, password)
            for command in ("gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                            "time set 6000", "weather clear", "forceload add 0 0 112 64",
                            "fill 16 96 16 48 112 48 minecraft:air", "fill 64 96 16 96 112 48 minecraft:air",
                            "fill 16 96 16 48 96 48 minecraft:stone", "fill 64 96 16 96 96 48 minecraft:stone"):
                connection.request(command)
            processes["client"] = subprocess.Popen(client_args, cwd=ROOT, stdout=client_log, stderr=subprocess.STDOUT)
            ready = wait_until(lambda: load_json(output / "ready.json"), processes, 300,
                               "waiting for the isolated client to join")
            player = ready["player"]
            assert re.fullmatch(r"[A-Za-z0-9_]{1,16}", player), player
            result["player"] = player
            connection.request(f"gamemode spectator {player}")
            # Establish tracking of both fixture chunks before emitting mode packets.
            connection.request(f"tp {player} 56 104 40 180 20")
            time.sleep(2.0)
            for command, stage, name in (("start", "WORLD_READY", "world"),
                                          ("convert", "CONVERT_READY", "mounted"),
                                          ("swap", "SWAP_READY", "swapped"),
                                          ("move-swap", "MOVING_READY", "moving")):
                response = connection.request("teutonlib_mekanism_models " + command)
                json.loads(response)

                def settled():
                    status = json.loads(connection.request("teutonlib_mekanism_models status"))
                    assert status["state"] != "FAIL", status
                    return status if status.get("stage") == stage and status["state"] == "READY" else None

                status = wait_until(settled, processes, 45, f"settling fixture {name}")
                camera = status["camera"]
                connection.request(f"tp {player} " + " ".join(map(str, camera)))
                connection.request("tellraw " + player + " " + json.dumps({"text": "TEUTONLIB_MEKANISM_CAPTURE " + name}))

                def captured():
                    final = load_json(output / "report.json")
                    if final and final.get("failure"):
                        raise AssertionError(final)
                    return load_json(output / f"{name}.json")

                snapshot = wait_until(captured, processes, 60, f"capturing Mekanism {name}")
                captures.append(snapshot)
                verify_capture(snapshot, command != "start", command == "swap")
                print(f"PASS {name}: {snapshot['mounted']['effective']}", flush=True)
            assert captures[2]["mounted"]["actualCompile"]["compiles"] > captures[1]["mounted"]["actualCompile"]["compiles"], \
                "Live Configurator change did not rebuild the mounted pipe section"
            assert captures[2]["mounted"]["probe"]["actualUnculledCutoutFingerprint"] != \
                captures[1]["mounted"]["probe"]["actualUnculledCutoutFingerprint"], \
                "Live Configurator swap did not change the mounted pipe geometry"
            assert captures[3]["mounted"]["probe"]["actualUnculledCutoutFingerprint"] == \
                captures[1]["mounted"]["probe"]["actualUnculledCutoutFingerprint"], \
                "Moving Configurator change did not restore the expected pipe geometry"
            assert captures[3]["mounted"]["probe"]["connectionPacketCount"] > \
                captures[2]["mounted"]["probe"]["connectionPacketCount"], \
                "Moving Configurator change did not deliver a new mode packet"
            connection.request("tellraw " + player + " " + json.dumps({"text": "TEUTONLIB_MEKANISM_FINISH"}))
            processes["client"].wait(timeout=90)
            assert processes["client"].returncode == 0
            client_report = load_json(output / "report.json")
            assert client_report and client_report.get("passed"), client_report
            connection.request("stop")
            processes["server"].wait(timeout=90)
            assert processes["server"].returncode == 0
            result["passed"] = True
            print(f"Mekanism model verification PASS: {report_dir}", flush=True)
        except BaseException:
            result["failure"] = traceback.format_exc()
            raise
        finally:
            if connection is not None:
                try:
                    connection.request("tellraw @a " + json.dumps({"text": "TEUTONLIB_MEKANISM_FINISH"}))
                    connection.request("stop")
                except (OSError, RuntimeError):
                    pass
                connection.close()
            for process in reversed(list(processes.values())):
                stop_process(process)
            (report_dir / "report.json").write_text(json.dumps(result, indent=2) + "\n")


if __name__ == "__main__":
    main()
