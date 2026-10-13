#!/usr/bin/env python3
"""Snapshot current deployable source, including uncommitted/untracked source. No Git writes."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
from datetime import datetime, timezone

root = Path(__file__).resolve().parents[1]
destination = Path(sys.argv[1]).resolve()
destination.mkdir(parents=True, exist_ok=False)
names = subprocess.check_output(['git', 'ls-files', '--cached', '--others', '--exclude-standard', '-z'], cwd=root).decode().split('\0')
top = {'Dockerfile', '.dockerignore', '.env.prod.example', 'compose.prod.yml', 'compose.release.yml',
       'package.json', 'package-lock.json', 'angular.json', 'tsconfig.json', 'tsconfig.app.json', 'tsconfig.spec.json',
       'backend/pom.xml', 'backend/Dockerfile', 'backend/.dockerignore'}
prefixes = ('src/', 'public/', 'backend/src/', 'ops/')
hashes = {}
for name in sorted(set(names)):
    p = root / name
    if not name or not p.is_file(): continue
    if '__pycache__' in p.parts or p.suffix == '.pyc': continue
    if name not in top and not name.startswith(prefixes) and name not in ('docker/nginx.prod.conf', 'docker/nginx.prod.conf.template'): continue
    if p.is_symlink(): raise SystemExit('No se permiten enlaces en el paquete: ' + name)
    if p.name.startswith('.env') and name != '.env.prod.example': raise SystemExit('Configuración privada inesperada')
    target = destination / name
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(p, target)
    hashes[name] = hashlib.sha256(target.read_bytes()).hexdigest()
manifest = ''.join(f'{digest}  {name}\n' for name, digest in hashes.items())
(destination / 'SOURCE.sha256').write_text(manifest)
release_hash = hashlib.sha256(manifest.encode()).hexdigest()
metadata = {
    'release': 'sm-' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + release_hash[:12],
    'source_sha256': release_hash,
    'git_head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root).decode().strip(),
    'git_branch': subprocess.check_output(['git', 'branch', '--show-current'], cwd=root).decode().strip(),
    'git_status': subprocess.check_output(['git', 'status', '--short'], cwd=root).decode(),
    'includes_uncommitted_source': True,
}
(destination / 'RELEASE.json').write_text(json.dumps(metadata, indent=2) + '\n')
print(json.dumps({k:v for k,v in metadata.items() if k != 'git_status'}, indent=2))
