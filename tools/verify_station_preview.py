#!/usr/bin/env python3
"""Exercise real Create station overlays and client use-item packets in disposable worlds."""
from pathlib import Path
import hashlib,json,math,secrets,shutil,socket,struct,subprocess,time,traceback
ROOT=Path(__file__).resolve().parents[1]
VERSION=next(line.split('=',1)[1].strip() for line in (ROOT/'gradle.properties').read_text().splitlines() if line.startswith('mod_version='))
run=secrets.token_hex(6); report=ROOT/'build/reports/station-preview'/run; report.mkdir(parents=True)
instance=ROOT/'run/smoke/station-preview'/run; server=instance/'server'; client=instance/'client'
server.mkdir(parents=True); client.mkdir()
checks=[]; processes={}; connection=None

def port():
    with socket.socket() as s: s.bind(('127.0.0.1',0)); return s.getsockname()[1]
def exact(n):
    data=b''
    while len(data)<n:
        part=connection.recv(n-len(data))
        if not part: raise RuntimeError('RCON closed')
        data+=part
    return data
def request(kind,body):
    packet=struct.pack('<ii',42,kind)+body.encode()+b'\0\0'; connection.sendall(struct.pack('<i',len(packet))+packet)
    packet=exact(struct.unpack('<i',exact(4))[0]); assert struct.unpack('<i',packet[:4])[0]==42
    return packet[8:-2].decode().strip()
def command(s): return request(2,s)
def wait(test,seconds=120):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        for name,p in processes.items(): assert p.poll() is None,(name,p.returncode,report)
        result=test()
        if result: return result
        failure=client/'station-preview/failure.txt'
        assert not failure.exists(),failure.read_text() if failure.exists() else ''
        time.sleep(.25)
    raise AssertionError('Timeout; '+str(report))
def action(name,i):
    result=command(f'teutonlib_station_preview_test {name} {i}')
    assert result.startswith('PASS'),result
    return result
def tell(message): command('tellraw @a '+json.dumps({'text':'STATION_PREVIEW '+message}))
def status(i): return json.loads(command(f'teutonlib_station_preview_test status {i}'))
def camera(i):
    dx,dz=dirs[i]; x=i*192+dx*7-dz*12; z=i*128+dz*7+dx*12
    command(f'tp @a {x+.5} 191 {z+.5} facing {i*192+dx*7+.5} 180 {i*128+dz*7+.5}')
    time.sleep(1)
def capture(label,i,expected):
    camera(i); tell(f'capture {label} {i}'); path=client/'station-preview'/f'{label}.json'
    wait(path.exists,30); data=json.loads(path.read_text()); actual=status(i)
    assert sorted(b['offset'] for b in data['bogeys'])==expected,(label,data)
    assert sorted(b['offset'] for b in actual['bogeys'])==expected,(label,actual)
    for b in data['bogeys']:
        native=next(a for a in actual['bogeys'] if a['offset']==b['offset'])
        assert b['world']==native['world'] and b['state']==native['state'],(b,native)
    for offset,valid in data['renderedValid'].items():
        offset=int(offset)
        expected_valid=(offset>=3 or offset==0) and (bool(expected) or offset==0) and all(abs(offset-b)>2 for b in expected)
        assert valid==expected_valid,(label,offset,valid,expected_valid)
    checks.append({'label':label,'client':data,'server':actual}); print('PASS rendered '+label,flush=True)
def click(i,offset,count,pitch=30):
    dx,dz=dirs[i]; x=i*192+dx*(offset+1); z=i*128+dz*(offset+1)
    command(f'tp @a {x+.5} 182 {z+2.5} 180 {pitch}')
    command('teutonlib_station_preview_test equip'); time.sleep(.5)
    tell(f'click {i} {offset}'); wait(lambda:len(status(i)['bogeys'])==count,20); time.sleep(.5)

game,rcon=port(),port(); password=secrets.token_hex(16)
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={game}\nonline-mode=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=5\nsimulation-distance=5\nmax-tick-time=180000\nlevel-name=world\nenable-rcon=true\nrcon.port={rcon}\nrcon.password={password}\nbroadcast-rcon-to-ops=false\nspawn-protection=0\n')
(client/'options.txt').write_text('onboardAccessibility:false\npauseOnLostFocus:false\nfullscreen:false\nenableVsync:false\nrenderDistance:6\nsimulationDistance:5\nmaxFps:60\nfov:0.0\n')
(client/'config').mkdir(); (client/'config/fml.toml').write_text('earlyWindowControl = false\n')
subprocess.run(['./gradlew','jar','trainPortalTestClasses','--offline','--console=plain'],cwd=ROOT,check=True)
frozen=report/f'teutonlib-{VERSION}.jar'; shutil.copy2(ROOT/f'build/libs/teutonlib-{VERSION}.jar',frozen)
init=report/'isolated.gradle'
init.write_text('''allprojects { afterEvaluate {
    def packagedRun = sourceSets.create('stationPreviewPackaged')
    packagedRun.runtimeClasspath = sourceSets.main.runtimeClasspath - sourceSets.main.output + files(%s)
    ['client','smokeServer'].each { name -> neoForge.runs.getByName(name).with {
        sourceSet = packagedRun
        loadedMods.set([neoForge.mods.getByName('teutonlib_train_portal_test')])
    } }
    neoForge.runs.getByName('client').with {
        gameDirectory = file(%s)
        systemProperty('teutonlib.stationPreviewTests','true')
        systemProperty('teutonlib.trainPortalClientTests','true')
        programArgument('--quickPlayMultiplayer'); programArgument(%s)
    }
    tasks.named('runSmokeServer') { dependsOn tasks.jar }
    tasks.named('runClient') { dependsOn tasks.jar }
} }
'''%(json.dumps(str(frozen)),json.dumps(str(client)),json.dumps(f'127.0.0.1:{game}')))
common=['-PcsoRuntime=all','-PtrainPortalTests','-I',str(init),'--offline','--console=plain','--no-daemon']
dirs=[(1,0),(-1,0),(0,1),(0,-1)]
try:
    with (report/'server.log').open('w') as sl,(report/'client.log').open('w') as cl:
        processes['server']=subprocess.Popen(['./gradlew','runSmokeServer',*common,f'-PperipheralTestDir={server}'],cwd=ROOT,stdout=sl,stderr=subprocess.STDOUT)
        wait(lambda:'Done (' in (report/'server.log').read_text(errors='replace'),240)
        connection=socket.create_connection(('127.0.0.1',rcon),timeout=60); request(3,password)
        command('gamerule doDaylightCycle false'); command('time set 6000'); command('weather clear')
        for i in range(4): action('setup',i)
        time.sleep(3)
        for i in range(4): action('enter',i)
        processes['client']=subprocess.Popen(['./gradlew','runClient',*common],cwd=ROOT,stdout=cl,stderr=subprocess.STDOUT)
        wait(lambda:'logged in with entity id' in (report/'server.log').read_text(errors='replace'),240)
        command('gamemode creative @a'); command('teutonlib_station_preview_test equip')
        for i in range(4):
            capture(f'{i}-empty',i,[])
            click(i,0,1); capture(f'{i}-first',i,[0])
            click(i,6,2); capture(f'{i}-second',i,[0,6])
            before=status(i); click(i,6,2); time.sleep(.5); after=status(i)
            assert before['bogeys']!=after['bogeys'],('Size change failed',i,before,after)
            capture(f'{i}-resized',i,[0,6])
            click(i,14,3); capture(f'{i}-third',i,[0,6,14])
            action('remove',i); time.sleep(.5); capture(f'{i}-removed',i,[])
            click(i,0,1,pitch=-30); capture(f'{i}-upward-look',i,[0])
            native=status(i)['bogeys'][0]
            assert native['upsideDown']==native['canBeUpsideDown'],native
            action('cancel',i)
        tell('quit'); processes['client'].wait(timeout=60); assert processes['client'].returncode==0; processes.pop('client')
        command('stop'); connection.close(); connection=None
        processes['server'].wait(timeout=120); assert processes['server'].returncode==0; processes.pop('server')
    for name in ('server','client'):
        text=(report/f'{name}.log').read_text(errors='replace')
        for failure in ('FAILED during APPLY','NullPointerException','[CTF FAILSAFE]'):
            assert failure not in text,(name,failure)
    jar=frozen
    (report/'result.json').write_text(json.dumps({'passed':True,'checks':checks,'instance':str(instance),'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest()},indent=2))
except BaseException:
    (report/'result.json').write_text(json.dumps({'passed':False,'checks':checks,'instance':str(instance),'failure':traceback.format_exc()},indent=2)); raise
finally:
    if 'client' in processes and processes['client'].poll() is None:
        try: tell('quit'); processes['client'].wait(timeout=20)
        except Exception: pass
    if 'server' in processes and processes['server'].poll() is None and connection:
        try: command('stop'); processes['server'].wait(timeout=60)
        except Exception: pass
    for p in processes.values():
        if p.poll() is None:
            p.terminate()
            try: p.wait(timeout=15)
            except subprocess.TimeoutExpired: p.kill(); p.wait()
    if connection: connection.close()
    print(report,flush=True)
