"""Check publishable evidence against external secret values without printing them."""
from pathlib import Path
import re

root=Path(__file__).resolve().parents[2]
secret_values=[]
for line in (root/'.env').read_text(encoding='utf-8-sig').splitlines():
    if '=' not in line or line.lstrip().startswith('#'):continue
    key,value=line.split('=',1)
    if any(word in key.upper() for word in ['PASSWORD','SECRET','TOKEN']):
        value=value.strip().strip('"').strip("'")
        if len(value)>=6:secret_values.append(value)
findings=[]
for p in (root/'shipment-service/docs/sprint3').rglob('*'):
    if p.suffix not in ['.md','.json','.txt'] or p.name in ['contrato-s2.txt','consigna-s3.txt']:continue
    text=p.read_text(encoding='utf-8-sig')
    changed=text
    for value in secret_values:changed=changed.replace(value,'[REDACTED]')
    changed=re.sub(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+','[REDACTED_JWT]',changed)
    assert '-----BEGIN PRIVATE KEY-----' not in changed and '-----BEGIN RSA PRIVATE KEY-----' not in changed
    if text!=changed:
        p.write_text(changed,encoding='utf8'); findings.append(p.name)
print('Evidence checked; files requiring redaction:',len(findings))
