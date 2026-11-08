"""Publish actual Surefire results and sanitized execution excerpts, without raw response bodies."""
from pathlib import Path
import json
import re
import subprocess
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[2]
out=root/'shipment-service/docs/sprint3/evidencias'
report={}
for repo in ['security-service','courier-service','shipment-service','api-gateway','eureka-server','config-server']:
    suites=[]
    for file in (root/repo/'target/surefire-reports').glob('TEST-*.xml'):
        suite=ET.parse(file).getroot()
        suites.append({key:suite.attrib.get(key) for key in ['name','tests','failures','errors','skipped','time']})
    report[repo]={'commit':subprocess.check_output(['git','-C',str(root/repo),'rev-parse','HEAD'],text=True).strip(),
                  'suites':suites,'tests':sum(int(s['tests'] or 0) for s in suites),
                  'failures':sum(int(s['failures'] or 0) for s in suites),
                  'errors':sum(int(s['errors'] or 0) for s in suites)}
    assert report[repo]['failures']==report[repo]['errors']==0
(out/'surefire-summary.json').write_text(json.dumps(report,indent=2),encoding='utf8')

for name in ['docker-build-verified-source','closure-final-start','e2e-recovery-final','apidog-recovery-final',
             'runtime-release-final','scale-run','persistence-run','downstream-run','publish-run','release-pull']:
    path=out/(name+'.log')
    if path.exists():
        lines=path.read_text(encoding='utf8',errors='replace').splitlines()
        # Actual excerpts with trailing whitespace trimmed, not reconstructed output.
        excerpt='\n'.join(line.rstrip() for line in lines[-35:])+'\n'
        excerpt=re.sub(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+','[REDACTED_JWT]',excerpt)
        (out/(name+'-excerpt.txt')).write_text(excerpt,encoding='utf8')
print('Surefire results exported; only actual counts and sanitized excerpts are published')
