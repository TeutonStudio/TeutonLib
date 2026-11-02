#!/usr/bin/env python3
"""Film a real connected docking platform crossing a train portal.

Each case owns a disposable world. The server verifies native connector locks,
transfers, persistent cargo, and body dimensions; the client records only the
Minecraft framebuffer with F3+B forced on for every captured frame.
"""

import argparse
import json
import secrets
import subprocess
import time
import traceback
from pathlib import Path

from verify_train_portal_client import ROOT, Rcon, available_ports, load_json, stop_process, wait_until


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case", choices=("wide", "narrow"), required=True)
    parser.add_argument("--check", action="store_true", help="Run the same native verification without a client or video")
    parser.add_argument("--rollback", action="store_true", help="Also verify native post-load rollback (requires --check --case wide)")
    args = parser.parse_args()
    if args.rollback and (not args.check or args.case != "wide"):
        parser.error("--rollback requires --check --case wide")
    run_id = time.strftime("%Y%m%d-%H%M%S") + "-" + args.case
    instance = ROOT / "run/smoke/train-docking-demo" / run_id
    server_dir, client_dir = instance / "server", instance / "client"
    report_dir = ROOT / "build/reports/train-docking-demo" / run_id
    for directory in (server_dir, client_dir, report_dir):
        directory.mkdir(parents=True, exist_ok=True)
    (report_dir.parent / "latest.txt").write_text(str(report_dir))
    (report_dir.parent / f"latest-{args.case}.txt").write_text(str(report_dir))
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
    # Avoid FML's separate loading-window handoff race on the desktop OpenGL driver.
    # This setting belongs only to this disposable recording client.
    (client_dir / "config").mkdir(exist_ok=True)
    (client_dir / "config/fml.toml").write_text("earlyWindowControl = false\n")
    init = report_dir / "client.gradle"
    init.write_text("""allprojects { afterEvaluate { neoForge.runs.getByName('client').with {
        gameDirectory = file(%s)
        systemProperty('teutonlib.trainPortalClientTests', 'true')
        systemProperty('teutonlib.trainDemo', 'true')
        systemProperty('teutonlib.trainDemoHitboxes', 'true')
        programArgument('--quickPlayMultiplayer')
        programArgument(%s)
    } } }
""" % (json.dumps(str(client_dir)), json.dumps(f"127.0.0.1:{game}")))
    common = ["-PcsoRuntime=all", "-PtrainPortalTests", "-PhybridTrainTests", "--offline", "--console=plain", "--no-daemon"]
    processes, rcon = {}, None
    clips, evidence = [], {}
    recording = client_dir / "demo-recording"

    def sleep(seconds):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if (recording / "failure.txt").exists():
                raise RuntimeError((recording / "failure.txt").read_text())
            for name, process in processes.items():
                if process.poll() is not None:
                    raise RuntimeError(f"{name} exited while waiting")
            time.sleep(.2)

    with (report_dir / "server.log").open("w") as server_log, (report_dir / "client.log").open("w") as client_log:
        try:
            processes["server"] = subprocess.Popen(
                ["./gradlew", "runSmokeServer", *common, f"-PperipheralTestDir={server_dir}"],
                cwd=ROOT, stdout=server_log, stderr=subprocess.STDOUT)
            wait_until(lambda: "Done (" in (report_dir / "server.log").read_text(errors="replace"), processes, 240, "server startup")
            rcon = Rcon(port, password)

            def command(text):
                answer = rcon.request(text)
                if any(word in answer for word in ("FAIL", "Unknown", "Incorrect", "unexpected error", "Check failed", "Index:")):
                    raise RuntimeError(f"{text}: {answer}")
                return answer

            def demo(action):
                answer = command("teutonlib_train_demo " + action)
                print(action, answer, flush=True)
                return answer

            def verify(stage):
                answer = json.loads(demo("dock-verify " + stage))
                status = json.loads(demo("dock-status"))
                assert answer[stage + "Verified"] and status[stage + "Verified"] and status["nativeTransfersVerified"], status
                assert status["case"] == args.case, status
                if stage == "source" or args.case == "wide":
                    assert status["docking"]["trainLocked"] and status["docking"]["platformLocked"], status
                    assert status["docking"]["validConstraints"] == 1, status
                else:
                    assert not status["docking"]["trainHasPeer"] and not status["docking"]["platformHasPeer"], status
                    assert status["docking"]["validConstraints"] == 0, status
                evidence[stage] = {"verification": answer, "status": status}
                (report_dir / "docking-evidence.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2))

            def settled():
                wait_until(lambda: "queued=0" in demo("status"), processes, 30, "building native blocks")

            for cmd in ["gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                        "gamerule commandModificationBlockLimit 1000000", "gamerule doTileDrops false", "time set 6000", "weather clear"]:
                command(cmd)
            for dimension, left, right, floor in [("minecraft:overworld", 20, 125, "smooth_stone"),
                                                   ("minecraft:the_nether", 136, 260, "polished_blackstone_bricks")]:
                command(f"execute in {dimension} run forceload add {left} -64 {right} 64")
                command(f"execute in {dimension} run fill {left} 99 -48 {right} 125 48 air")
                command(f"execute in {dimension} run fill {left} 99 -24 {right} 99 24 {floor}")
                for z in (-18, 18):
                    command(f"execute in {dimension} run fill {left} 99 {z} {right} 99 {z} sea_lantern")
            command("execute in minecraft:the_nether run fill 150 100 0 255 100 0 create:track[shape=xo]")
            for z in (-12, -5, 5, 12):
                command(f"execute in minecraft:the_nether run fill 136 108 {z} 260 108 {z} minecraft:light[level=15]")
            # The frame matches the actual aperture created by the native fixture.
            half_width = 15 if args.case == "wide" else 4.5
            for dimension, plane in [("minecraft:overworld", 100), ("minecraft:the_nether", 160)]:
                for z in (-int(half_width + .5), int(half_width + .5)):
                    command(f"execute in {dimension} run fill {plane} 99 {z} {plane} 113 {z} crying_obsidian")
                command(f"execute in {dimension} run fill {plane} 113 {-int(half_width + .5)} {plane} 113 {int(half_width + .5)} crying_obsidian")
            demo("prepare"); sleep(1); demo("enter"); sleep(.25)
            for offset in (1, 7, 17, 23):
                demo(f"bogey {offset}")
            demo("deck 0"); demo("deck 1"); settled()
            demo("machines"); settled(); demo("run"); sleep(3)
            demo("assemble"); sleep(3)
            if args.check:
                demo("dock " + args.case); sleep(4); verify("source")
                demo("route"); sleep(2); demo("verify")
                if args.rollback:
                    rollback = json.loads(demo("dock-rollback"))
                    assert rollback["rollbackVerified"], rollback
                    evidence["rollback"] = rollback
                    sleep(3); verify("source"); demo("verify")
                demo("move 77")
                wait_until(lambda: "travel=0.0 " in demo("status"), processes, 50, "crossing the native portal edge")
                sleep(3); demo("verify"); verify("target")
                (report_dir / "result.json").write_text(json.dumps({
                    "passed": True, "case": args.case, "serverOnly": True,
                    "rollbackTested": args.rollback,
                    "instance": str(instance), "evidence": evidence}, ensure_ascii=False, indent=2))
                command("stop")
                print(f"CHECK PASSED {report_dir}", flush=True)
                return

            processes["client"] = subprocess.Popen(["./gradlew", "runClient", *common, "-I", str(init)],
                                                    cwd=ROOT, stdout=client_log, stderr=subprocess.STDOUT)
            ready = wait_until(lambda: load_json(client_dir / "train-portal-verification/ready.json"), processes, 300, "client startup")
            player = ready["player"]
            command(f"gamemode spectator {player}")
            command(f"effect give {player} minecraft:night_vision infinite 0 true")

            def client(text):
                command("tellraw " + player + " " + json.dumps({"text": "TEUTONLIB_DEMO " + text}))

            def camera(dimension, start, end, look, duration):
                command(f"execute in {dimension} run portal tp {player} {' '.join(map(str, start))}")
                sleep(1)
                client("camera " + " ".join(map(str, (*start, *end, *look, duration))))
                sleep(.6)

            def graphical_check(name, dimension, expected_roots=2):
                command("tellraw " + player + " " + json.dumps({"text": "TEUTONLIB_VISUAL_CAPTURE " + name}))
                snapshot = wait_until(lambda: load_json(client_dir / f"train-portal-verification/{name}.json"), processes, 30, "checking native ContraptionLevel rendering")
                assert snapshot["dimension"] == dimension, snapshot
                roots = [sub for sub in snapshot["sublevels"] if not sub["removed"] and sub["shipDimension"] == dimension
                         and (sub.get("renderData") or "").endswith(".ContraptionLevel")]
                assert len(roots) == expected_roots, roots
                platform = evidence["target" if "target" in evidence else "source"]["status"]["platform"]
                if platform["dimension"] == dimension:
                    visible = [sub for sub in snapshot["sublevels"] if sub["ship"] == platform["uuid"]
                               and not sub["removed"] and sub["shipDimension"] == dimension]
                    assert len(visible) == 1 and visible[0].get("renderData"), visible
                    assert not visible[0]["renderData"].endswith(".ContraptionLevel"), visible
                (report_dir / f"{name}.json").write_text(json.dumps(snapshot, indent=2))

            def shot(name, title, subtitle, duration, action=None):
                print(f"RECORD {name}: {title}", flush=True)
                client("record " + name); sleep(.25)
                if action:
                    action()
                sleep(duration)
                client("snapshot " + name); client("stop")
                capture = wait_until(lambda: load_json(recording / f"{name}.json"), processes, 30, "encoding " + name)
                assert capture["continuousHitboxes"] and capture["capturedFrames"] > 0 and capture["framesWithoutHitboxes"] == 0, capture
                clips.append({"name": name, "title": title, "subtitle": subtitle, "capture": capture})
                (report_dir / "clips.json").write_text(json.dumps(clips, ensure_ascii=False, indent=2))

            source, destination = "minecraft:overworld", "minecraft:the_nether"
            camera(source, (54, 113, 30), (60, 112, 28), (56, 103, 4), 8)
            shot("01-zug", "Docking am ContraptionLevel-Zug", "2 Wagen · echte Sable-Physik · F3+B durchgehend aktiv", 5)
            camera(source, (48, 108, 17), (53, 108, 18), (49, 103, 5), 9)
            shot("02-andocken", "Mobile Plattform andocken", "Die nativen Docking Connectoren verbinden Wagen und separates Sublevel.", 5,
                 lambda: demo("dock " + args.case))
            sleep(1); verify("source")
            camera(source, (53, 106, 14), (55, 106, 14), (50, 103, 4), 7)
            shot("03-verriegelt", "Dock verriegelt · Transfers geprüft", "Native Verbindung und Übertragung geprüft; Ladung und Körper-UUIDs erfasst.", 6)
            demo("route"); sleep(2); demo("verify")
            graphical_check("docking-source", source)
            camera(source, (80, 115, 35), (87, 113, 34), (86, 104, 5), 11)
            shot("04-anfahrt", "Verbundene Plattform fährt mit", "Wagen und angedocktes Sublevel nähern sich gemeinsam dem Portal.", 10,
                 lambda: demo("move 28"))
            camera(source, (92, 112, 31), (96, 111, 30), (100, 104, 5), 9)
            shot("05-portal", "Großes Portal: gesamter Verbund passt" if args.case == "wide" else "Kleines Portal: Plattform passt nicht",
                 "Beide Docking-Partner werden gemeinsam übertragen." if args.case == "wide" else "Die Docking-Verbindung wird vor dem Wechsel getrennt.",
                 8, lambda: demo("move 18"))
            camera(destination, (180, 115, 36), (195, 113, 34), (187, 104, 5), 13)
            shot("06-ankunft", "Ankunft mit verbundener Plattform" if args.case == "wide" else "Zug kommt ohne die zu breite Plattform an",
                 "Der zweite Wagen folgt; der Docking-Verbund bleibt zusammen." if args.case == "wide" else "Beide Wagen wechseln; das abgetrennte Sublevel bleibt in der Ausgangswelt.",
                 12, lambda: demo("move 31"))
            wait_until(lambda: "travel=0.0 " in demo("status"), processes, 25, "settling the train after traversal")
            sleep(2); demo("verify"); verify("target")
            graphical_check("docking-target", destination)
            camera(destination, (188, 109, 20), (193, 108, 18), (188, 103, 5), 8)
            shot("07-dock-danach", "Dock wieder verriegelt und funktionsfähig" if args.case == "wide" else "Dock am Ziel ist sauber getrennt",
                 "Verriegelung, native Transfers und vollständige Ladung nach dem Wechsel geprüft." if args.case == "wide" else "Keine Verbindung über Dimensionen; die Ladung beider Partner ist erhalten.", 7)
            if args.case == "narrow":
                platform = evidence["target"]["status"]["platform"]
                assert platform["dimension"] == source, platform
                x, y, z = platform["center"]
                camera(source, (x - 7, y + 11, z + 19), (x - 3, y + 9, z + 16), (x, y, z), 9)
                graphical_check("docking-left-behind", source, expected_roots=0)
                shot("08-zurueckgeblieben", "Die Plattform bleibt in der Ausgangswelt", "Separates Sable-Sublevel und seine Ladung erhalten · Connector entriegelt", 8)
                camera(destination, (192, 116, 33), (198, 114, 31), (196, 104, 2), 7)
            else:
                camera(destination, (191, 117, 36), (198, 115, 33), (196, 104, 5), 7)
            shot("09-ergebnis", "Test bestanden: gemeinsamer Portalwechsel" if args.case == "wide" else "Test bestanden: Trennung bei zu kleinem Portal",
                 "Native Prüfungen bestanden · identische Körper-UUIDs · F3+B in jedem Aufnahmeframe", 6)
            client("quit"); command("stop")
            (report_dir / "result.json").write_text(json.dumps({
                "passed": True, "case": args.case, "continuousHitboxes": True,
                "recording": str(recording), "instance": str(instance),
                "evidence": evidence, "clips": clips}, ensure_ascii=False, indent=2))
            print(f"DONE {report_dir}", flush=True)
        except BaseException as error:
            failure = {"passed": False, "case": args.case, "serverOnly": args.check,
                       "rollbackTested": args.rollback,
                       "instance": str(instance), "report": str(report_dir),
                       "exception": repr(error), "traceback": traceback.format_exc(),
                       "evidence": evidence, "clips": clips}
            if rcon:
                try:
                    response = rcon.request("teutonlib_train_demo dock-status")
                    print("FAILURE DOCKING STATUS", response, flush=True)
                    try:
                        failure["dockingStatus"] = json.loads(response)
                        (report_dir / "failure-docking-status.json").write_text(
                            json.dumps(failure["dockingStatus"], ensure_ascii=False, indent=2))
                    except json.JSONDecodeError:
                        failure["dockingStatusResponse"] = response
                except (OSError, RuntimeError) as diagnostic_error:
                    failure["dockingStatusError"] = repr(diagnostic_error)
            (report_dir / "result.json").write_text(json.dumps(failure, ensure_ascii=False, indent=2))
            print(f"FAILED {report_dir}: {error!r}", flush=True)
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


if __name__ == "__main__":
    main()
