"""Publish immutable new closure tags using the existing Docker CLI login."""
import json
from pathlib import Path
import re
import subprocess
import sys

service=Path(__file__).resolve().parents[1]
evidence=service/'docs/sprint3/evidencias'
sources=json.loads((evidence/'image-sources.json').read_text(encoding='utf8'))
selected=set(sys.argv[1:]) or {s['service'] for s in sources}
published=[]
if (evidence/'published-images.json').exists():
    published=[p for p in json.loads((evidence/'published-images.json').read_text(encoding='utf8'))
               if p['service'] not in selected]
for source in sources:
    if source['service'] not in selected:
        assert next(p for p in published if p['service']==source['service'])['source_sha256']==source['source_sha256']
        continue
    target='facutolaba/logitrack-'+source['service']+':0.3.1-s3-'+source['source_sha256'][:12]
    subprocess.run(['docker','tag',source['image'],target],check=True)
    push=subprocess.run(['docker','push',target],capture_output=True,text=True)
    (evidence/('push-'+source['service']+'.log')).write_text(push.stdout+push.stderr,encoding='utf8')
    if push.returncode:raise RuntimeError('Registry push failed for '+source['service']+'; see local log')
    matches=re.findall(r'digest: (sha256:[0-9a-f]{64})',push.stdout+push.stderr)
    assert matches,'Registry did not return a digest'
    digest=matches[-1]
    remote=target.split(':')[0]+'@'+digest
    # Pull verifies that the published artifact is downloadable by its immutable digest.
    pull=subprocess.run(['docker','pull',remote],capture_output=True,text=True,check=True)
    image_id=subprocess.check_output(['docker','image','inspect',remote,'--format','{{.Id}}'],text=True).strip()
    local_id=subprocess.check_output(['docker','image','inspect',source['image'],'--format','{{.Id}}'],text=True).strip()
    assert image_id==local_id
    published.append({'service':source['service'],'tag':target,'digest':digest,
                      'image':remote,'image_id':image_id,'source_sha256':source['source_sha256'],
                      'push_verified':True,'pull_verified':True})
    (evidence/'published-images.json').write_text(json.dumps(published,indent=2),encoding='utf8')
    print(source['service']+': pushed and pulled '+digest,flush=True)

text=(service/'compose.sprint3.yml').read_text(encoding='utf8')
text=re.sub(r'    build:\n      context: .*\n      args:\n        SOURCE_DIGEST: .*\n','',text)
published=[next(p for p in published if p['service']==s['service']) for s in sources]
(evidence/'published-images.json').write_text(json.dumps(published,indent=2),encoding='utf8')
for source,publication in zip(sources,published):text=text.replace(source['image'],publication['image'])
mysql_digest=subprocess.check_output(['docker','image','inspect','mysql:8.4','--format','{{index .RepoDigests 0}}'],text=True).strip()
assert '@sha256:' in mysql_digest
text=text.replace('image: mysql:8.4','image: '+mysql_digest)
(service/'compose.sprint3.release.yml').write_text(text,encoding='utf8')
print('Image-only release Compose generated; no build entries')
