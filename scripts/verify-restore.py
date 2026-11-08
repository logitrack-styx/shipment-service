"""Verify backup restoration into a new scratch schema on the isolated Shipment MySQL."""
import json
import subprocess
import uuid
from sprint3_e2e import COMPOSE, SERVICE

assert 'compose.sprint3.yml' in ' '.join(COMPOSE)
scratch='logitrack_restorecheck_'+uuid.uuid4().hex[:10]
def query(sql,database=None):
    command=COMPOSE+['exec','-T','shipment-db','sh','-c',
        'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --skip-column-names '+(database or '"$MYSQL_DATABASE"')]
    return subprocess.run(command,input=sql.encode(),capture_output=True,check=True).stdout
measure="SELECT COUNT(*),SUM(CRC32(HEX(id))) FROM envios; SELECT COUNT(*) FROM clientes;"
before=query(measure)
dump=subprocess.run(COMPOSE+['exec','-T','shipment-db','sh','-c',
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --single-transaction --no-tablespaces -u"$MYSQL_USER" "$MYSQL_DATABASE"'],
    capture_output=True,check=True).stdout
query('CREATE DATABASE '+scratch+' CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
restore_command=COMPOSE+['exec','-T','shipment-db','sh','-c',
    'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot '+scratch]
subprocess.run(restore_command,input=dump,capture_output=True,check=True)
after=query(measure,scratch)
assert before==after
orphans=query('SELECT COUNT(*) FROM envios e LEFT JOIN clientes c ON e.cliente_id=c.id WHERE c.id IS NULL;',scratch)
assert int(orphans.strip())==0
(SERVICE/'docs/sprint3/evidencias/restore-report.json').write_text(json.dumps({
    'source_project':'logitrack-s3-close','scratch_schema':scratch,'dump_bytes':len(dump),
    'counts_and_id_checksum_match':True,'orphan_shipments':0,
    'original_schema_untouched':True,'scratch_schema_retained_for_inspection':True},indent=2),encoding='utf8')
print('Backup restore verified in isolated scratch schema; source schema untouched')
