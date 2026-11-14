#!/usr/bin/env python3
"""Two-client rope snapshots, screenshots, recessed Flux model, and out-of-tracking rendering."""
from pathlib import Path
import json, os, re, secrets, subprocess, time, traceback
from verify_train_portal_client import Rcon, available_ports, load_json, stop_process, wait_until
ROOT=Path(__file__).resolve().parents[1]
def main():
    token=time.strftime('%Y%m%d-%H%M%S')+'-'+secrets.token_hex(4)
    instance=ROOT/'run/smoke/hose-cable-client'/token; report=ROOT/'build/reports/hose-cable-client'/token
    server=instance/'server'; server.mkdir(parents=True); report.mkdir(parents=True)
    game,port=available_ports(); password=secrets.token_hex(16)
    (server/'eula.txt').write_text('eula=true\n')
    (server/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={game}\nonline-mode=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=8\nsimulation-distance=5\nmax-tick-time=180000\nenable-rcon=true\nrcon.port={port}\nrcon.password={password}\nbroadcast-rcon-to-ops=false\nspawn-protection=0\ndifficulty=peaceful\nallow-flight=true\n')
    processes={}; logs=[]; connection=None; result={'passed':False,'captures':[],'instance':str(instance)}
    common=['-PcsoRuntime=all','-PhoseTests','--offline','--console=plain']
    env=dict(os.environ); env.setdefault('DISPLAY',':0')
    try:
        log=(report/'server.log').open('w'); logs.append(log)
        processes['server']=subprocess.Popen(['./gradlew','runSmokeServer',*common,f'-PperipheralTestDir={server}'],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
        wait_until(lambda:'Done (' in (report/'server.log').read_text(errors='replace'),processes,240,'starting cable display server')
        connection=Rcon(port,password)
        connection.request("forceload add 32 32 63 95"); time.sleep(1.0)
        for command in ['gamerule doDaylightCycle false','time set 6000','weather clear','teutonlib_cable_test prepare','teutonlib_cable_test flux']:
            response=connection.request(command)
            if command.endswith('prepare'):
                assert response.startswith('PASS cable'),response
                head=re.search(r'head=([0-9a-f-]+)',response)[1]
        time.sleep(5)
        result['serverChecks']=[]
        for command in ['teutonlib_cable_test status','teutonlib_cable_test saved']:
            response=connection.request(command); assert response.startswith('PASS '),response
            result['serverChecks'].append(response)
        response=connection.request('teutonlib_cable_test long'); assert response.startswith('PASS cable'),response
        long_head=re.search(r'head=([0-9a-f-]+)',response)[1]
        # Compile once before launching both clients, then Gradle tasks remain up to date.
        for observer in (1,2):
            client=instance/f'client-{observer}'; client.mkdir(); (client/'config').mkdir()
            (client/'config/fml.toml').write_text('earlyWindowControl = false\n')
            (client/'options.txt').write_text('onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nenableVsync:false\nrenderDistance:8\nmaxFps:40\n')
            init=report/f'client-{observer}.gradle'
            init.write_text("allprojects { afterEvaluate { neoForge.runs.getByName('client').with { gameDirectory=file(%s); systemProperty('teutonlib.hoseCableClientTests','true'); programArgument('--username'); programArgument('CableObserver%s'); programArgument('--quickPlayMultiplayer'); programArgument(%s) } } }\n" % (json.dumps(str(client)),observer,json.dumps(f'127.0.0.1:{game}')))
            log=(report/f'client-{observer}.log').open('w'); logs.append(log)
            processes[f'client-{observer}']=subprocess.Popen(['./gradlew','runClient',*common,'-I',str(init)],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
            output=client/'cable-verification'
            diagnostic_at=None
            dumped=False
            def ready_or_failure():
                nonlocal diagnostic_at,dumped
                content=(report/f'client-{observer}.log').read_text(errors='replace')
                assert 'Failed to register automatic subscribers' not in content, 'Client test subscriber registration failed'
                assert '---- Minecraft Crash Report ----' not in content, 'Client crashed during startup'
                if 'Connecting to ' in content and diagnostic_at is None: diagnostic_at=time.monotonic()
                if diagnostic_at is not None and not dumped and time.monotonic()-diagnostic_at>4:
                    dumped=True
                    for proc in Path('/proc').iterdir():
                        if not proc.name.isdigit(): continue
                        try:
                            cwd=os.readlink(proc/'cwd'); args=(proc/'cmdline').read_bytes().split(b'\0')
                        except OSError: continue
                        if cwd in (str(server),str(client)) and args and args[0].endswith(b'/java'):
                            jcmd=Path(os.fsdecode(args[0])).with_name('jcmd')
                            with (report/f'threads-{observer}-{Path(cwd).name}.txt').open('w') as dump:
                                subprocess.run([str(jcmd),proc.name,'Thread.print'],stdout=dump,stderr=subprocess.STDOUT)
                assert 'Client disconnected with reason:' not in content, 'Client disconnected while joining; see thread snapshots'
                return load_json(output/'ready.json')
            ready=wait_until(ready_or_failure,processes,300,f'joining observer {observer}')
            player=ready['player']; assert re.fullmatch(r'[A-Za-z0-9_]{1,16}',player)
            connection.request(f'gamemode spectator {player}')
            for name,coords,uuid,tracked in [('sag','45.5 217.5 53 180 8',head,True),('flux','44 214.8 47 180 15',head,True),('long','40.5 217 90 180 -5',long_head,False)]:
                connection.request(f'tp {player} {coords}')
                connection.request('tellraw '+player+' '+json.dumps({'text':f'TEUTONLIB_CABLE_CAPTURE {name} {uuid} {str(tracked).lower()}'}))
                capture=wait_until(lambda:load_json(output/f'{name}.json'),processes,90,f'capturing {name} for observer {observer}')
                assert capture.get('passed'),capture
                capture['observer']=observer; result['captures'].append(capture)
                print(f'PASS observer {observer} {name}: points={len(capture["points"])}, tracked={capture["tracked"]}',flush=True)
        sag=[c for c in result['captures'] if c['name']=='sag']
        assert len(sag[0]['points'])==len(sag[1]['points'])
        maximum=max(sum((a-b)**2 for a,b in zip(p,q))**.5 for p,q in zip(sag[0]['points'],sag[1]['points']))
        # Captures are taken at separate server times; the settled cable must agree spatially.
        assert maximum<.25,maximum
        result['twoClientMaximumPointDifference']=maximum
        before=connection.request('teutonlib_cable_test long-status')
        assert 'ready=true active=true' in before,before
        for observer in (1,2): connection.request(f'tp CableObserver{observer} 0 225 0')
        connection.request('forceload remove 144 64 191 95')
        def paused():
            value=connection.request('teutonlib_cable_test long-status')
            return value if 'ready=false active=false' in value else None
        pause=wait_until(paused,processes,30,'pausing cable through an untracked chunk')
        assert int(re.search(r'objects=(\d+)',pause)[1])<int(re.search(r'objects=(\d+)',before)[1]),(before,pause)
        connection.request('forceload add 144 64 191 95')
        def resumed():
            value=connection.request('teutonlib_cable_test long-status')
            return value if 'ready=true active=true' in value else None
        resume=wait_until(resumed,processes,30,'resuming the same cable after chunk load')
        assert f'head={long_head}' in pause and f'head={long_head}' in resume
        result['chunkLifecycle']=[before,pause,resume]
        for observer in (1,2): connection.request('tellraw CableObserver'+str(observer)+' '+json.dumps({'text':'TEUTONLIB_CABLE_FINISH'}))
        for key,process in processes.items():
            if key!='server': process.wait(timeout=90); assert process.returncode==0
        connection.request('stop'); processes['server'].wait(timeout=90); assert processes['server'].returncode==0
        result['passed']=True
    except BaseException:
        result['failure']=traceback.format_exc(); raise
    finally:
        if connection:
            if processes.get('server') and processes['server'].poll() is None:
                try: connection.request('stop')
                except Exception: pass
            connection.close()
        for process in reversed(list(processes.values())): stop_process(process)
        for log in logs: log.close()
        (report/'report.json').write_text(json.dumps(result,indent=2)+'\n')
if __name__=='__main__': main()
