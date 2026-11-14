#!/usr/bin/env python3
"""Verify real antenna clicks and both locator screens in an isolated local client/server."""
from pathlib import Path
import json, secrets, shutil, socket, struct, subprocess, sys, time

ROOT = Path(__file__).resolve().parents[1]
SABLE = '--sable' in sys.argv
VISUAL = '--visual' in sys.argv
if VISUAL and not SABLE: raise SystemExit('--visual requires --sable')
RUN = secrets.token_hex(6)
OUTPUT = ROOT / 'build/reports/peripheral-antenna-client' / RUN
INSTANCE = ROOT / 'run/peripheral/antenna-client' / RUN
SERVER, CLIENT = INSTANCE / 'server', INSTANCE / 'client'
for path in (OUTPUT, SERVER, CLIENT): path.mkdir(parents=True)
processes = {}
connection = None

def port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]

def exact(length):
    data = b''
    while len(data) < length:
        part = connection.recv(length - len(data))
        if not part: raise RuntimeError('RCON closed')
        data += part
    return data

def request(kind, body):
    data = struct.pack('<ii', 42, kind) + body.encode() + b'\0\0'
    connection.sendall(struct.pack('<i', len(data)) + data)
    response = exact(struct.unpack('<i', exact(4))[0])
    assert struct.unpack('<i', response[:4])[0] == 42
    return response[8:-2].decode().strip()

def command(body): return request(2, body)

def wait(test, seconds=180):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        failure = OUTPUT / 'failure.txt'
        assert not failure.exists(), failure.read_text() if failure.exists() else ''
        result = test()
        if result: return result
        for name, process in processes.items():
            assert process.poll() is None, (name, process.returncode, OUTPUT)
        time.sleep(.25)
    raise AssertionError(f'Timeout: {OUTPUT}')

game, rcon = port(), port()
password = secrets.token_hex(16)
accepted = ROOT / 'run/smoke/all/eula.txt'
if not accepted.exists(): accepted = ROOT / 'run/peripheral/cc/eula.txt'
if accepted.exists(): shutil.copy2(accepted, SERVER / 'eula.txt')
(SERVER / 'server.properties').write_text(
    f'server-ip=127.0.0.1\nserver-port={game}\nonline-mode=false\nlevel-type=minecraft:flat\n'
    f'generate-structures=false\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\n'
    f'enable-rcon=true\nrcon.port={rcon}\nrcon.password={password}\nbroadcast-rcon-to-ops=false\n')
(CLIENT / 'options.txt').write_text('onboardAccessibility:false\npauseOnLostFocus:false\nfullscreen:false\nenableVsync:false\nrenderDistance:5\nsimulationDistance:5\nmaxFps:60\nlang:en_us\n')
(CLIENT / 'config').mkdir()
(CLIENT / 'config/fml.toml').write_text('earlyWindowControl = false\n')
init = OUTPUT / 'isolated.gradle'
init.write_text('''allprojects { afterEvaluate {
    %s
    neoForge.runs.getByName('client').with {
        gameDirectory = file(%s)
        systemProperty('teutonlib.peripheralAntennaClientTests', 'true')
        systemProperty('teutonlib.antennaClientReport', %s)
        systemProperty('teutonlib.antennaClientServer', %s)
    }
} }
''' % ("dependencies.add('runtimeOnly', files('dependencies/verification/sable-neoforge-1.21.1-' + providers.gradleProperty('sable_version').get() + '.jar'))" if SABLE else '', json.dumps(str(CLIENT)), json.dumps(str(OUTPUT)), json.dumps(f'127.0.0.1:{game}')))
common = ['-PcsoRuntime=create', '-PperipheralRuntime=cc', '-PperipheralTests', '-I', str(init), '--offline', '--console=plain', '--no-daemon']
try:
    with (OUTPUT / 'server.log').open('w') as server_log, (OUTPUT / 'client.log').open('w') as client_log:
        processes['server'] = subprocess.Popen(['./gradlew', 'runSmokeServer', *common, f'-PperipheralTestDir={SERVER}'], cwd=ROOT, stdout=server_log, stderr=subprocess.STDOUT)
        wait(lambda: 'Done (' in (OUTPUT / 'server.log').read_text(errors='replace'))
        connection = socket.create_connection(('127.0.0.1', rcon), timeout=20)
        request(3, password)
        assert 'PASS' in command('teutonlib_link_test antenna_client_setup' + ('_sable' if SABLE else ''))
        command('gamerule doDaylightCycle false'); command('time set 6000'); command('weather clear')
        processes['client'] = subprocess.Popen(['./gradlew', 'runClient', *common], cwd=ROOT, stdout=client_log, stderr=subprocess.STDOUT)
        wait(lambda: 'logged in with entity id' in (OUTPUT / 'server.log').read_text(errors='replace'))
        wait(lambda: (OUTPUT / 'status.txt').exists() and (OUTPUT / 'status.txt').read_text() == 'READY')
        command('gamemode creative @a')
        if VISUAL:
            for variant, name in enumerate(('sable-visual-top', 'sable-visual-bottom', 'sable-visual-moved-rotated', 'sable-visual-moved-rotated-again')):
                assert 'PASS' in command(f'teutonlib_link_test antenna_client_visual_{variant}')
                (OUTPUT / 'visual-request.txt').write_text(name)
                wait(lambda: (OUTPUT / 'visual-done.txt').exists() and (OUTPUT / 'visual-done.txt').read_text() == name)
            (OUTPUT / 'visual-request.txt').unlink()
            assert 'PASS' in command('teutonlib_link_test antenna_client_visual_-1')
        command('fill 10 200 7 10 205 12 minecraft:stone')
        assert 'PASS' in command('teutonlib_link_test antenna_client_focus')
        (OUTPUT / 'focus.txt').write_text('ready')
        wait(lambda: (OUTPUT / 'status.txt').exists() and (OUTPUT / 'status.txt').read_text() == 'LINK_FOCUS')
        command('tp @a 13.5 200 11.5 161.565 2.17')
        (OUTPUT / 'link-focus.txt').write_text('ready')
        wait(lambda: (OUTPUT / 'status.txt').read_text().startswith('PASS'))
        processes['client'].wait(timeout=60)
        assert processes.pop('client').returncode == 0
        screenshots = sorted((OUTPUT / 'screenshots').glob('*.png'))
        screenshots = [path for path in screenshots if not path.name.startswith('debug-')]
        assert len(screenshots) == (12 if VISUAL else 8), screenshots
        command('stop'); connection.close(); connection = None
        processes['server'].wait(timeout=60)
        assert processes.pop('server').returncode == 0
        (OUTPUT / 'result.json').write_text(json.dumps({'passed': True, 'sable': SABLE, 'visual': VISUAL, 'screenshots': [str(p) for p in screenshots]}, indent=2))
        print(f'PASS real antenna client/server; reports: {OUTPUT}', flush=True)
finally:
    if connection:
        try: command('stop'); connection.close()
        except Exception: pass
    for process in processes.values():
        if process.poll() is None:
            process.terminate()
            try: process.wait(timeout=20)
            except subprocess.TimeoutExpired: process.kill()
