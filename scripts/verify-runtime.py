"""Inspect runtime contracts without exporting application configuration or secrets."""
import json
import subprocess
from sprint3_e2e import COMPOSE, SERVICE, call

report={}
config=call('GET','/security-service/default',base='http://localhost:8888')
assert config[0]==200
assert any('security.jwt.access-token-ttl' in source['source'] for source in config[1]['propertySources'])
report['config']={'name':config[1]['name'],'version':config[1]['version'],
                  'ttl_property_served':True,'profiles':config[1]['profiles']}
registry=call('GET','/eureka/apps',headers={'Accept':'application/json'},base='http://localhost:8065')
assert registry[0]==200
report['eureka']=[{'app':app['name'],'statuses':[i['status'] for i in app['instance']]}
                  for app in registry[1]['applications']['application']]
assert {app['app'] for app in report['eureka']}=={'API-GATEWAY','SECURITY-SERVICE','COURIER-SERVICE','SHIPMENT-SERVICE'}
assert all('UP' in app['statuses'] for app in report['eureka'])
services={'security-service':8081,'courier-service':8082,'shipment-service':8083}
expected={'security-service':'/auth/login','courier-service':'/couriers','shipment-service':'/envios'}
for service,port in services.items():
    raw=subprocess.check_output(COMPOSE+['exec','-T',service,'curl','--fail','--silent',f'http://localhost:{port}/v3/api-docs'])
    doc=json.loads(raw)
    assert doc['openapi'].startswith('3.') and expected[service] in doc['paths']
    assert doc['components']['securitySchemes']['bearerAuth']['scheme']=='bearer'
    ui=subprocess.check_output(COMPOSE+['exec','-T',service,'curl','--fail','--silent',f'http://localhost:{port}/swagger-ui/index.html'])
    assert b'swagger-ui' in ui and b'<html' in ui.lower()
    uid=subprocess.check_output(COMPOSE+['exec','-T',service,'id','-u'],text=True).strip()
    assert uid!='0'
    report[service]={'openapi':doc['openapi'],'paths':sorted(doc['paths']),
                     'bearer':doc['components']['securitySchemes']['bearerAuth'],'ui_http':200,'uid':uid}
    (SERVICE/f'docs/sprint3/evidencias/openapi-{service}.json').write_text(json.dumps(doc,indent=2),encoding='utf8')

raw_ps=subprocess.check_output(COMPOSE+['ps','--all','--format','json'],text=True).strip()
ps=json.loads(raw_ps) if raw_ps.startswith('[') else [json.loads(line) for line in raw_ps.splitlines()]
assert len(ps)==9
assert all(p['Health']=='healthy' and p['State']=='running' for p in ps)
report['compose']=[{'service':p['Service'],'state':p['State'],'health':p['Health'],'publishers':p.get('Publishers')} for p in ps]
for p in ps:
    if p['Service'] in ['security-service','courier-service','shipment-service','security-db','courier-db','shipment-db']:
        assert all(not publisher.get('PublishedPort') for publisher in (p.get('Publishers') or []))

sources=json.loads((SERVICE/'docs/sprint3/evidencias/image-sources.json').read_text(encoding='utf8'))
images=[]
for source in sources:
    inspect=json.loads(subprocess.check_output(['docker','image','inspect',source['image']],text=True))[0]
    container=next(p for p in ps if p['Service']==source['service'])
    running_image=subprocess.check_output(['docker','inspect',container['ID'],'--format','{{.Image}}'],text=True).strip()
    assert running_image==inspect['Id']
    assert inspect['Config']['Labels']['org.logitrack.source.sha256']==source['source_sha256']
    assert inspect['Config']['User'] not in ['', 'root','0']
    images.append({'service':source['service'],'image':source['image'],'id':inspect['Id'],
                   'architecture':inspect['Architecture'],'os':inspect['Os'],'user':inspect['Config']['User'],
                   'source_sha256':source['source_sha256'],'commit':source['base_commit']})
report['images']=images
(SERVICE/'docs/sprint3/evidencias/runtime-report.json').write_text(json.dumps(report,indent=2),encoding='utf8')
print('Runtime verified: 9 healthy, 3 OpenAPI documents, non-root images, internal business ports')
