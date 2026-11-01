#!/usr/bin/env python3
"""Exercise New Age motor settings with native Create UI and moving Sable blocks.

Uses disposable localhost server/client worlds and the opt-in energy test mod.
Requires the local optional mod binaries and an OpenGL display.
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
    instance = ROOT / "run/smoke/motor-speed" / run_id
    server_dir, client_dir = instance / "server", instance / "client"
    report_dir = ROOT / "build/reports/motor-speed" / run_id
    for directory in (server_dir, client_dir, report_dir):
        directory.mkdir(parents=True, exist_ok=True)
    game_port, rcon_port = available_ports()
    password = secrets.token_hex(16)
    (server_dir / "eula.txt").write_text("eula=true\n")
    (server_dir / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={game_port}\nonline-mode=false\n"
        "level-name=motor-speed-verification\nlevel-type=minecraft:flat\n"
        "generate-structures=false\nview-distance=6\nsimulation-distance=4\n"
        "spawn-protection=0\ndifficulty=peaceful\nallow-flight=true\n"
        f"enable-rcon=true\nrcon.port={rcon_port}\nrcon.password={password}\n"
        "broadcast-rcon-to-ops=false\n")
    (client_dir / "options.txt").write_text(
        "onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\n"
        "fullscreen:false\nenableVsync:false\nrenderDistance:6\nsimulationDistance:4\nmaxFps:60\n")
    (client_dir / "config").mkdir()
    (client_dir / "config/fml.toml").write_text("earlyWindowControl=false\n")
    init = report_dir / "isolated-client.gradle"
    init.write_text("""allprojects {
    afterEvaluate {
        neoForge.runs.getByName('client').with {
            gameDirectory = file(%s)
            systemProperty('teutonlib.motorSpeedClientTests', 'true')
            programArgument('--quickPlayMultiplayer')
            programArgument(%s)
        }
    }
}
""" % (json.dumps(str(client_dir)), json.dumps(f"127.0.0.1:{game_port}")))
    common = ["-PcsoRuntime=all", "-PmotorEnergyTests", "-PtrainPortalTests",
              "--offline", "--console=plain", "--no-daemon"]
    server_args = ["./gradlew", "runSmokeServer", *common, f"-PperipheralTestDir={server_dir}"]
    client_args = ["./gradlew", "runClient", *common, "-I", str(init)]
    processes = {}
    connection = None
    captures = []
    result = {"passed": False, "instance": str(instance), "captures": captures}
    output = client_dir / "motor-speed-verification"
    environment = dict(os.environ)
    environment.setdefault("DISPLAY", ":0")
    with (report_dir / "server.log").open("w") as server_log, (report_dir / "client.log").open("w") as client_log:
        try:
            processes["server"] = subprocess.Popen(server_args, cwd=ROOT, env=environment,
                                                    stdout=server_log, stderr=subprocess.STDOUT)
            wait_until(lambda: "Done (" in (report_dir / "server.log").read_text(errors="replace"),
                       processes, 240, "starting motor test server")
            connection = Rcon(rcon_port, password)
            for command in ("gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                            "time set 6000", "weather clear", "gamerule doTileDrops false",
                            "forceload add 0 0 112 64"):
                connection.request(command)
            packet_test = connection.request("teutonlib_energy_test speed")
            result["packetTest"] = packet_test
            print(packet_test, flush=True)
            processes["client"] = subprocess.Popen(client_args, cwd=ROOT, env=environment,
                                                    stdout=client_log, stderr=subprocess.STDOUT)
            ready = wait_until(lambda: load_json(output / "ready.json"), processes, 300,
                               "joining motor test client")
            player = ready["player"]
            assert re.fullmatch(r"[A-Za-z0-9_]{1,16}", player), player
            connection.request(f"gamemode creative {player}")
            connection.request(f"give {player} create:wrench")
            connection.request(f"tp {player} 8 221 11 180 30")
            for index, action in enumerate(("world", "assemble", "move")):
                state_text = connection.request(f"teutonlib_energy_test speedFixture {action}")
                state = json.loads(state_text[state_text.index("{"):])
                target = state.get("target", [state.get("x"), state.get("y"), state.get("z")])
                connection.request(f"tp {player} {state['worldX']} {state['worldY'] + 2.5} {state['worldZ'] + 2}")
                instruction = {"stage": action, "target": target, "side": "UP",
                               "desiredValue": 32 + index * 8, "row": index % 2}
                pending = output / "instruction.pending.json"
                pending.write_text(json.dumps(instruction))
                pending.replace(output / "instruction.json")
                capture = wait_until(lambda: load_json(output / f"{action}.json"), processes, 60,
                                     f"opening motor speed selection ({action})")
                captures.append(capture)
                assert capture.get("passed"), capture
                status_text = connection.request("teutonlib_energy_test speedFixture status")
                status = json.loads(status_text[status_text.index("{"):])
                expected = instruction["desiredValue"] * (-1 if instruction["row"] == 0 else 1)
                capture["serverState"] = status
                assert status["speed"] == expected, status
                print(f"{action}: selection saved, server speed={status['speed']}", flush=True)
            assert "PASS" in packet_test, packet_test
            result["passed"] = True
        except BaseException:
            result["error"] = traceback.format_exc()
            print(result["error"], flush=True)
        finally:
            client = processes.get("client")
            if client and client.poll() is None and output.is_dir():
                pending = output / "instruction.pending.json"
                pending.write_text(json.dumps({"stage": "finish", "finish": True, "stop": True}))
                pending.replace(output / "instruction.json")
                try:
                    client.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    pass
            if connection:
                try:
                    connection.request("teutonlib_energy_test speedFixture clear")
                    connection.request("stop")
                except Exception:
                    pass
                connection.close()
            for process in processes.values():
                stop_process(process)
            (report_dir / "report.json").write_text(json.dumps(result, indent=2))
            print(f"Report: {report_dir / 'report.json'}", flush=True)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
