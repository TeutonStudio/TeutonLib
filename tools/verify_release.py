#!/usr/bin/env python3
"""Validate the locally built release archives and write SHA256SUMS."""
from pathlib import Path
import hashlib
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
props = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines()
             if '=' in line and not line.lstrip().startswith('#'))
version = props['mod_version']
name = props['mod_id']
jar = ROOT / f'build/libs/{name}-{version}.jar'
sources = ROOT / f'build/libs/{name}-{version}-sources.jar'
bundle = ROOT / f'build/distributions/{name}-{version}-computer-ui-template.zip'
allowed_java = {'src/main/java/de/TeutonStudio/TeutonLib/mixin/computer/client/DbwClientMixin.java'}
java = {str(p.relative_to(ROOT)) for parent in ('src', 'examples') for p in (ROOT / parent).rglob('*.java')}
assert java == allowed_java, f'Unexpected Java sources: {java - allowed_java}; missing required Java mixins: {allowed_java - java}'
with zipfile.ZipFile(jar) as archive:
    names = archive.namelist()
    class_files = {}
    for language in ('kotlin', 'java'):
        compiled = ROOT / f'build/classes/{language}/main'
        class_files.update({str(p.relative_to(compiled)): p for p in compiled.rglob('*.class')})
    assert set(class_files) == {n for n in names if n.endswith('.class')}, 'Release classes differ from tested output'
    for entry, path in class_files.items():
        assert archive.read(entry) == path.read_bytes(), f'Stale release class: {entry}'
    metadata = tomllib.loads(archive.read('META-INF/neoforge.mods.toml').decode())
    assert metadata['license'] == 'MIT'
    assert metadata['mods'][0]['version'] == version
    assert metadata['modLoader'] == 'kotlinforforge', 'KotlinForForge language loader required'
    kff_range = f"[{props['kotlin_for_forge_version']},6)"
    assert metadata['loaderVersion'] == kff_range
    kff = next(d for d in metadata['dependencies'][name] if d['modId'] == 'kotlinforforge')
    assert kff['type'] == 'required' and kff['side'] == 'BOTH', 'KotlinForForge must be mandatory on both sides'
    assert kff['versionRange'] == kff_range
    assert 'de/TeutonStudio/TeutonLib/client/TeutonLibClient.class' in names, 'Config editor registration missing'
    assert not any(n.endswith('.jar') for n in names), 'Foreign JAR embedded'
    assert not any('/peripheraltest/' in n or '/energytest/' in n or '/dockingtest/' in n or n.startswith('example/') for n in names), 'Verification code shipped'
    for license_name in ('LICENSE', 'TEMPLATE_LICENSE.txt'):
        assert archive.read('META-INF/' + license_name) == (ROOT / license_name).read_bytes()
with zipfile.ZipFile(sources) as archive:
    java_sources = {n for n in archive.namelist() if n.endswith('.java')}
    assert java_sources == {'de/TeutonStudio/TeutonLib/mixin/computer/client/DbwClientMixin.java'}, 'Unexpected Java sources shipped'
    assert any(n.endswith('.kt') for n in archive.namelist()), 'Kotlin sources missing'
    for license_name in ('LICENSE', 'TEMPLATE_LICENSE.txt'):
        assert archive.read('META-INF/' + license_name) == (ROOT / license_name).read_bytes()
with zipfile.ZipFile(bundle) as archive:
    assert archive.read('mods/' + jar.name) == jar.read_bytes()
    assert archive.read('sources/' + sources.name) == sources.read_bytes()
    assert any(n.startswith('examples/') and n.endswith('.kt') for n in archive.namelist())
    assert not any(n.endswith('.java') for n in archive.namelist()), 'Loose Java source shipped outside sources archive'
    assert f'docs/release-{version}.md' in archive.namelist()
    for license_name in ('LICENSE', 'TEMPLATE_LICENSE.txt'):
        assert archive.read(license_name) == (ROOT / license_name).read_bytes()
checksums = ''.join(f'{hashlib.sha256(p.read_bytes()).hexdigest()}  {p.name}\n' for p in (jar, sources, bundle))
(ROOT / 'build/distributions/SHA256SUMS').write_text(checksums)
print(f'PASS: Kotlin release {version} with isolated Java DbW mixin; metadata, licenses, archive contents and bundled artifacts verified')
print(checksums, end='')
