"""Run against the isolated compose.sprint3.yml stack; reports never contain secrets."""
import base64
import concurrent.futures
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import urllib.request
import urllib.error
import uuid

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / 'shipment-service'
OUT = SERVICE / 'docs/sprint3/evidencias/e2e-report.json'
REPORT = []
ENV = {}
for line in (ROOT / '.env').read_text(encoding='utf-8-sig').splitlines():
    if '=' in line and not line.lstrip().startswith('#'):
        key, value = line.split('=', 1)
        ENV[key.strip()] = value.strip().strip('"').strip("'")
BASE = os.environ.get('S3_BASE_URL', 'http://localhost:8066')
COMPOSE = ['docker', 'compose', '--env-file', str(ROOT / '.env'), '-f', str(SERVICE / 'compose.sprint3.yml')]

def call(method, path, token=None, body=None, headers=None, base=BASE):
    hdr = {'Content-Type': 'application/json'}
    if token: hdr['Authorization'] = 'Bearer ' + token
    hdr.update(headers or {})
    request = urllib.request.Request(base + path, method=method, headers=hdr,
            data=json.dumps(body).encode() if body is not None else None)
    try:
        response = urllib.request.urlopen(request, timeout=25)
    except urllib.error.HTTPError as ex:
        response = ex
    raw = response.read()
    try: data = json.loads(raw)
    except (ValueError, UnicodeError): data = None
    return response.status, data, dict(response.headers)

def check(name, result, expected, assertion=None):
    code, body, _ = result
    passed = code == expected and (assertion(body) if assertion else True)
    REPORT.append({'scenario': name, 'expected_status': expected, 'actual_status': code,
                   'assertions_passed': bool(passed)})
    OUT.write_text(json.dumps(REPORT, indent=2), encoding='utf8')
    if not passed:
        raise AssertionError(f'{name}: status={code}, expected={expected}; content assertion={bool(passed)}')
    return body

def payload(token):
    return json.loads(base64.urlsafe_b64decode(token.split('.')[1] + '=='))

def login(username, password):
    return call('POST', '/api/v1/auth/login', body=dict(username=username, password=password))

def token_with(claims, algorithm='RS256'):
    from cryptography.hazmat.primitives import serialization, hashes
    from cryptography.hazmat.primitives.asymmetric import padding
    def encode(value): return base64.urlsafe_b64encode(json.dumps(value, separators=(',', ':')).encode()).rstrip(b'=')
    unsigned = encode({'alg': algorithm, 'typ': 'JWT'}) + b'.' + encode(claims)
    if algorithm == 'RS256':
        key = serialization.load_pem_private_key((ROOT/'logitrack-local-secrets/security/private.pem').read_bytes(), password=None)
        signature = key.sign(unsigned, padding.PKCS1v15(), hashes.SHA256())
    else:
        signature = hmac.new(b'test-only-not-production-secret', unsigned, hashlib.sha256).digest()
    return (unsigned + b'.' + base64.urlsafe_b64encode(signature).rstrip(b'=')).decode()

def main():
    run = uuid.uuid4().hex[:12]
    password = secrets.token_urlsafe(20)
    admin_login = check('A-01 / S3-03 ADMIN login', login(ENV.get('ADMIN_USERNAME', 'admin'), ENV['ADMIN_PASSWORD']), 200,
            lambda b: b['tokenType'] == 'Bearer' and b['expiresIn'] > 0)
    admin = admin_login['accessToken']
    claims = payload(admin)
    assert claims['exp'] - claims['iat'] == admin_login['expiresIn']
    assert json.loads(base64.urlsafe_b64decode(admin.split('.')[0]+'=='))['alg'] == 'RS256'
    check('S3-04 users/me', call('GET', '/api/v1/users/me', admin), 200, lambda b: b['id'] == claims['sub'])
    client_name = 'cliente_' + run
    client = check('PREP-01 / S3-05 register CLIENTE', call('POST', '/api/v1/auth/register', body={
        'username': client_name, 'email': run+'@example.invalid', 'password': password, 'role': 'ADMIN'}), 201,
        lambda b: b['role'] == 'CLIENTE')
    client_login = check('PREP-02 CLIENTE login', login(client_name, password), 200)
    client_token = client_login['accessToken']
    check('A-02 / S3-09 missing JWT', call('GET', '/api/v1/couriers'), 401, lambda b: b['status'] == 401)
    tampered = admin[:-8] + ('AAAAAAAA' if admin[-8:] != 'AAAAAAAA' else 'BBBBBBBB')
    check('A-03 tampered JWT', call('GET', '/api/v1/couriers', tampered), 401)
    check('A-05 ADMIN couriers', call('GET', '/api/v1/couriers', admin), 200, lambda b: isinstance(b, list))
    courier_user = check('S3-06 ADMIN creates REPARTIDOR', call('POST', '/api/v1/users', admin, {
        'username': 'courier_'+run, 'email': 'courier_'+run+'@example.invalid', 'password': password, 'role':'REPARTIDOR'}), 201)
    courier_id = courier_user['id']
    courier = check('S3-06 create Courier profile', call('POST','/api/v1/couriers',admin,{
        'userId':courier_id,'nombre':'Courier de prueba','tipoVehiculo':'Moto'}),201)
    repartidor = check('REPARTIDOR login', login('courier_'+run,password),200)['accessToken']
    direction = {'calle':'Belgrano','numero':'123','ciudad':'Jujuy','codigoPostal':'4600'}
    shipment_body = {'origen':direction,'destino':direction,'peso':1,'ancho':1,'alto':1,'largo':1,
                     'clienteId': str(uuid.uuid4())}
    def shipment():
        return check('S3-07 create owned Shipment',call('POST','/api/v1/shipments',client_token,shipment_body),201,
                     lambda b:b['clienteId']==client['id'])
    envio=shipment(); path='/api/v1/shipments/'+envio['id']
    check('ADMIN registers for existing CLIENTE',call('POST','/api/v1/shipments',admin,
          shipment_body|{'clienteId':client['id']}),201,lambda b:b['clienteId']==client['id'])
    check('ADMIN rejects REPARTIDOR as CLIENTE',call('POST','/api/v1/shipments',admin,
          shipment_body|{'clienteId':courier_id}),400)
    check('ADMIN rejects nonexistent CLIENTE',call('POST','/api/v1/shipments',admin,
          shipment_body|{'clienteId':str(uuid.uuid4())}),404)
    assign={'repartidorId':courier_id}
    check('A-04 / S3-10 CLIENTE cannot assign',call('PUT',path+'/repartidor',client_token,assign),403)
    check('S3-08 Feign assignment',call('PUT',path+'/repartidor',admin,assign),200,lambda b:b['repartidorId']==courier_id)
    check('S3-11 repeated PUT no duplicate',call('PUT',path+'/repartidor',admin,assign),409)
    check('S3-12 REPARTIDOR assigned visibility',call('GET',path,repartidor),200)
    check('S3-12 REPARTIDOR cannot create Shipment',call('POST','/api/v1/shipments',repartidor,shipment_body),403)
    other_name='other_'+run
    check('prepare other CLIENTE',call('POST','/api/v1/auth/register',body={
        'username':other_name,'email':other_name+'@example.invalid','password':password}),201)
    other=check('other CLIENTE login',login(other_name,password),200)['accessToken']
    check('S3-13 owner isolation read',call('GET',path,other),403)
    check('S3-13 owner isolation cancel',call('PATCH',path+'/cancelacion',other),403)
    check('S3-14 nonexistent Courier',call('GET','/api/v1/couriers/'+str(uuid.uuid4()),admin),404)
    missing=shipment()
    check('S3-14 Feign nonexistent Courier',call('PUT','/api/v1/shipments/'+missing['id']+'/repartidor',admin,
          {'repartidorId':str(uuid.uuid4())}),404)
    check('set Courier inactive',call('PATCH','/api/v1/couriers/'+courier_id+'/estado',admin,{'estado':'INACTIVO'}),200)
    inactive=shipment()
    check('S3-15 inactive Courier conflict',call('PUT','/api/v1/shipments/'+inactive['id']+'/repartidor',admin,assign),409)
    check('set Courier active',call('PATCH','/api/v1/couriers/'+courier_id+'/estado',admin,{'estado':'ACTIVO'}),200)
    concurrent=shipment(); concurrent_path='/api/v1/shipments/'+concurrent['id']+'/repartidor'
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        statuses=sorted(pool.map(lambda _:call('PUT',concurrent_path,admin,assign)[0],range(2)))
    assert statuses==[200,409], statuses
    REPORT.append({'scenario':'S3-16 concurrent assignment','actual_statuses':statuses,'assertions_passed':True})
    check('S3-17 bounded pagination',call('GET','/api/v1/shipments?size=2',admin),200,lambda b:len(b)<=2)
    check('S3-17 page limit',call('GET','/api/v1/shipments?size=101',admin),400)
    check('S3-17 negative page',call('GET','/api/v1/couriers?page=-1',admin),400)
    check('S3-17 empty page',call('GET','/api/v1/shipments?page=999',admin),200,lambda b:b==[])
    for name, changes in [('issuer',{'iss':'other'}),('expired',{'exp':int(time.time())-120}),
        ('subject',{'sub':'invalid'}),('roles-string',{'roles':'ADMIN'}),('roles-unknown',{'roles':['UNKNOWN']}),
        ('roles-empty',{'roles':[]})]:
        check('JWT negative '+name,call('GET','/api/v1/couriers',token_with(claims|changes)),401)
    without_exp=claims.copy(); without_exp.pop('exp')
    check('JWT missing exp',call('GET','/api/v1/couriers',token_with(without_exp)),401)
    check('JWT forbidden HS256',call('GET','/api/v1/couriers',token_with(claims,'HS256')),401)
    check('deny unknown route',call('GET','/api/v1/unknown',admin),403)
    check('deny unknown method',call('DELETE',path,admin),403)
    check('S3-18 CORS preflight',call('OPTIONS','/api/v1/couriers',headers={
        'Origin':'http://localhost:5173','Access-Control-Request-Method':'GET',
        'Access-Control-Request-Headers':'Authorization'}),200)
    check('CORS rejects unknown origin',call('OPTIONS','/api/v1/couriers',headers={
        'Origin':'https://unknown.example.invalid','Access-Control-Request-Method':'GET'}),403)
    outage=shipment(); outage_path='/api/v1/shipments/'+outage['id']
    try:
        subprocess.run(COMPOSE+['stop','-t','10','courier-service'],check=True,capture_output=True)
        check('S3-19 Courier outage controlled',call('PUT',outage_path+'/repartidor',admin,assign),503)
        check('S3-19 outage no write',call('GET',outage_path,admin),200,lambda b:b.get('repartidorId') is None)
    finally:
        subprocess.run(COMPOSE+['start','courier-service'],check=True,capture_output=True)
    for attempt in range(45):
        result=call('PUT',outage_path+'/repartidor',admin,assign)
        if result[0]==200: break
        time.sleep(2)
    check('S3-20 Courier recovery assignment',result,200)
    check('S3-01 Config environment',call('GET','/shipment-service/default',base='http://localhost:8888'),200,
          lambda b:b.get('name')=='shipment-service')
    check('S3-02 Eureka UP',call('GET','/eureka/apps',headers={'Accept':'application/json'},base='http://localhost:8065'),200,
        lambda b:all(any(i['status']=='UP' for i in a['instance']) for a in b['applications']['application']))
    OUT.write_text(json.dumps(REPORT,indent=2),encoding='utf8')
    print('E2E assertions passed:',len(REPORT),'; report contains no tokens or credentials')

if __name__=='__main__':
    try: main()
    except Exception as ex:
        REPORT.append({'scenario':'RUN FAILED','error_type':type(ex).__name__,'assertions_passed':False})
        OUT.write_text(json.dumps(REPORT,indent=2),encoding='utf8')
        raise
