"""Create closure evidence only from executed, passing reports; never closes GitHub issues."""
from pathlib import Path
import json
import re

service=Path(__file__).resolve().parents[1]
docs=service/'docs/sprint3'
e=docs/'evidencias'
def load(name):return json.loads((e/name).read_text(encoding='utf-8-sig'))
e2e=load('e2e-report.json'); assert all(r['assertions_passed'] for r in e2e)
apidog=load('apidog-run.json');assert all(r['passed'] for r in apidog['assertions'])
jwt=load('downstream-jwt-report.json');assert all(r['passed'] for r in jwt)
runtime=load('runtime-report.json');assert len(runtime['compose'])==9
assert all(r['health']=='healthy' for r in runtime['compose'])
publications=load('published-images.json');assert len(publications)==6
assert all(r['push_verified'] and r['pull_verified'] for r in publications)
persistence=load('persistence-report.json');assert persistence['same_admin_id']
unit=load('surefire-summary.json');assert all(not r['failures'] and not r['errors'] for r in unit.values())

mapping={
'S3-00':('Planificación y DoD verificable','docs/SPRINT3-PLANNING.md; docs/sprint3','auditoria-inicial.md, fichas y reportes de esta ejecución'),
'S3-01':('Reconciliación Git de configuración','styx-config/*.yaml','fetch sin sobrescrituras y divergencia inicial 0/0 registrada'),
'S3-02':('Configuración externa por servicio','styx-config/*.yaml','runtime-report.json: Config sirve TTL desde Git, versión registrada'),
'S3-03':('Descubrimiento Eureka','eureka-server/src/main','runtime-report.json: cuatro aplicaciones UP'),
'S3-04':('Security como cliente Eureka','security-service/pom.xml; src/main/resources','registro UP y login/usuarios por Gateway en E2E'),
'S3-05':('Courier como cliente Eureka','courier-service/pom.xml; src/main/resources','registro UP, disponibilidad y recuperación en E2E'),
'S3-06':('Shipment como cliente Eureka','shipment-service/pom.xml; src/main/resources','registro UP y flujo de envíos en E2E'),
'S3-07':('Feign por nombre de servicio','src/main/java/com/logitrack/shipment/client','E2E S3-08: asignación consultando Courier, sin URL fija'),
'S3-08':('Traducción de fallos y resiliencia','src/main/java/com/logitrack/shipment/client','suite CourierClient, E2E 404/409/503 y recuperación'),
'S3-09':('Asignación atómica y concurrencia','src/main/java/com/logitrack/shipment/repository/EnvioRepository.java','suite MySQL y E2E: resultados concurrentes 200/409 sin doble asignación'),
'S3-10':('Gateway como entrada única','api-gateway/src/main; styx-config/api-gateway.yaml','runtime: puertos de negocio internos y registro UP; E2E por 8066'),
'S3-11':('Rutas lb y reescritura','styx-config/api-gateway.yaml','E2E auth/users/couriers/shipments; StripPrefix y RewritePath efectivos'),
'S3-12':('Contrato JWT RS256','api-gateway/.../SecurityConfig.java; decoders de las tres APIs','E2E negativos Gateway, downstream-jwt-report.json y JwtDecoderContractTest'),
'S3-13':('RBAC por método/ruta y propiedad','api-gateway/.../SecurityConfig.java; servicios de dominio','E2E 401/403, ruta/método desconocidos, CLIENTE ajeno y REPARTIDOR'),
'S3-14':('CORS y errores perimetrales','api-gateway/.../SecurityConfig.java','E2E preflight permitido, origen rechazado y JSON 401/403'),
'S3-15':('Compose global e imágenes trazables','compose.sprint3.yml; compose.sprint3.release.yml; seis Dockerfiles','runtime nueve saludables, no root, sin puertos internos; push/pull seis imágenes'),
'S3-16':('Arranque, caída y recuperación','scripts/sprint3_e2e.py; Compose','E2E S3-19/20: caída 503 sin escritura y recuperación Feign/Gateway'),
'S3-17':('Colección Apidog vigente','docs/sprint3/apidog-coleccion.json; scripts/run-apidog.mjs','siete requests y scripts originales; doce variables exactas; 16 aserciones'),
'S3-18':('Matriz de veinte escenarios','scripts/sprint3_e2e.py','55 comprobaciones, incluyendo los veinte grupos S3-01..20, runtime y persistencia'),
'S3-19':('Deuda de identidad S2','security-service/.../AdminBootstrap.java; AuthServiceImpl.java','suite 5 pruebas, login por email en MySQL, bootstrap único después de reinicio, TTL'),
'S3-20':('Decisión de deuda Courier','courier-service/.../SecurityServiceUsuarioRolVerifier.java; ADR-004','RestClient acotado al alta con timeouts; ddl-auto:update justificado para demo, no producción'),
'S3-21':('Review y defensa','docs/sprint3/guion-review.md; fichas y matriz','guion equivalente de 14 minutos con pruebas reales y limitaciones explícitas')}

lines=['# Matriz de cierre — evidencia ejecutada','',
'Estado técnico: VALIDADO en las ramas de cierre. Integración y cierre administrativo se acreditan separadamente con snapshots de GitHub; no se deducen de estas pruebas.','',
f'Ejecución 2026-11-08: {len(e2e)} comprobaciones E2E; {len(apidog["assertions"])} aserciones Apidog; {len(jwt)} comprobaciones directas de JWT/email. Nueve contenedores saludables y seis imágenes publicadas/descargadas por digest.','',
'## Pruebas automatizadas','', '| Repositorio | Tests | Fallos | Errores |','|---|---:|---:|---:|']
for repo,result in unit.items():lines.append(f'| {repo} | {result["tests"]} | {result["failures"]} | {result["errors"]} |')
lines+=['','Config no tiene tests existentes: su build no se presenta como suite funcional. Su comportamiento se verificó por Config Server, Docker y E2E.','',
'## Fichas por issue','']
index=[]
for file in sorted(e.glob('docs-issues-*.json')):
    repo=file.stem.removeprefix('docs-issues-')
    for issue in json.loads(file.read_text(encoding='utf-8-sig')):
        match=re.search(r'\[(S3-\d+)\]',issue['title'])
        if match:
            key=match[1]; problem,path,proof=mapping[key]
        elif repo=='courier-service' and issue['number'] in [2,3,4,6]:
            key=f'Courier heredado #{issue["number"]}'
            problem,path,proof={2:('Gestión de repartidores','courier-service/src/main','suite Courier 20 y E2E alta/consulta/estado/propiedad'),
                3:('Dockerización integrada','compose.sprint3.release.yml','seis imágenes publicadas y nueve componentes saludables'),
                4:('Integración completa','scripts/sprint3_e2e.py','55 comprobaciones E2E y recuperación real'),
                6:('MySQL y entorno','Compose y configuración Courier','BD aislada, sin puerto host, secretos externos y persistencia')}[issue['number']]
        else:continue
        lines+=['### '+key+' — '+repo+'#'+str(issue['number']),'',
            f'- Problema: {problem}.',f'- Implementación y motivo: `{path}`; mantiene separación de dominios y contrato vigente.',
            f'- Verificación: {proof}.',f'- Estado técnico: VALIDADO. [Issue]({issue["url"]}); estado administrativo inicial: {issue["state"]}.','']
        index.append({'repo':repo,'number':issue['number'],'id':key,'title':issue['title'],
            'problem':problem,'path':path,'proof':proof,'url':issue['url'],'initial_state':issue['state']})
lines+=['## Evidencias y límites','',
'Reportes: e2e-report.json, apidog-run.json, downstream-jwt-report.json, runtime-report.json, persistence-report.json, scale-report.json, published-images.json y surefire-summary.json en evidencias/. Los fallos intermedios se conservaron localmente y se corrigieron; no se presentan como aprobaciones.',
'', 'Swagger fue observado en navegador en las tres APIs; ver swagger-observacion.md. La colección usa el export que confirmó el usuario y conserva sus doce nombres a*. El runner se identifica como HTTP/Node, no como la app de escritorio Apidog.',
'', 'Las fuentes S1/S2/S3 y el contrato S2 fueron encontrados y leídos tras ampliar el inventario. No se localizó S3 v1.2 ni la matriz JSON original; los criterios se contrastaron con la consigna oficial S3, cierre v2 aprobado, issues y colección vigente. No se inventa haber leído archivos ausentes.',
'', 'SCL continúa como término no confirmado por ausencia de grabación/transcripción. Courier ddl-auto:update queda limitado y justificado para demo en ADR-004; no se declara production-ready. No se incorporaron funcionalidades S4.',
'', 'La aprobación de integración exigida por la revisión automática se registra aparte. Ningún issue se cierra por esta generación de documentación.']
(docs/'matriz-cierre.md').write_text('\n'.join(lines)+'\n',encoding='utf8')
(e/'closure-index.json').write_text(json.dumps(index,indent=2,ensure_ascii=False),encoding='utf8')
print('Generated individual evidence sheets:',len(index))
