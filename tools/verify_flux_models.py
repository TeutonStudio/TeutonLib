#!/usr/bin/env python3
"""Render both New Age extensions, all six orientations and five simultaneous Flux inputs."""
from pathlib import Path
import json, os, secrets, subprocess, time, traceback
from verify_train_portal_client import Rcon, available_ports, load_json, stop_process, wait_until

ROOT=Path(__file__).resolve().parents[1]
def main():
    token=time.strftime('%Y%m%d-%H%M%S')+'-'+secrets.token_hex(4)
    instance=ROOT/'run/smoke/flux-models'/token
    report=ROOT/'build/reports/flux-models'/token
    server=instance/'server'; client=instance/'client'
    server.mkdir(parents=True); client.mkdir(); report.mkdir(parents=True)
    game,port=available_ports(); password=secrets.token_hex(16)
    (server/'eula.txt').write_text('eula=true\n')
    (server/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={game}\nonline-mode=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=6\nsimulation-distance=4\nmax-tick-time=180000\nenable-rcon=true\nrcon.port={port}\nrcon.password={password}\nbroadcast-rcon-to-ops=false\nspawn-protection=0\ndifficulty=peaceful\nallow-flight=true\n')
    (client/'config').mkdir(); (client/'config/fml.toml').write_text('earlyWindowControl = false\n')
    (client/'options.txt').write_text('onboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nenableVsync:false\nrenderDistance:8\nmaxFps:40\n')
    init=report/'client.gradle'
    init.write_text("allprojects { afterEvaluate { neoForge.runs.getByName('client').with { gameDirectory=file(%s); systemProperty('teutonlib.hoseCableClientTests','true'); programArgument('--username'); programArgument('FluxObserver'); programArgument('--quickPlayMultiplayer'); programArgument(%s) } } }\n" % (json.dumps(str(client)),json.dumps(f'127.0.0.1:{game}')))
    env=dict(os.environ); env.setdefault('DISPLAY',':0')
    common=['-PcsoRuntime=all','-PhoseTests','--offline','--console=plain']
    processes={}; logs=[]; connection=None
    result={'passed':False,'captures':[],'instance':str(instance)}
    try:
        log=(report/'server.log').open('w'); logs.append(log)
        processes['server']=subprocess.Popen(['./gradlew','runSmokeServer',*common,f'-PperipheralTestDir={server}'],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
        wait_until(lambda:'Done (' in (report/'server.log').read_text(errors='replace'),processes,240,'starting Flux model server')
        connection=Rcon(port,password)
        for command in ['forceload add 32 32 79 63','gamerule doDaylightCycle false','time set 6000','weather clear','teutonlib_cable_test flux-matrix']:
            response=connection.request(command)
            if 'flux-matrix' in command: assert response.startswith('PASS cable'),response
        log=(report/'client.log').open('w'); logs.append(log)
        processes['client']=subprocess.Popen(['./gradlew','runClient',*common,'-I',str(init)],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT)
        output=client/'cable-verification'
        wait_until(lambda:load_json(output/'ready.json'),processes,300,'joining Flux observer')
        connection.request('gamemode spectator FluxObserver')
        directions=[('down',(0,-1,0)),('up',(0,1,0)),('north',(0,0,-1)),('south',(0,0,1)),('west',(-1,0,0)),('east',(1,0,0))]
        for row,kind in enumerate(['basic','advanced']):
            for index,(direction,(dx,dy,dz)) in enumerate(directions):
                x,y,z=40+index*6+.5,220.5,40+row*7+.5
                # View the back and a side together, with a lower angle for downward-facing motors.
                if dy: camera=(x+4.2,y+1.0-dy*2,z+4.2)
                else: camera=(x-dx*4.5+dz*2.8,y+1.5,z-dz*4.5-dx*2.8)
                connection.request(f'execute anchored eyes run tp FluxObserver {camera[0]} {camera[1]} {camera[2]} facing {x} {y} {z}')
                name=f'flux-{kind}-{direction}'
                connection.request('tellraw FluxObserver '+json.dumps({'text':f'TEUTONLIB_CABLE_CAPTURE {name} 00000000-0000-0000-0000-000000000000 false'}))
                capture=wait_until(lambda:load_json(output/f'{name}.json'),processes,90,f'capturing {name}')
                assert capture.get('passed'),capture
                result['captures'].append(capture)
                print(f'PASS {name}: five fitted models, collision extents and network tint',flush=True)
        connection.request('tellraw FluxObserver '+json.dumps({'text':'TEUTONLIB_CABLE_FINISH'}))
        processes['client'].wait(timeout=90); assert processes['client'].returncode==0
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
