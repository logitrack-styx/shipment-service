from pathlib import Path
import hashlib,json,re,subprocess

root=Path(__file__).resolve().parents[2]
text=(root/'docker-compose.yml').read_text(encoding='utf8')
text=text.replace('name: logitrack','name: logitrack-s3-close',1)
text=re.sub(r'^    container_name:.*\n','',text,flags=re.M)
text=text.replace('./logitrack-local-secrets/','../logitrack-local-secrets/')
text=text.replace('${DB_PASSWORD:-secret_password}','${DB_PASSWORD:?DB_PASSWORD required}')
text=text.replace('${DB_ROOT_PASSWORD:-root_password}','${DB_ROOT_PASSWORD:?DB_ROOT_PASSWORD required}')
text=text.replace('${ADMIN_PASSWORD:-Admin123!}','${ADMIN_PASSWORD:?ADMIN_PASSWORD required}')
text=text.replace('      ADMIN_EMAIL:', '      ADMIN_USERNAME: ${ADMIN_USERNAME:-admin}\n      ADMIN_EMAIL:')
text=text.replace('      CONFIG_GIT_URI:', '      SPRING_CLOUD_CONFIG_SERVER_GIT_DEFAULT_LABEL: ${CONFIG_GIT_LABEL:-main}\n      CONFIG_GIT_URI:')
text=text.replace('      JWT_ISSUER: logitrack-security', '      JWT_ISSUER: logitrack-security\n      JWT_ACCESS_TOKEN_TTL: ${JWT_ACCESS_TOKEN_TTL:-3600}')
manifest=[]
for repo in ['config-server','eureka-server','security-service','courier-service','shipment-service','api-gateway']:
    docker=root/repo/'Dockerfile'
    content=docker.read_text(encoding='utf8')
    if 'ARG SOURCE_DIGEST' not in content:
        content=content.replace('FROM eclipse-temurin:25-jre','FROM eclipse-temurin:25-jre\nARG SOURCE_DIGEST\nLABEL org.logitrack.source.sha256=$SOURCE_DIGEST')
        docker.write_text(content,encoding='utf8')
    digest=hashlib.sha256()
    paths=sorted([root/repo/'pom.xml',docker]+list((root/repo/'src').rglob('*')))
    for p in paths:
        if p.is_file():
            digest.update(str(p.relative_to(root/repo)).replace('\\','/').encode()); digest.update(p.read_bytes())
    sha=digest.hexdigest()
    image=f'logitrack/{repo}:s3-{sha[:12]}'
    context='.' if repo=='shipment-service' else '../'+repo
    text=text.replace(f'    image: facutolaba/logitrack-{repo}:0.3.0', f'    image: {image}\n    build:\n      context: {context}\n      args:\n        SOURCE_DIGEST: {sha}')
    manifest.append(dict(service=repo,image=image,source_sha256=sha,
        base_commit=subprocess.check_output(['git','-C',repo,'rev-parse','HEAD'],text=True).strip(),
        source_state='source snapshot; source_sha256 identifies exact build inputs'))
(root/'shipment-service/compose.sprint3.yml').write_text(text,encoding='utf8')
(root/'shipment-service/docs/sprint3/evidencias/image-sources.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
(root/'shipment-service/.env.sprint3.example').write_text('''# Copy outside version control and supply external secrets.
DB_USERNAME=logitrack_user
DB_PASSWORD=
DB_ROOT_PASSWORD=
ADMIN_USERNAME=admin
ADMIN_EMAIL=admin@example.invalid
ADMIN_PASSWORD=
JWT_ACCESS_TOKEN_TTL=3600
CONFIG_GIT_URI=https://github.com/logitrack-styx/styx-config.git
CONFIG_GIT_LABEL=main
''',encoding='utf8')
