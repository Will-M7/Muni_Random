#!/usr/bin/env python3
"""Build from a frozen source directory and export runtime images for offline installation."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys

root = Path(sys.argv[1]).resolve()
metadata = json.loads((root/'RELEASE.json').read_text())
subprocess.run(['sha256sum','-c','SOURCE.sha256'],cwd=root,check=True,stdout=subprocess.DEVNULL)
version = metadata['release']
tags = {'BACKEND_IMAGE':'san-miguel-backend:'+version, 'FRONTEND_IMAGE':'san-miguel-frontend:'+version, 'MYSQL_IMAGE':'mysql:8.4'}
for key, context in [('BACKEND_IMAGE',root/'backend'),('FRONTEND_IMAGE',root)]:
    subprocess.run(['docker','build','-t',tags[key],str(context)],check=True)
images = {key:json.loads(subprocess.check_output(['docker','image','inspect',tag]))[0] for key,tag in tags.items()}
if any(image['Architecture'] != 'amd64' or image['Os'] != 'linux' for image in images.values()):
    raise SystemExit('Esta entrega requiere imágenes linux/amd64; no mezclar arquitecturas.')
(root/'images.lock.env').write_text(''.join(key+'='+image['Id']+'\n' for key,image in images.items()))
inspection = {key:{field:image.get(field) for field in ('Id','RepoTags','RepoDigests','Architecture','Os','Created')} for key,image in images.items()}
(root/'IMAGE-INSPECT.json').write_text(json.dumps(inspection,indent=2)+'\n')
subprocess.run(['docker','save','-o',str(root/'images.tar'),*tags.values()],check=True)
def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''): h.update(block)
    return h.hexdigest()
(root/'IMAGES.sha256').write_text(''.join(digest(root/name)+'  '+name+'\n' for name in ('images.tar','images.lock.env','IMAGE-INSPECT.json','RELEASE.json')))
print('Imágenes exportadas y fijadas por SHA256: '+str(root))
