#!/usr/bin/env python3
"""Exercise actual native rope steps and collision samples in an isolated local world."""
from pathlib import Path
import argparse, json, math, secrets, subprocess, time, traceback, shutil
from verify_train_portal_client import Rcon, available_ports, stop_process, wait_until

ROOT = Path(__file__).resolve().parents[1]

def resample(points, count=65):
    distances = [0.0]
    for a, b in zip(points, points[1:]):
        distances.append(distances[-1]+math.dist(a,b))
    output = []
    edge = 0
    for index in range(count):
        distance = distances[-1]*index/(count-1)
        while edge < len(points)-2 and distances[edge+1] < distance:
            edge += 1
        span = distances[edge+1]-distances[edge]
        t = (distance-distances[edge])/span if span > 1e-12 else 0.0
        output.append([a+(b-a)*t for a,b in zip(points[edge],points[edge+1])])
    return output

def stability(connection):
    samples = []
    counts = []
    raw = []
    for _ in range(40):
        response = connection.request('teutonlib_cable_test sample')
        assert response.startswith('PASS cable sample '), response
        points = json.loads(response.split(' ',3)[3])
        raw.append(points)
        counts.append(len(points))
        samples.append(resample(points))
        time.sleep(.1)
    movements = [math.dist(a,b) for previous,current in zip(samples,samples[1:]) for a,b in zip(previous,current)]
    return {'maximumStep':max(movements), 'rmsStep':math.sqrt(sum(d*d for d in movements)/len(movements)),
            'topologyChanges':sum(a!=b for a,b in zip(counts,counts[1:])), 'samples':len(samples), 'poses':raw}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--max-jitter', type=float, default=.05)
    args = parser.parse_args()
    run_id = time.strftime('%Y%m%d-%H%M%S')+'-'+secrets.token_hex(4)
    base = ROOT/'run/smoke/hose-cable'
    instance = base/'fixture'
    report = ROOT/'build/reports/hose-cable'/run_id
    if not (instance/'world').exists():
        seeds = list(base.glob('*/world'))
        if seeds: shutil.copytree(max(seeds,key=lambda p:p.stat().st_mtime),instance/'world')
    instance.mkdir(parents=True,exist_ok=True); report.mkdir(parents=True)
    game, port = available_ports(); password = secrets.token_hex(16)
    (instance/'eula.txt').write_text('eula=true\n')
    (instance/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={game}\nonline-mode=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nview-distance=5\nsimulation-distance=4\nmax-tick-time=180000\nenable-rcon=true\nrcon.port={port}\nrcon.password={password}\nbroadcast-rcon-to-ops=false\nspawn-protection=0\n')
    process = connection = None
    result = {'passed':False,'checks':[],'instance':str(instance)}
    try:
        with (report/'server.log').open('w') as log:
            process = subprocess.Popen(['./gradlew','runSmokeServer','-PcsoRuntime=all','-PhoseTests',f'-PperipheralTestDir={instance}','--offline','--console=plain'],cwd=ROOT,stdout=log,stderr=subprocess.STDOUT)
            wait_until(lambda:'Done (' in (report/'server.log').read_text(errors='replace'),{'server':process},240,'starting cable physics server')
            connection = Rcon(port,password)
            connection.request("forceload add 32 32 63 63")
            time.sleep(1.0)
            for command, delay in [('prepare',4),('status',0),('saved',0),('obstacle-move',4),('status',0),('retract',1),('released',0),('cleanup',0)]:
                response = connection.request('teutonlib_cable_test '+command)
                print(response,flush=True)
                assert response.startswith('PASS cable '),response
                result['checks'].append(response)
                time.sleep(delay)
                if command == 'status':
                    measured = stability(connection)
                    result.setdefault('stability',[]).append(measured)
                    print('Cable stability: '+json.dumps({k:v for k,v in measured.items() if k!='poses'}),flush=True)
                    assert measured['maximumStep'] <= args.max_jitter, {k:v for k,v in measured.items() if k!='poses'}
            connection.request('stop'); process.wait(timeout=90)
            assert process.returncode == 0,process.returncode
            result['passed']=True
    except BaseException:
        result['failure']=traceback.format_exc(); raise
    finally:
        if connection:
            if process and process.poll() is None:
                try: connection.request('stop')
                except Exception: pass
            connection.close()
        if process: stop_process(process)
        (report/'report.json').write_text(json.dumps(result,indent=2)+'\n')
if __name__ == '__main__': main()
