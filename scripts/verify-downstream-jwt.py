"""Exercise each local resource server with JWTs supplied only over curl stdin."""
import json
import subprocess
import time
from sprint3_e2e import COMPOSE, ENV, SERVICE, login, payload, token_with, call

auth=login(ENV.get('ADMIN_USERNAME','admin'),ENV['ADMIN_PASSWORD'])
assert auth[0]==200
token=auth[1]['accessToken']; claims=payload(token)
report=[]
email_auth=call('POST','/api/v1/auth/login',body={'email':ENV.get('ADMIN_EMAIL','admin@logitrack.com'),
                                             'password':ENV['ADMIN_PASSWORD']})
assert email_auth[0]==200
report.append({'service':'security-service','case':'email-login-MySQL','status':200,'passed':True})
cases=[('absent',None),('tampered',token[:-10]+'AAAAAAAAAA')]
for name,changes in [('issuer',{'iss':'other'}),('expired',{'exp':int(time.time())-120}),
    ('subject',{'sub':'invalid'}),('roles-string',{'roles':'ADMIN'}),('roles-unknown',{'roles':['UNKNOWN']}),
    ('roles-empty',{'roles':[]})]:cases.append((name,token_with(claims|changes)))
no_exp=claims.copy();no_exp.pop('exp')
cases.extend([('missing-exp',token_with(no_exp)),('algorithm-HS256',token_with(claims,'HS256'))])
for service,port,path in [('security-service',8081,'/users/me'),('courier-service',8082,'/couriers'),
                           ('shipment-service',8083,'/envios')]:
    for name,bearer in [('valid',token)]+cases:
        config=f'url="http://localhost:{port}{path}"\nsilent\nwrite-out="\\n%{{http_code}}"\n'
        if bearer:config+=f'header="Authorization: Bearer {bearer}"\n'
        raw=subprocess.run(COMPOSE+['exec','-T',service,'curl','--config','-'],
            input=config.encode(),check=True,capture_output=True).stdout
        body,_,status=raw.rpartition(b'\n');code=int(status)
        assert code==(200 if name=='valid' else 401),(service,name,code)
        if code==401:
            data=json.loads(body)
            assert set(data)=={'status','error','mensaje','timestamp'}
            assert data['status']==401 and data['error']=='Unauthorized' and data['timestamp'].endswith('Z')
        report.append({'service':service,'case':name,'status':code,'passed':True})
(SERVICE/'docs/sprint3/evidencias/downstream-jwt-report.json').write_text(json.dumps(report,indent=2),encoding='utf8')
print('Direct resource-server JWT cases passed:',len(report))
