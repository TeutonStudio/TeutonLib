#!/usr/bin/env python3
"""Real Create networks in a disposable server: native rate, shared hose limit and reverse flow."""
from pathlib import Path
import json
import secrets
import subprocess
import time
from verify_train_portal_client import Rcon, available_ports, stop_process, wait_until

ROOT = Path(__file__).resolve().parents[1]

def main():
    instance = ROOT / 'run/smoke/hose-connector' / secrets.token_hex(8)
    report = ROOT / 'build/reports/hose-connector'
    instance.mkdir(parents=True)
    report.mkdir(parents=True, exist_ok=True)
    (report / 'report.json').unlink(missing_ok=True)
    _, port = available_ports()
    password = secrets.token_hex(16)
    (instance / 'eula.txt').write_text('eula=true\n')
    (instance / 'server.properties').write_text(
        'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n'
        'level-type=minecraft:flat\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\n'
        f'enable-rcon=true\nrcon.port={port}\nrcon.password={password}\n')
    process = connection = None
    checks = []
    try:
        with (report / 'server.log').open('w') as log:
            process = subprocess.Popen(['./gradlew','runSmokeServer','-PcsoRuntime=all','-PhoseTests',
                f'-PperipheralTestDir={instance}','--offline','--console=plain'], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
            wait_until(lambda: 'Done (' in (report / 'server.log').read_text(errors='replace'), {'server':process}, 240, 'starting connector fixture')
            connection = Rcon(port, password)
            def command(action):
                value = connection.request('teutonlib_hose_connector_test ' + action)
                print(value, flush=True)
                assert value.startswith('PASS connector'), value
                return value
            def sample():
                raw = command('sample').split(' ',3)[3]
                return [list(map(int, entry.split('=')[1].split(','))) for entry in raw.split(';')]
            connection.request('forceload add 0 48 31 143')
            time.sleep(1)
            command('prepare')
            time.sleep(1)
            command('dock-all')
            time.sleep(6)
            command('status')
            command('validate')
            a = sample()
            time.sleep(3)
            b = sample()
            delta = [sum(y[1:])-sum(x[1:]) for x,y in zip(a,b)]
            assert delta[0] > 0, ('native pump did not transfer', delta)
            assert all(d > 0 for d in delta), ('hose did not transfer', delta)
            for d in delta[1:]:
                assert abs(d / delta[0] - .5) < .06, ('expected shared half throughput', delta)
            for lane in b:
                assert sum(lane) == 8000, ('fluid was lost or duplicated', b)
            checks.append({'halfFlow': delta, 'massBalance': b})
            command('undock')
            a=sample();time.sleep(1);b=sample()
            assert b[1] == a[1], ('undocked hose transferred',a,b)
            command('dock')
            time.sleep(2)
            command('reverse')
            a=sample();time.sleep(3);b=sample()
            assert all(y[0] > x[0] for x,y in zip(a,b)), ('reverse transfer failed',a,b)
            checks.append({'reverse': [y[0]-x[0] for x,y in zip(a,b)]})
            command('stop')
            time.sleep(1)
            a=sample();time.sleep(1);b=sample()
            assert a == b, ('unpowered network transferred',a,b)
            checks.append('undock closes immediately; reverse flow; no flow without pump')
            checks.append(command('lifecycle'))
            checks.append(command('physics'))
            command('motion-prepare')
            time.sleep(1)
            checks.append(command('motion-sample'))
            saved_identity = command('identity')
            connection.request('save-all flush')
            connection.request('stop')
            process.wait(timeout=60)
            connection.close()
            connection = None
            with (report / 'restart.log').open('w') as restart_log:
                process = subprocess.Popen(['./gradlew','runSmokeServer','-PcsoRuntime=all','-PhoseTests',
                    f'-PperipheralTestDir={instance}','--offline','--console=plain'], cwd=ROOT, stdout=restart_log, stderr=subprocess.STDOUT)
                wait_until(lambda: 'Done (' in (report / 'restart.log').read_text(errors='replace'), {'server':process}, 240, 'restarting saved connector fixture')
                connection = Rcon(port, password)
                connection.request('forceload add 0 48 31 143')
                time.sleep(2)
                assert command('identity') == saved_identity, 'restart changed dock/head identity'
                checks.append('real process restart: same socket/head UUIDs, active connection, exactly one head')
            (report/'report.json').write_text(json.dumps({'passed':True,'checks':checks,'instance':str(instance)},indent=2)+'\n')
            print('PASS connector networks',flush=True)
    finally:
        if connection:
            try: connection.request('stop')
            except Exception: pass
            connection.close()
        if process:
            try: process.wait(timeout=40)
            except subprocess.TimeoutExpired: stop_process(process)

if __name__ == '__main__':
    main()
