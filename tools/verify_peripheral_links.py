#!/usr/bin/env python3
"""Exercise a disposable localhost-only Minecraft server (Python standard library only).

Usage: python3 tools/verify_peripheral_links.py [cc|console]
Pass --dbw to additionally exercise Drive By Wire 0.3.0.
Needs the same local dependencies as ./gradlew runServer. Profiles run sequentially.
The generated worlds live under run/peripheral/<profile>, separate from development worlds.
"""
from pathlib import Path
import subprocess,time,os,signal,socket,struct,secrets,sys,json
root=Path(__file__).resolve().parents[1]
profile=sys.argv[1] if len(sys.argv)>1 else 'cc'
dbw='--dbw' in sys.argv
channels_only='--channels-only' in sys.argv
report_name=f'peripheral-{profile}'+('-channels' if channels_only else '')
report=root/'build/reports/smoke';report.mkdir(parents=True,exist_ok=True)
(report/f'{report_name}.json').unlink(missing_ok=True)
if profile not in ('cc', 'console'):
 raise SystemExit('Profile must be cc or console')
instance=root/'run/peripheral'/profile
instance.mkdir(parents=True,exist_ok=True)
if not (instance/'eula.txt').exists():
 accepted=root/'run/smoke/all/eula.txt'
 if accepted.exists(): (instance/'eula.txt').write_text(accepted.read_text())
properties=instance/'server.properties'
original=properties.read_text() if properties.exists() else 'server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\n'
config=instance/'world/serverconfig/teutonlib-server.toml'
if config.exists():
 config.write_text(config.read_text().replace('enabled = false','enabled = true'))
values={'server-ip':'127.0.0.1','enable-rcon':'true','rcon.password':secrets.token_hex(16),'rcon.port':'25586','broadcast-rcon-to-ops':'false'}
lines=original.splitlines()
for key,value in values.items():
 prefix=key+'='
 lines=[line for line in lines if not line.startswith(prefix)]
 lines.append(prefix+value)
properties.write_text('\n'.join(lines)+'\n')
logpath=report/f'{report_name}.log'
log=logpath.open('w')
extra_args = ['-PperipheralTests', '-PperipheralRuntime='+profile] + (['-PdbwTests'] if dbw else [])
launch_args=['./gradlew','runSmokeServer','-PcsoRuntime='+('all' if profile=='console' or dbw else 'create'), '-PperipheralTestDir='+str(instance),*extra_args,'--offline','--no-daemon','--console=plain']
proc=subprocess.Popen(launch_args,cwd=root,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
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
  try: connection=socket.create_connection(('127.0.0.1',25586),timeout=5)
  except ConnectionRefusedError:
   if time.monotonic()>=deadline: raise
   time.sleep(.1)
 request(3,values['rcon.password'])
 def wait_for(expected, console=False):
  deadline=time.monotonic()+35
  while time.monotonic()<deadline:
   result=request(2,'teutonlib_link_test '+('console_result' if console else 'result')).strip()
   if result==expected:
    checks.append({'phase':expected}); print(expected,flush=True); return
   if 'FAIL' in result: raise AssertionError(result)
   time.sleep(.25)
  raise AssertionError('Timed out waiting for '+expected+': '+result)
 def channel_wait(expected, suffix='result'):
  deadline=time.monotonic()+30
  while time.monotonic()<deadline:
   result=request(2,'teutonlib_link_test channels_'+suffix).strip()
   if result==expected: checks.append({'channels':expected}); print('Channels '+expected,flush=True); return
   if 'FAIL' in result: raise AssertionError(result)
   time.sleep(.25)
  raise AssertionError('Channels timeout: '+expected+' '+result)
 for family in ('normal','advanced'):
  assert 'STARTED' in command('teutonlib_link_test channels_'+family)
  channel_wait('READY'); channel_wait('DIMENSION_PASS','dimension')
  for phase in ('target_modem','reconnected','local_modem','reconnected','link_modem','offline'):
   command('teutonlib_link_test channels_'+phase); channel_wait('PASS '+phase)
  command('teutonlib_link_test channels_ui_prepare')
  channel_wait('UI_READY', 'ui_ready')
  assert 'PASS inspect' in command('teutonlib_link_test channels_inspect')
 if channels_only:
  command('teutonlib_link_test channels_unload'); channel_wait('PASS unloaded','unloaded')
  command('teutonlib_link_test channels_reload'); channel_wait('PASS restored','restored')
 if not channels_only:
  for family in ('normal','advanced'):
   assert 'STARTED' in command('teutonlib_link_test '+family)
   wait_for('READY')
   command('teutonlib_link_test detach'); wait_for('DETACHED')
   command('teutonlib_link_test reconnect'); wait_for('PASS')
   assert 'PASS conflict' in command('teutonlib_link_test conflict')
  assert 'STARTED' in command('teutonlib_link_test chunk_prepare')
  for action in ('chunk_unloaded','chunk_reconnected'):
   if action=='chunk_reconnected': command('teutonlib_link_test chunk_reload')
   deadline=time.monotonic()+45
   while time.monotonic()<deadline:
    result=request(2,'teutonlib_link_test '+action).strip()
    if result=='PASS '+action: checks.append({'phase':action});print(result,flush=True);break
    if 'FAIL' in result: raise AssertionError(result)
    time.sleep(.5)
   else: raise AssertionError(action+' timed out: '+result)
  if profile=='console':
   assert 'PASS sable' in command('teutonlib_link_test sable')
   for family in ('normal','advanced'):
    assert 'STARTED' in command('teutonlib_link_test console_'+family)
    wait_for('READY',True)
    assert 'PASS workspace' in command('teutonlib_link_test console_workspace')
    command('teutonlib_link_test console_split');wait_for('DETACHED',True)
    command('teutonlib_link_test console_merge');wait_for('PASS',True)
    assert 'PASS one_attachment' in command('teutonlib_link_test console_attachments')
    command('teutonlib_link_test console_reboot');wait_for('READY',True)
    command('teutonlib_link_test console_split');wait_for('DETACHED',True)
    command('teutonlib_link_test console_merge');wait_for('PASS',True)
    assert 'PASS one_attachment' in command('teutonlib_link_test console_attachments')
 command('stop'); proc.wait(timeout=35)
 assert proc.returncode==0
 connection.close(); connection=None
 offset=logpath.stat().st_size
 proc=subprocess.Popen(launch_args,cwd=root,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
 deadline=time.monotonic()+150
 while time.monotonic()<deadline:
  if proc.poll() is not None: raise RuntimeError('Restart exited')
  with logpath.open('r',errors='replace') as stream:
   stream.seek(offset); fresh=stream.read()
  if 'Done (' in fresh: break
  time.sleep(.5)
 else: raise RuntimeError('Restart timed out')
 deadline=time.monotonic()+10
 while connection is None:
  try: connection=socket.create_connection(('127.0.0.1',25586),timeout=5)
  except ConnectionRefusedError:
   if time.monotonic()>deadline: raise
   time.sleep(.1)
 request(3,values['rcon.password'])
 channel_wait('PASS restart','restart')
 if not channels_only: assert 'PASS persistence' in command('teutonlib_link_test persistence')
 command('stop');proc.wait(timeout=35)
 assert proc.returncode==0
 (report/f'{report_name}.json').write_text(json.dumps({'profile':profile,'dbw':dbw,'passed':True,'checks':checks},indent=2)+'\n')
 print('Peripheral checks passed.',flush=True)

except Exception:
 print('\n'.join(logpath.read_text(errors='replace').splitlines()[-75:]),flush=True)
 raise
finally:
 if connection:
  try: request(2,'stop')
  except (OSError,RuntimeError): pass
  connection.close()
  try: proc.wait(timeout=35)
  except subprocess.TimeoutExpired: pass
 if proc.poll() is None:
  os.killpg(proc.pid,signal.SIGTERM)
  try: proc.wait(timeout=25)
  except subprocess.TimeoutExpired:
   os.killpg(proc.pid,signal.SIGKILL);proc.wait()
 properties.write_text(original)
 log.close()
