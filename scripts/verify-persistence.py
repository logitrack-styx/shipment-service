"""Restart only the isolated closure stack and verify retained identities and data."""
import json
import subprocess
import time
from sprint3_e2e import COMPOSE, ENV, SERVICE, call, login

def count(service,table,where=''):
    command=COMPOSE+['exec','-T',service,'sh','-c',
        'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" --batch --skip-column-names']
    return int(subprocess.run(command,input=f'SELECT COUNT(*) FROM {table} {where};'.encode(),
               check=True,capture_output=True).stdout.strip())

auth=login(ENV.get('ADMIN_USERNAME','admin'),ENV['ADMIN_PASSWORD'])
assert auth[0]==200
token=auth[1]['accessToken']
before_user=call('GET','/api/v1/users/me',token)[1]['id']
before_count=count('shipment-db','envios')
before_admin=count('security-db','usuarios',"WHERE role='ADMIN'")
subprocess.run(COMPOSE+['restart','security-service','shipment-db'],check=True,capture_output=True)
for _ in range(60):
    try:
        user=call('GET','/api/v1/users/me',token)
        shipments=call('GET','/api/v1/shipments?size=1',token)
        if user[0]==200 and shipments[0]==200:break
    except (OSError,ValueError):pass
    time.sleep(2)
assert user[0]==200 and user[1]['id']==before_user
assert shipments[0]==200
after_count=count('shipment-db','envios')
after_admin=count('security-db','usuarios',"WHERE role='ADMIN'")
assert before_count==after_count and before_admin==after_admin==1
(SERVICE/'docs/sprint3/evidencias/persistence-report.json').write_text(json.dumps({
    'restarted':['security-service','shipment-db'],'admin_count_before':before_admin,'admin_count_after':after_admin,
    'same_admin_id':True,'shipment_rows_before':before_count,'shipment_rows_after':after_count,
    'authenticated_gateway_after_restart':True},indent=2),encoding='utf8')
print('Persistence verified: same ADMIN, no duplicate bootstrap, shipment rows retained')
