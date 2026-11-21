#!/usr/bin/env python3
"""Reproduce native station assembly in runClient's DEFAULT mod environment.

Usage: python3 tools/verify_station_assembly_client.py --world-metadata PATH/level.dat
Only the supplied generation metadata is copied into an isolated game directory;
no existing chunks, sublevels, configs or world are opened. Requires a local display.
The opt-in testmod clicks Create's actual AssemblyScreen button, verifies native
no_controls rejection and restoration, then adds controls and assembles a train.
--trace records Java callers immediately before invalid native lifecycle calls.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import secrets
import signal
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--world-metadata', required=True, type=Path)
    parser.add_argument('--trace', action='store_true')
    args = parser.parse_args()
    metadata = args.world_metadata.resolve(strict=True)
    run_id = secrets.token_hex(6)
    report = ROOT / 'build/reports/station-assembly-client' / run_id
    game = ROOT / 'run/smoke/station-assembly-client' / run_id
    report.mkdir(parents=True)
    (game / 'saves/Repro').mkdir(parents=True)
    (game / 'config').mkdir()
    shutil.copy2(metadata, game / 'saves/Repro/level.dat')
    (game / 'options.txt').write_text(
        'onboardAccessibility:false\npauseOnLostFocus:false\nfullscreen:false\n'
        'enableVsync:false\nrenderDistance:4\nsimulationDistance:5\nmaxFps:60\n')
    (game / 'config/fml.toml').write_text('earlyWindowControl = false\n')
    init = report / 'isolated.gradle'
    init.write_text('''allprojects { afterEvaluate {
    neoForge.runs.getByName('client').with {
        gameDirectory = file(%s)
        systemProperty('teutonlib.stationAssemblyRepro', 'true')
        systemProperty('teutonlib.stationAssemblyTrace', %s)
        programArgument('--quickPlaySingleplayer'); programArgument('Repro')
    }
} }
''' % (json.dumps(str(game)), json.dumps(str(args.trace).lower())))
    command = ['./gradlew', 'runClient', '-PstationAssemblyTests', '-I', str(init), '--offline', '--console=plain']
    result = {'passed': False, 'command': command, 'gameDirectory': str(game),
              'metadataSha256': hashlib.sha256(metadata.read_bytes()).hexdigest(),
              'runtime': 'default csoRuntime=all, no trainPortalTests or extra runtime mods'}
    process = None
    try:
        with (report / 'console.log').open('w') as log:
            process = subprocess.Popen(command, cwd=ROOT, env={**os.environ, 'RUST_BACKTRACE': 'full'},
                                       stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            deadline = time.monotonic() + 240
            while process.poll() is None:
                if time.monotonic() > deadline:
                    raise TimeoutError('runClient reproduction exceeded 240 seconds')
                time.sleep(.5)
        result['exitCode'] = process.returncode
        assert process.returncode == 0, f'runClient failed with exit {process.returncode}; see {report}'
        final = (game / 'assembly-result.txt').read_text()
        assert final.startswith('PASS rejection and successful assembly'), final
        result.update(passed=True, checks=final.splitlines())
    except BaseException as failure:
        result['failure'] = repr(failure)
        raise
    finally:
        if process and process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
        for name in ('logs', 'crash-reports', 'screenshots'):
            if (game / name).exists():
                shutil.copytree(game / name, report / name)
        for name in ('assembly-result.txt', 'assembly-rejection.txt'):
            if (game / name).exists():
                shutil.copy2(game / name, report / name)
        (report / 'result.json').write_text(json.dumps(result, indent=2))
        print(report, flush=True)


if __name__ == '__main__':
    main()
