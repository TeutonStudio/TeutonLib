#!/usr/bin/env python3
"""Packaged station tests in fresh local worlds; never opens a personal save."""
from pathlib import Path
import hashlib, json, secrets, shutil, socket, struct, subprocess, time, traceback
ROOT = Path(__file__).resolve().parents[1]
VERSION = next(line.split('=', 1)[1].strip() for line in (ROOT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
RUN = secrets.token_hex(6)
REPORT = ROOT / 'build/reports/station-build' / RUN
INSTANCE = ROOT / 'run/smoke/station-build' / RUN
REPORT.mkdir(parents=True)
INSTANCE.mkdir(parents=True)
checks = []

def exact(s, n):
    data = b''
    while len(data) < n:
        part = s.recv(n-len(data))
        if not part: raise RuntimeError('RCON closed')
        data += part
    return data

def request(s, kind, body):
    packet = struct.pack('<ii',42,kind)+body.encode()+b'\0\0'
    s.sendall(struct.pack('<i',len(packet))+packet)
    packet = exact(s,struct.unpack('<i',exact(s,4))[0])
    assert struct.unpack('<i',packet[:4])[0] == 42
    return packet[8:-2].decode().strip()

def wait(command, seconds=60):
    deadline = time.monotonic()+seconds
    while time.monotonic()<deadline:
        result = request(connection,2,command)
        if result.startswith('PASS'):
            print(result,flush=True); checks.append(result); return
        assert result.startswith('RUNNING'),result
        time.sleep(.5)
    raise AssertionError('Timeout: '+command)

subprocess.run(['./gradlew','jar','trainPortalTestClasses','--offline','--console=plain'],cwd=ROOT,check=True)
frozen = REPORT/f'teutonlib-{VERSION}.jar'
shutil.copy2(ROOT/f'build/libs/teutonlib-{VERSION}.jar', frozen)
init = REPORT/'packaged.gradle'
init.write_text('''allprojects { afterEvaluate {
    def packagedRun = sourceSets.create('stationPackaged')
    packagedRun.runtimeClasspath = sourceSets.main.runtimeClasspath - sourceSets.main.output + files("%s")
    neoForge.runs.getByName('smokeServer').with {
        sourceSet = packagedRun
        loadedMods.set([neoForge.mods.getByName('teutonlib_train_portal_test')])
    }
    tasks.named('runSmokeServer') { dependsOn tasks.jar }
} }
''' % str(frozen))
process = None; connection = None
try:
    for enabled in (True,False):
        stage = INSTANCE/('enabled' if enabled else 'disabled'); stage.mkdir()
        (stage/'eula.txt').write_text('eula=true\n')
        (stage/'world/serverconfig').mkdir(parents=True)
        # Config defaults are filled in by NeoForge; this is the real option.
        (stage/'world/serverconfig/teutonlib-server.toml').write_text('[trains]\nhybridByDefault = '+str(enabled).lower()+'\n')
        with socket.socket() as s:
            s.bind(('127.0.0.1',0)); port=s.getsockname()[1]
        password=secrets.token_hex(16)
        (stage/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\nmax-tick-time=180000\nlevel-name=world\nenable-rcon=true\nrcon.port='+str(port)+'\nrcon.password='+password+'\nbroadcast-rcon-to-ops=false\nspawn-protection=0\n')
        args=['./gradlew','runSmokeServer','-PcsoRuntime=all','-PtrainPortalTests','-PhybridTrainTests',f'-PperipheralTestDir={stage}','-I',str(init),'--offline','--console=plain']
        for restart in (False,True):
            logpath=REPORT/f'{enabled}-{restart}.log'
            with logpath.open('w') as log:
                process=subprocess.Popen(args,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
                deadline=time.monotonic()+240
                while 'Done (' not in logpath.read_text(errors='replace'):
                    assert process.poll() is None,logpath
                    assert time.monotonic()<deadline,logpath
                    time.sleep(.5)
                connection=socket.create_connection(('127.0.0.1',port),timeout=60)
                request(connection,3,password)
                if restart:
                    wait('teutonlib_station_persistence_test loaded')
                    wait('teutonlib_station_persistence_test returned')
                    if enabled: wait('teutonlib_station_build_test loaded')
                else:
                    wait('teutonlib_station_persistence_test setup')
                    time.sleep(3)
                    wait('teutonlib_station_persistence_test checkpoint')
                    if enabled:
                        result=request(connection,2,'teutonlib_station_build_test start'); assert result.startswith('RUNNING'),result
                        wait('teutonlib_station_build_test status')
                request(connection,2,'stop'); connection.close(); connection=None
                process.wait(timeout=120); assert process.returncode==0,logpath
                content=logpath.read_text(errors='replace')
                for failure in ('Mixin apply failed','FAILED during APPLY','[CTF FAILSAFE]','Failed to load sub-level','NullPointerException'):
                    assert failure not in content,(failure,logpath)
                process=None
            if enabled and not restart:
                config = stage/'world/serverconfig/teutonlib-server.toml'
                text = config.read_text()
                assert 'hybridByDefault = true' in text
                config.write_text(text.replace('hybridByDefault = true', 'hybridByDefault = false'))
                checks.append('PASS next restart disables new hybrid construction while retaining existing plots')
        # Ensure this run really used the requested setting, not a wrong TOML table.
        assert f'enabled={str(enabled).lower()}' in '\n'.join(checks)
    jar=frozen
    (REPORT/'result.json').write_text(json.dumps({'passed':True,'checks':checks,'instance':str(INSTANCE),'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest()},indent=2))
except BaseException:
    (REPORT/'result.json').write_text(json.dumps({'passed':False,'checks':checks,'failure':traceback.format_exc(),'instance':str(INSTANCE)},indent=2))
    raise
finally:
    if process and process.poll() is None:
        if connection:
            try: request(connection,2,'stop'); process.wait(timeout=60)
            except Exception: pass
        if process.poll() is None:
            process.terminate()
            try: process.wait(timeout=15)
            except subprocess.TimeoutExpired: process.kill(); process.wait()
    if connection: connection.close()
    print(REPORT,flush=True)
