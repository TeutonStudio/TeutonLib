#!/usr/bin/env python3
"""Exercise a disposable localhost-only Minecraft server (Python standard library only).

Usage: python3 tools/verify_server.py [all|none|create]
Needs the same local dependencies as ./gradlew runServer. Profiles run sequentially.
The generated worlds live under run/smoke/<profile>, separate from development worlds.
"""
from pathlib import Path
import subprocess,time,os,signal,socket,struct,secrets,sys,json
root=Path(__file__).resolve().parents[1]
profile=sys.argv[1] if len(sys.argv)>1 else 'all'
report=root/'build/reports/smoke';report.mkdir(parents=True,exist_ok=True)
if profile not in ('all', 'none', 'create'):
 raise SystemExit('Profile must be all, none or create')
instance=root/'run/smoke'/profile
instance.mkdir(parents=True,exist_ok=True)
properties=instance/'server.properties'
original=properties.read_text() if properties.exists() else 'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n'
config=instance/'world/serverconfig/teutonlib-server.toml'
if config.exists():
 config.write_text(config.read_text().replace('enabled = false','enabled = true'))
values={'server-ip':'127.0.0.1','enable-rcon':'true','rcon.password':secrets.token_hex(16),'rcon.port':'25585','broadcast-rcon-to-ops':'false'}
lines=original.splitlines()
for key,value in values.items():
 prefix=key+'='
 lines=[line for line in lines if not line.startswith(prefix)]
 lines.append(prefix+value)
properties.write_text('\n'.join(lines)+'\n')
logpath=report/f'server-{profile}.log'
log=logpath.open('w')
motor_tests = os.environ.get('TEUTONLIB_MOTOR_TESTS') == '1'
extra_args = ['-PmotorEnergyTests'] if motor_tests else []
docking_tests = os.environ.get('TEUTONLIB_DOCKING_TESTS') == '1'
if docking_tests: extra_args.append('-PanalogDockingTests')
proc=subprocess.Popen(['./gradlew','runSmokeServer',f'-PcsoRuntime={profile}',*extra_args,'--offline','--no-daemon','--console=plain'],cwd=root,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
connection=None
checks=[]
def read_exact(n):
 data=b''
 while len(data)<n:
  chunk=connection.recv(n-len(data))
  if not chunk: raise RuntimeError('RCON closed')
  data+=chunk
 return data
def request(kind,body):
 packet=struct.pack('<ii',42,kind)+body.encode()+b'\0\0'
 connection.sendall(struct.pack('<i',len(packet))+packet)
 size=struct.unpack('<i',read_exact(4))[0]
 response=read_exact(size)
 identifier,response_type=struct.unpack('<ii',response[:8])
 if identifier!=42: raise RuntimeError('RCON request rejected')
 return response[8:-2].decode()
def command(body):
 response=request(2,body)
 checks.append({'command':body,'response':response})
 print(body+': '+response,flush=True)
 return response
def expect_status(requested,active):
 response=command('teutonlib cso status')
 assert f'Config={str(requested).lower()}' in response,response
 assert f'aktiv={str(active).lower()}' in response,response
try:
 deadline=time.monotonic()+150
 while time.monotonic()<deadline:
  if proc.poll() is not None: raise RuntimeError('Server exited during startup')
  if 'Done (' in logpath.read_text(errors='replace'): break
  time.sleep(.5)
 else: raise RuntimeError('Server startup timed out')
 deadline=time.monotonic()+10
 while connection is None:
  try: connection=socket.create_connection(('127.0.0.1',25585),timeout=5)
  except ConnectionRefusedError:
   if time.monotonic()>=deadline: raise
   time.sleep(.1)
 request(3,values['rcon.password'])
 active=profile=='all'
 # The fixture world was created with the default enabled setting.
 expect_status(True,active)
 if docking_tests:
  assert profile == 'all', 'Docking verification requires Simulated and Sable'
  assert 'Analog docking PASS:' in command('teutonlib_docking_test')
 if motor_tests:
  assert profile == 'all', 'Motor verification requires the all profile and local New Age/Flux JARs'
  assert 'verification PASS' in command('teutonlib_energy_test')
  command('teutonlib_energy_test prepare')
  for enabled in (False, True):
   contents=config.read_text()
   old=f'motorExtensionEnergyTransfer = {str(not enabled).lower()}'
   new=f'motorExtensionEnergyTransfer = {str(enabled).lower()}'
   assert old in contents, contents
   config.write_text(contents.replace(old,new))
   deadline=time.monotonic()+15
   while time.monotonic()<deadline:
    response=request(2,'teutonlib_energy_test probe')
    if f'enabled={str(enabled).lower()} PASS=true' in response: break
    time.sleep(.25)
   else: raise AssertionError('Motor energy config reload failed: '+response)
   checks.append({'motorConfigReload': enabled, 'response': response})
  command('fill 6 218 6 10 222 10 minecraft:air')
  command('forceload add 16000 16000')
  assert 'PASS=true' in command('teutonlib_energy_test lifecycle prepare')
  command('forceload remove 16000 16000')
  deadline=time.monotonic()+40
  while time.monotonic()<deadline:
   response=request(2,'teutonlib_energy_test lifecycle unloaded')
   if 'PASS=true' in response: break
   time.sleep(.5)
  else: raise AssertionError('Motor chunk unload failed: '+response)
  checks.append({'motorChunkUnload':response})
  assert 'PASS=true' in command('teutonlib_energy_test lifecycle reload')
  assert 'PASS=true' in command('teutonlib_energy_test lifecycle prepare')
 if profile in ('all','create'):
  command('forceload add 0 0')
  command('setblock 0 100 0 create:creative_motor[facing=east]')
  command('setblock 1 100 0 create:cogwheel[axis=x]')
  command('setblock 1 101 0 create:cogwheel[axis=x]')
  if profile == 'all': command('setblock 3 100 0 simulated:docking_connector')
  time.sleep(1)
  assert '-16.0f' in command('data get block 1 101 0 Speed')
 for _ in range(2):
  command('teutonlib cso disable');expect_status(False,False)
  if profile in ('all','create'): assert '-16.0f' in command('data get block 1 101 0 Speed')
  command('teutonlib cso enable');expect_status(True,active)
 config=instance/'world/serverconfig/teutonlib-server.toml'
 contents=config.read_text()
 assert 'enabled = true' in contents
 config.write_text(contents.replace('enabled = true','enabled = false'))
 deadline=time.monotonic()+15
 while time.monotonic()<deadline:
  response=request(2,'teutonlib cso status')
  if 'Config=false' in response and 'aktiv=false' in response: break
  time.sleep(.25)
 else: raise AssertionError('Config watcher did not apply the live edit')
 checks.append({'fileReload':'passed','status':response})
 print('Live config file reload: passed',flush=True)
 command('teutonlib cso enable');expect_status(True,active)
 assert 'enabled = true' in config.read_text()
 if profile in ('all','create'):
  command('fill 0 100 0 3 101 0 minecraft:air')
  command('forceload remove 0 0')
 if docking_tests:
  command('forceload add 16000 16000 16016 16032')
  command('teutonlib_docking_test prepareReload')
  deadline=time.monotonic()+15
  while time.monotonic()<deadline:
   response=request(2,'teutonlib_docking_test probeReload')
   if 'PASS=true' in response: break
   time.sleep(.25)
  else: raise AssertionError('Docking reload fixture did not lock: '+response)
  checks.append({'dockingBeforeReload':response})
 command('stop')
 proc.wait(timeout=60)
 assert proc.returncode==0,f'Gradle exit {proc.returncode}'
 if motor_tests or docking_tests:
  connection.close();connection=None
  # Reload the same disposable world and verify saved buffers and connections.
  log.flush()
  offset=logpath.stat().st_size
  proc=subprocess.Popen(['./gradlew','runSmokeServer',f'-PcsoRuntime={profile}',*extra_args,'--offline','--no-daemon','--console=plain'],cwd=root,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
  deadline=time.monotonic()+150
  while time.monotonic()<deadline:
   if proc.poll() is not None: raise RuntimeError('Server exited during restart')
   if 'Done (' in logpath.read_text(errors='replace')[offset:]: break
   time.sleep(.5)
  else: raise RuntimeError('Server restart timed out')
  deadline=time.monotonic()+10
  while connection is None:
   try: connection=socket.create_connection(('127.0.0.1',25585),timeout=5)
   except ConnectionRefusedError:
    if time.monotonic()>=deadline: raise
    time.sleep(.1)
  request(3,values['rcon.password'])
  if motor_tests: assert 'PASS=true' in command('teutonlib_energy_test lifecycle restart')
  if docking_tests:
   deadline=time.monotonic()+15
   while time.monotonic()<deadline:
    response=request(2,'teutonlib_docking_test probeReload')
    if 'PASS=true' in response: break
    time.sleep(.25)
   else: raise AssertionError('Docking restart failed: '+response)
   checks.append({'dockingAfterRestart':response})
   command('forceload remove 16000 16000 16016 16032')
   deadline=time.monotonic()+40
   while time.monotonic()<deadline:
    response=request(2,'execute if loaded 16008 240 16008 run say docking_chunk_still_loaded')
    if 'docking_chunk_still_loaded' not in response: break
    time.sleep(.5)
   else: raise AssertionError('Docking chunk did not unload')
   command('forceload add 16000 16000 16016 16032')
   deadline=time.monotonic()+15
   while time.monotonic()<deadline:
    response=request(2,'teutonlib_docking_test probeReload')
    if 'PASS=true' in response: break
    time.sleep(.25)
   else: raise AssertionError('Docking chunk reload failed: '+response)
   checks.append({'dockingAfterChunkReload':response})
   command('teutonlib_docking_test clearReload')
   command('forceload remove 16000 16000 16016 16032')
  command('stop')
  proc.wait(timeout=60)
  assert proc.returncode==0,f'Gradle restart exit {proc.returncode}'
 (report/f'server-{profile}.json').write_text(json.dumps({'profile':profile,'passed':True,'checks':checks},indent=2,ensure_ascii=False)+'\n')
 print('Server smoke checks passed.',flush=True)
except Exception:
 print('\n'.join(logpath.read_text(errors='replace').splitlines()[-75:]),flush=True)
 raise
finally:
 if connection:
  connection.close()
 if proc.poll() is None:
  os.killpg(proc.pid,signal.SIGTERM)
  try: proc.wait(timeout=25)
  except subprocess.TimeoutExpired:
   os.killpg(proc.pid,signal.SIGKILL);proc.wait()
 properties.write_text(original)
 log.close()
