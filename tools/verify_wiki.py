#!/usr/bin/env python3
"""Check bilingual Wiki navigation, media integrity, provenance and concise README."""
from pathlib import Path
import json
import re
import struct
import subprocess
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
WIKI = ROOT / 'docs/wiki'
PREFIX = 'https://raw.githubusercontent.com/wiki/TeutonStudio/TeutonLib/'
LINK = re.compile(r'(?<!!)\[[^\]]+\]\(([^)]+)\)')
IMAGE = re.compile(r'!\[[^\]]+\]\(([^)]+)\)')

def anchor(text):
    text = re.sub(r'[`*\[\]]', '', text).strip().lower()
    return ''.join(c for c in text if c.isalnum() or c in ' -_').replace(' ', '-')

def verify():
    errors, used = [], set()
    inventory = json.loads((ROOT / 'docs/wiki-media-coverage.json').read_text())
    registered = {m['path']: m for m in inventory['media']}
    feature_pages = {f[k] for f in inventory['features'] for k in ('page', 'english_page')}
    pages = list(WIKI.glob('*.md'))
    expected = feature_pages | {'Home.md','English.md','_Sidebar.md','_Footer.md','Bildnachweise.md','Bildnachweise-en.md'}
    if {p.name for p in pages} != expected:
        errors.append('Guide inventory and actual Wiki pages differ')
    for page in pages:
        text = page.read_text()
        # Fences, unlike a split on ``` text, allow language identifiers.
        if len(re.findall(r'^```',text,re.M)) % 2:
            errors.append(f'{page.name}: unbalanced code fences')
        for url in LINK.findall(text):
            if '://' in url or url.startswith('mailto:'):
                continue
            name, _, fragment = unquote(url).partition('#')
            target = WIKI / ((name.removesuffix('.md') or page.stem) + '.md')
            if not target.is_file():
                errors.append(f'{page.name}: missing page {url}')
            elif fragment:
                headings = {anchor(h) for h in re.findall(r'^#+ (.+)$',target.read_text(),re.M)}
                if fragment not in headings:
                    errors.append(f'{page.name}: missing anchor {url}')
        for url in IMAGE.findall(text):
            if not url.startswith(PREFIX):
                errors.append(f'{page.name}: use canonical Wiki raw URL for {url}')
                continue
            relative = unquote(url[len(PREFIX):])
            used.add(relative)
            path = WIKI / relative
            if not path.is_file():
                errors.append(f'{page.name}: missing image {relative}')
                continue
            header = path.read_bytes()[:24]
            if header.startswith(b'\x89PNG\r\n\x1a\n'):
                width,height=struct.unpack('>II',header[16:24])
            elif header.startswith((b'GIF87a',b'GIF89a')):
                width,height=struct.unpack('<HH',header[6:10])
            else:
                errors.append(f'{page.name}: invalid PNG/GIF {relative}')
                continue
            if min(width,height)<100 or path.stat().st_size>10*1024*1024:
                errors.append(f'{page.name}: unsuitable image size {relative}')
            if relative not in registered:
                errors.append(f'{page.name}: missing provenance {relative}')
    for feature in inventory['features']:
        de,en=feature['page'],feature['english_page']
        if feature['status']!='illustrated' or not feature['media']:
            errors.append(f'{de}: incomplete illustration')
        for name,other in ((de,en),(en,de)):
            path=WIKI/name
            if not path.exists():continue
            text=path.read_text()
            if f']({Path(other).stem})' not in text:
                errors.append(f'{name}: missing reciprocal language link')
            images={unquote(u[len(PREFIX):]) for u in IMAGE.findall(text) if u.startswith(PREFIX)}
            if not set(feature['media']).issubset(images):
                errors.append(f'{name}: feature media not embedded')
    for medium in inventory['media']:
        if not medium.get('source') or not medium.get('scope') or not medium.get('kind'):
            errors.append(f"Incomplete media provenance: {medium['path']}")
    # All version-pinned repository references must exist at that tag. Master links
    # are checked against the staged documentation/current checkout.
    refs=set()
    for page in pages:
        refs.update(re.findall(r'https://github\.com/TeutonStudio/TeutonLib/(?:blob|tree)/([^/\s)]+)/([^\s)#]+)',page.read_text()))
    for ref,path in sorted(refs):
        if ref=='master':
            exists=(ROOT/unquote(path)).exists()
        else:
            result=subprocess.run(['git','cat-file','-e',f'{ref}:{unquote(path)}'],cwd=ROOT,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            exists=result.returncode==0
        if not exists:errors.append(f'Missing source reference: {ref}/{path}')
    readme=(ROOT/'README.md').read_text()
    if len(readme.split())>130 or '/wiki/English' not in readme or '/wiki)' not in readme:
        errors.append('README must be concise and link both Wiki languages')
    if errors:
        print('\n'.join('FAIL: '+e for e in errors));return 1
    print(f'PASS: {len(inventory["features"])} bilingual guide pairs, {len(used)} embedded PNG/GIF files, {len(refs)} source references; README {len(readme.split())} words.')
    print('PASS: reciprocal language links, internal destinations, image integrity and media provenance. Desktop contract/source evidence is not counted as an Android device test.')
    return 0

if __name__=='__main__':raise SystemExit(verify())
