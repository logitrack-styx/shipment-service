"""Seed and measure only the isolated closure stack. Does not delete records."""
import json
from pathlib import Path
import platform
import statistics
import subprocess
import time
import tracemalloc
from sprint3_e2e import COMPOSE, SERVICE, ENV, call, login

assert 'compose.sprint3.yml' in ' '.join(COMPOSE)
def sql(query):
    # Credentials expand inside the container; they are absent from host argv/output.
    command=COMPOSE+['exec','-T','shipment-db','sh','-c',
        'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE" --batch --skip-column-names']
    result=subprocess.run(command,input=query.encode(),capture_output=True,check=True)
    return result.stdout

sql("""
SET SESSION cte_max_recursion_depth=10001;
INSERT IGNORE INTO envios(id,created_date,last_modified_date,codigo_seguimiento,peso,ancho,alto,largo,estado,
origen_calle,origen_numero,origen_ciudad,origen_codigo_postal,
destino_calle,destino_numero,destino_ciudad,destino_codigo_postal,cliente_id,version)
WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i+1 FROM n WHERE i<10000)
SELECT UNHEX(MD5(CONCAT('scale-envio',i))),NOW(6),NOW(6),UNHEX(MD5(CONCAT('scale-track',i))),1,1,1,1,'PENDIENTE',
'Calle','1','Ciudad','1000','Calle','2','Ciudad','1000',(SELECT id FROM clientes ORDER BY id LIMIT 1),0 FROM n;
""")
count=int(sql('SELECT COUNT(*) FROM envios;').strip())
assert count>=10000
report={'dataset_rows':count,'platform':platform.platform(),
    'measurement_scope':'Host Python allocation while capturing mysql CLI output; not Java heap or server memory',
    'samples':[]}
for name,query in [('unbounded-baseline','SELECT HEX(id) FROM envios ORDER BY id;'),
                   ('bounded-page','SELECT HEX(id) FROM envios ORDER BY id LIMIT 100 OFFSET 100;')]:
    durations=[]
    for _ in range(3):
        tracemalloc.start(); started=time.perf_counter()
        output=sql(query)
        durations.append((time.perf_counter()-started)*1000)
        _, peak=tracemalloc.get_traced_memory(); tracemalloc.stop()
    rows=len(output.splitlines())
    assert rows==(count if name=='unbounded-baseline' else 100)
    report['samples'].append({'query':name,'rows':rows,'stdout_bytes':len(output),
        'median_ms_including_docker_exec':round(statistics.median(durations),2),'host_capture_peak_bytes':peak})
token=login(ENV.get('ADMIN_USERNAME','admin'),ENV['ADMIN_PASSWORD'])[1]['accessToken']
code,body,headers=call('GET','/api/v1/shipments?size=100&page=1',token)
assert code==200 and len(body)==100
report['http_page']={'status':code,'rows':len(body),'total':headers.get('X-Total-Count',headers.get('x-total-count'))}
(SERVICE/'docs/sprint3/evidencias/scale-report.json').write_text(json.dumps(report,indent=2),encoding='utf8')
print('Scale fixture rows:',count,'; HTTP page rows:',len(body))
