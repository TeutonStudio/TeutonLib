#!/usr/bin/env python3
"""Film a genuine two-wagon Sable station build and Immersive Portals traversal.

Uses a disposable world and only records the Minecraft framebuffer. Requires the
cached optional mod verification profile and a working OpenGL desktop session.
"""
from pathlib import Path
import json
import secrets
import subprocess
import time
import sys
from verify_train_portal_client import ROOT, Rcon, available_ports, wait_until, load_json, stop_process


def main():
    continuous_hitboxes = "--f3b" in sys.argv
    run_id = time.strftime("%Y%m%d-%H%M%S")
    instance = ROOT / "run/smoke/train-demo" / run_id
    server_dir, client_dir = instance / "server", instance / "client"
    report_dir = ROOT / "build/reports/train-demo" / run_id
    for directory in (server_dir, client_dir, report_dir):
        directory.mkdir(parents=True, exist_ok=True)
    (ROOT / "build/reports/train-demo/latest.txt").write_text(str(report_dir))
    game, port = available_ports()
    password = secrets.token_hex(16)
    (server_dir / "eula.txt").write_text("eula=true\n")
    (server_dir / "server.properties").write_text(
        f"server-ip=127.0.0.1\nserver-port={game}\nonline-mode=false\n"
        "level-name=demo-world\nlevel-type=minecraft:flat\ngenerate-structures=false\n"
        "view-distance=8\nsimulation-distance=4\nspawn-protection=0\ndifficulty=peaceful\nallow-flight=true\n"
        f"enable-rcon=true\nrcon.port={port}\nrcon.password={password}\nbroadcast-rcon-to-ops=false\n")
    (client_dir / "options.txt").write_text(
        "onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\n"
        "fullscreen:false\nenableVsync:false\nrenderDistance:8\nsimulationDistance:5\nmaxFps:60\nfov:0.0\n")
    init = report_dir / "client.gradle"
    init.write_text("""allprojects { afterEvaluate { neoForge.runs.getByName('client').with {
        gameDirectory = file(%s)
        systemProperty('teutonlib.trainPortalClientTests', 'true')
        systemProperty('teutonlib.trainDemo', 'true')
        systemProperty('teutonlib.trainDemoHitboxes', '%s')
        programArgument('--quickPlayMultiplayer')
        programArgument(%s)
    } } }
""" % (json.dumps(str(client_dir)), str(continuous_hitboxes).lower(), json.dumps(f"127.0.0.1:{game}")))
    common = ["-PcsoRuntime=all", "-PtrainPortalTests", "-PhybridTrainTests", "--offline", "--console=plain", "--no-daemon"]
    processes, rcon = {}, None
    clips = []
    recording = client_dir / "demo-recording"
    def sleep(seconds):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if (recording / "failure.txt").exists():
                raise RuntimeError((recording / "failure.txt").read_text())
            time.sleep(.2)
    with (report_dir / "server.log").open("w") as server_log, (report_dir / "client.log").open("w") as client_log:
        try:
            processes["server"] = subprocess.Popen(["./gradlew", "runSmokeServer", *common, f"-PperipheralTestDir={server_dir}"], cwd=ROOT, stdout=server_log, stderr=subprocess.STDOUT)
            wait_until(lambda: "Done (" in (report_dir / "server.log").read_text(errors="replace"), processes, 240, "server startup")
            rcon = Rcon(port, password)
            def command(text):
                answer = rcon.request(text)
                if any(word in answer for word in ("FAIL", "Unknown", "Incorrect")):
                    print("COMMAND FAILED", text, answer, flush=True)
                    raise RuntimeError(f"{text}: {answer}")
                return answer
            for cmd in ["gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "gamerule commandModificationBlockLimit 1000000", "gamerule doTileDrops false", "time set 6000", "weather clear"]:
                command(cmd)
            for dimension, left, right, floor in [("minecraft:overworld", 20, 120, "smooth_stone"), ("minecraft:the_nether", 136, 260, "polished_blackstone_bricks")]:
                command(f"execute in {dimension} run forceload add {left} -64 {right} 64")
                command(f"execute in {dimension} run fill {left} 99 -48 {right} 125 48 air")
                command(f"execute in {dimension} run fill {left} 99 -24 {right} 99 24 {floor}")
                command(f"execute in {dimension} run fill {left} 99 -8 {right} 99 -8 sea_lantern")
                command(f"execute in {dimension} run fill {left} 99 8 {right} 99 8 sea_lantern")
            command("execute in minecraft:the_nether run fill 150 100 0 255 100 0 create:track[shape=xo]")
            # Vanilla light blocks illuminate the skyless demonstration platform.
            for z in (-5, 5):
                command(f"execute in minecraft:the_nether run fill 136 105 {z} 260 105 {z} minecraft:light[level=15]")
            # A broad portal border leaves all moving machinery enough clearance.
            for dimension, plane, material in [("minecraft:overworld", 100, "crying_obsidian"), ("minecraft:the_nether", 160, "crying_obsidian")]:
                for z in (-9, 9):
                    command(f"execute in {dimension} run fill {plane} 99 {z} {plane} 113 {z} {material}")
                command(f"execute in {dimension} run fill {plane} 113 -9 {plane} 113 9 {material}")
                for z in (-7, 7):
                    command(f"execute in {dimension} run fill {plane-12} 102 {z} {plane+12} 102 {z} sea_lantern")
            command("execute in minecraft:overworld run teutonlib_train_demo prepare")
            if "--check" in sys.argv:
                sleep(1)
                def check_demo(text):
                    answer = command("teutonlib_train_demo " + text)
                    print(text, answer, flush=True)
                    return answer
                check_demo("enter")
                for offset in (1, 7, 17, 23): check_demo(f"bogey {offset}")
                check_demo("deck 0"); check_demo("deck 1"); sleep(5)
                check_demo("machines"); sleep(2); check_demo("run"); sleep(3)
                check_demo("assemble"); sleep(3); check_demo("route"); sleep(2); check_demo("verify")
                check_demo("move 77"); sleep(27); check_demo("status"); check_demo("verify")
                command("stop")
                return
            processes["client"] = subprocess.Popen(["./gradlew", "runClient", *common, "-I", str(init)], cwd=ROOT, stdout=client_log, stderr=subprocess.STDOUT)
            ready = wait_until(lambda: load_json(client_dir / "train-portal-verification/ready.json"), processes, 300, "client startup")
            player = ready["player"]
            command(f"gamemode spectator {player}")
            command(f"effect give {player} minecraft:night_vision infinite 0 true")
            def client(text): command("tellraw " + player + " " + json.dumps({"text": "TEUTONLIB_DEMO " + text}))
            def graphical_check(name):
                client("inspect " + name)
                command("tellraw " + player + " " + json.dumps({"text": "TEUTONLIB_VISUAL_CAPTURE " + name}))
                snapshot = wait_until(lambda: load_json(client_dir / f"train-portal-verification/{name}.json"), processes, 30, "checking ContraptionLevel rendering")
                levels = [sub for sub in snapshot["sublevels"] if sub["renderData"].endswith(".ContraptionLevel")]
                assert len(levels) >= 2, levels
                inspected = load_json(recording / f"inspect-{name}.json")
                children = [entity for entity in inspected["entities"] if entity["type"].endswith(".ControlledContraptionEntity")]
                # The native embedding cache is scoped to the active render world;
                # it can be empty between a portal pass and the next frame.
                assert len(children) >= 2 and all(entity.get("sub") and entity["blocks"] > 0 for entity in children), inspected
                (report_dir / f"{name}.json").write_text(json.dumps(snapshot, indent=2))
            def camera(dimension, start, end, look, duration):
                command(f"execute in {dimension} run portal tp {player} {' '.join(map(str,start))}")
                sleep(1)
                client("camera " + " ".join(map(str, (*start, *end, *look, duration))))
                sleep(.6)
            def demo(action):
                result = command("teutonlib_train_demo " + action)
                print(action, result, flush=True)
                return result
            def settled():
                return wait_until(lambda: "queued=0" in demo("status"), processes, 30, "building native blocks")
            def shot(name, title, subtitle, duration, action=None):
                print(f"RECORD {name}: {title}", flush=True)
                client("record " + name)
                sleep(.25)
                if action: action()
                sleep(duration)
                client("snapshot " + name)
                client("stop")
                capture = wait_until(lambda: load_json(recording / f"{name}.json"), processes, 30, "encoding " + name)
                if continuous_hitboxes:
                    assert capture["continuousHitboxes"] and capture["capturedFrames"] > 0 and capture["framesWithoutHitboxes"] == 0, capture
                clips.append({"name": name, "title": title, "subtitle": subtitle})
                (report_dir / "clips.json").write_text(json.dumps(clips, ensure_ascii=False, indent=2))
            source, destination = "minecraft:overworld", "minecraft:the_nether"
            if "--inspect" in sys.argv:
                demo("enter")
                for offset in (1, 7, 17, 23): demo(f"bogey {offset}")
                demo("deck 0"); demo("deck 1"); sleep(5); demo("machines"); sleep(2); demo("run"); sleep(3)
                camera(source, (54,108,20), (54,108,20), (56,104,0), 5)
                client("snapshot before"); client("inspect before"); sleep(1)
                demo("assemble"); sleep(3); demo("route"); sleep(3); demo("verify")
                client("snapshot assembled"); client("inspect assembled"); sleep(1)
                demo("move 77"); sleep(27)
                camera(destination, (193,112,27), (193,112,27), (196,104,0), 5)
                sleep(3); demo("verify"); client("snapshot arrived"); client("inspect arrived"); sleep(2)
                client("quit"); command("stop"); print("INSPECT DONE", client_dir, flush=True)
                return
            camera(source, (53,112,25), (59,110,23), (56,102,0), 9)
            shot("01-station", "Zwei Wagen direkt auf Sable bauen", "Automatisierte Ingame-Demo · F3+B durchgehend aktiv" if continuous_hitboxes else "Automatisierte Ingame-Demo · echte Station, echte Block Entities", 4, lambda: demo("enter"))
            camera(source, (49,106,12), (53,106,13), (48,102,0), 8)
            def first():
                demo("bogey 1"); sleep(1.2); demo("bogey 7"); sleep(1.2); demo("deck 0")
            shot("02-wagen-eins", "Wagen 1 · zwei Drehgestelle", "Die Bauteile liegen bereits im ersten Sable-Sublevel.", 6, first)
            settled()
            camera(source, (64,108,16), (70,108,15), (63,102,0), 8)
            def second():
                demo("bogey 17"); sleep(1); demo("bogey 23"); sleep(1); demo("deck 1")
            shot("03-wagen-zwei", "Wagen 2 · eigenes Sable-Sublevel", "Das dritte Drehgestell beginnt den nächsten Wagen.", 6, second)
            settled()
            camera(source, (54,111,23), (61,110,24), (57,103,0), 8)
            shot("04-maschinen", "Die vier Geräte montieren", "Mechanisches Lager + Swivel Bearing · Radar + Schlauchrolle", 6, lambda: demo("machines"))
            settled(); demo("run"); sleep(3)
            camera(source, (51,107,11), (55,107,10), (49,104,0), 7)
            shot("05-lager", "Mechanisches Lager & Swivel Bearing", "Der Rotor dreht; die Truhe rotiert auf dem Swivel-Lager.", 7)
            camera(source, (68,108,12), (71,107,11), (65,105,1), 7)
            def hose_cycle():
                demo("hose 32"); sleep(2); demo("hose -32"); sleep(1.5); demo("hose 0")
            shot("06-radar-schlauch", "Radar & Schlauchrolle auf dem Bauzug", "Radar dreht · die Schlauchrolle fährt ihren physischen Kopf aus und ein.", 5, hose_cycle)
            camera(source, (54,112,25), (61,111,24), (56,103,0), 8)
            shot("07-contraption", "Zug bauen → ContraptionLevel", "Dieselben zwei Wagen wechseln in den Fahrmodus.", 6, lambda: demo("assemble"))
            demo("route"); sleep(3); demo("verify")
            graphical_check("demo-driving")
            client("bounds true")
            shot("08-rahmen", "ContraptionLevel im Fahrmodus", "Die orangefarbenen Grenzen zeigen die beiden Wagen.", 4)
            client("bounds " + str(continuous_hitboxes).lower())
            camera(source, (79,114,31), (86,112,29), (85,104,0), 11)
            shot("09-anfahrt", "Anfahrt zum Portal", "Zwei gekoppelte Wagen · laufende Geräte an Bord", 10, lambda: demo("move 28"))
            camera(source, (91,111,27), (95,110,25), (100,104,0), 9)
            shot("10-durchfahrt", "Durch das Immersive-Portal", "Der vordere Wagen erreicht die andere Dimension.", 8, lambda: demo("move 18"))
            camera(destination, (180,114,35), (195,113,33), (187,104,0), 13)
            shot("11-ankunft", "Beide Wagen kommen im Nether an", "Der hintere Wagen folgt durch dasselbe Portal.", 12, lambda: demo("move 31"))
            sleep(2); demo("verify")
            graphical_check("demo-arrived")
            camera(destination, (204,109,15), (209,108,14), (204,105,1), 8)
            shot("12-geraete-danach", "Auch nach der Portalfahrt in Betrieb", "Radar dreht weiter · die Schlauchrolle lässt sich weiterhin bewegen.", 5, hose_cycle)
            camera(destination, (185,108,13), (191,108,12), (187,104,0), 8)
            shot("13-lager-danach", "Beide Lager laufen weiter", "Mechanischer Rotor und Swivel-Truhe nach dem Dimensionswechsel.", 7)
            camera(destination, (191,117,34), (198,115,31), (196,104,0), 7)
            shot("14-abschluss", "Sable → ContraptionLevel → Portal", "2 Wagen · 4 Geräte · ein durchgängiger Zug", 6)
            client("quit")
            command("stop")
            (report_dir / "result.json").write_text(json.dumps({"passed": True, "continuousHitboxes": continuous_hitboxes, "recording": str(recording), "instance": str(instance), "clips": clips}, ensure_ascii=False, indent=2))
            print(f"DONE {report_dir}", flush=True)
        finally:
            if rcon:
                try: rcon.request("stop")
                except (OSError, RuntimeError): pass
                rcon.close()
            for proc in reversed(list(processes.values())): stop_process(proc)


if __name__ == "__main__": main()
