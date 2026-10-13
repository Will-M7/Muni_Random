#!/usr/bin/env python3
"""Production operations. No API workflows, no deletion of databases or volumes."""
import argparse
import base64
import getpass
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import tarfile
import tempfile
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[1]

def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)

def envfile(path):
    values = {}
    for line in Path(path).read_text().splitlines():
        line = line.strip()
        if not line or line.startswith('#'): continue
        k, v = line.split('=', 1)
        values[k] = v
    return values

def init_secrets(directory):
    directory = Path(directory).resolve()
    if directory == ROOT or ROOT in directory.parents:
        raise SystemExit('Guarde los secretos fuera del repositorio/paquete.')
    username = input('Usuario del administrador inicial: ').strip()
    if not re.fullmatch(r'[A-Za-z0-9_.-]{4,60}', username): raise SystemExit('Usuario inválido.')
    password = getpass.getpass('Contraseña NUEVA del administrador (mínimo 20 caracteres): ')
    if len(password) < 20 or len(password.encode()) > 72 or password != getpass.getpass('Repetir contraseña: '):
        raise SystemExit('Contraseña no válida o confirmación distinta (20 caracteres mínimo, 72 bytes máximo BCrypt).')
    if not all((re.search(r'[a-z]', password), re.search(r'[A-Z]', password), re.search(r'\d', password), re.search(r'[^\w]', password))):
        raise SystemExit('Use mayúsculas, minúsculas, números y símbolos.')
    directory.mkdir(mode=0o700, parents=True, exist_ok=False)
    material = {'db_password': secrets.token_urlsafe(36), 'mysql_root_password': secrets.token_urlsafe(36),
                'jwt_secret': secrets.token_urlsafe(64), 'identity_master_key': base64.b64encode(secrets.token_bytes(32)).decode(),
                'admin_password': password, 'backup_passphrase': secrets.token_urlsafe(48)}
    for name, value in material.items():
        p = directory / name
        p.write_text(value)
        # Parent 0700 blocks all other host users; Docker mounts only individual authorized files.
        p.chmod(0o400 if name == 'backup_passphrase' else 0o444)
    print('Secretos creados sin mostrarlos. Usuario inicial: ' + username)
    print('Custodie backup_passphrase por separado; no se incluye dentro de los respaldos.')

class Installation:
    def __init__(self, path):
        self.path = Path(path).resolve()
        self.values = envfile(self.path)
        self.images = envfile(ROOT / 'images.lock.env')
        self.project = self.values.get('COMPOSE_PROJECT_NAME', '')
        if not re.fullmatch(r'sm-prod-[a-z0-9-]+', self.project):
            raise SystemExit('COMPOSE_PROJECT_NAME debe ser exclusivo y comenzar por sm-prod-.')
        self.environment = {**os.environ, **self.images, **self.values}
        self.command = ['docker', 'compose', '--env-file', str(ROOT/'images.lock.env'), '--env-file', str(self.path),
                        '-p', self.project, '-f', str(ROOT/'compose.prod.yml'), '-f', str(ROOT/'compose.release.yml')]
    def compose(self, *args, **kwargs):
        return run(self.command + list(args), env=self.environment, **kwargs)
    def sql(self, statement):
        result = self.compose('exec', '-T', 'mysql', 'sh', '-c',
            'MYSQL_PWD="$(cat /run/secrets/db_password)" exec mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u "$MYSQL_USER" -D "$MYSQL_DATABASE" -N -B',
            input=statement, text=True, stdout=subprocess.PIPE)
        return result.stdout.strip()
    def fresh(self):
        existing = self.compose('ps','--all','--quiet',stdout=subprocess.PIPE,text=True).stdout.strip()
        if existing: raise SystemExit('El proyecto ya contiene contenedores; use otro proyecto nuevo.')
        for suffix in ('mysql_prod_data', 'uploads_prod_data'):
            result = subprocess.run(['docker','volume','inspect',self.project+'_'+suffix], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            if result.returncode == 0: raise SystemExit('El volumen ya existe: se rechaza aprovisionar/restaurar encima de datos.')
            if result.returncode != 1: raise SystemExit('No se pudo verificar Docker.')
        run(['docker','info'], stdout=subprocess.DEVNULL)
    def config(self):
        required = ('APP_BIND_IP','HTTP_PORT','HTTPS_PORT','SERVER_NAME','DB_NAME','DB_USER','ADMIN_BOOTSTRAP_USERNAME',
                    'CORS_ORIGINS','TLS_CERT_PATH','TLS_KEY_PATH','DB_PASSWORD_FILE','MYSQL_ROOT_PASSWORD_FILE',
                    'JWT_SECRET_FILE','IDENTITY_MASTER_KEY_FILE','ADMIN_BOOTSTRAP_PASSWORD_FILE')
        for key in required:
            if not self.values.get(key): raise SystemExit('Falta ' + key)
        address = ipaddress.ip_address(self.values['APP_BIND_IP'])
        if address.is_unspecified or not address.is_private or address.is_loopback:
            raise SystemExit('Configure la IP privada concreta del servidor municipal.')
        host = self.values['SERVER_NAME']
        if not re.fullmatch(r'[a-zA-Z0-9](?:[a-zA-Z0-9.-]{0,251}[a-zA-Z0-9])?', host): raise SystemExit('Nombre DNS inválido.')
        for name in ('DB_NAME','DB_USER'):
            if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{2,50}', self.values[name]): raise SystemExit('Nombre SQL inválido.')
        if self.values['DB_NAME'] in ('san_miguel','v20_manual') or self.values['DB_NAME'].startswith('v20_'):
            raise SystemExit('No se admiten bases de desarrollo.')
        port = int(self.values['HTTPS_PORT'])
        if not 1 <= port <= 65535 or not 1 <= int(self.values['HTTP_PORT']) <= 65535: raise SystemExit('Puerto inválido.')
        origin = 'https://' + host + ('' if port == 443 else ':'+str(port))
        if self.values['CORS_ORIGINS'] != origin: raise SystemExit('CORS_ORIGINS debe coincidir exactamente con ' + origin)
        cert, key = self.values['TLS_CERT_PATH'], self.values['TLS_KEY_PATH']
        run(['openssl','x509','-in',cert,'-noout','-checkend','604800'], stdout=subprocess.DEVNULL)
        verify = ['openssl','verify','-verify_hostname',host]
        if self.values.get('TLS_CA_PATH'): verify += ['-CAfile',self.values['TLS_CA_PATH']]
        run(verify+['-untrusted',cert,cert], stdout=subprocess.DEVNULL)
        certkey = subprocess.check_output(['openssl','x509','-in',cert,'-pubkey','-noout'])
        privatekey = subprocess.check_output(['openssl','pkey','-in',key,'-pubout'])
        if certkey != privatekey: raise SystemExit('El certificado y su clave no coinciden.')
        for keyname in required:
            if keyname.endswith('_FILE'):
                p = Path(self.values[keyname])
                if not p.is_absolute() or not p.is_file() or ROOT in p.resolve().parents: raise SystemExit('Secreto externo inválido: '+keyname)
                if p.parent.stat().st_mode & 0o077: raise SystemExit('Proteja el directorio de secretos con chmod 700: '+keyname)
        for name in ('DB_PASSWORD_FILE','MYSQL_ROOT_PASSWORD_FILE','ADMIN_BOOTSTRAP_PASSWORD_FILE'):
            if len(Path(self.values[name]).read_text().strip()) < 20: raise SystemExit('Secreto demasiado corto: '+name)
        if len(Path(self.values['JWT_SECRET_FILE']).read_text().strip()) < 48: raise SystemExit('JWT demasiado corto.')
        if len(base64.b64decode(Path(self.values['IDENTITY_MASTER_KEY_FILE']).read_text().strip(),validate=True)) != 32:
            raise SystemExit('La clave maestra debe contener exactamente 32 bytes codificados en Base64.')
        self.compose('config','--quiet')
        for name in ('MYSQL_IMAGE','BACKEND_IMAGE','FRONTEND_IMAGE'):
            run(['docker','image','inspect',self.images[name]], stdout=subprocess.DEVNULL)
        print('Configuración, certificado y disponibilidad local de imágenes: correctos. Falta validar DNS/firewall desde la red municipal.')
    def verify(self):
        names = self.sql('SHOW TABLES;').splitlines()
        counts = {name:int(self.sql('SELECT COUNT(*) FROM `'+name+'`;')) for name in names}
        allowed = {'flyway_schema_history','production_seed_guard','app_role','capability','role_capability',
                   'app_user','user_role','user_preference','global_parameter','identity_integration'}
        if any(count for name,count in counts.items() if name not in allowed): raise SystemExit('Existen datos operativos: no es un estado inicial vacío.')
        for name in ('app_user','user_role','user_preference','production_seed_guard','identity_integration'):
            if counts.get(name) != 1: raise SystemExit('Cantidad inicial inesperada: '+name)
        checks = [
          "SELECT COUNT(*) FROM app_user u JOIN user_role ur ON ur.user_id=u.id JOIN app_role r ON r.id=ur.role_id WHERE u.role='ADMIN_SISTEMA' AND r.name='ADMIN_SISTEMA' AND u.activo=1 AND u.fiscalizador_id IS NULL AND u.password_hash LIKE '$2%'",
          "SELECT COUNT(*) FROM production_seed_guard WHERE id=1 AND verified=1 AND bootstrapped=1",
          "SELECT COUNT(*) FROM identity_integration WHERE provider='DISABLED' AND encrypted_api_key IS NULL AND encrypted_client_id IS NULL AND encrypted_client_secret IS NULL AND encrypted_peruapi_key IS NULL AND encrypted_factiliza_token IS NULL",
        ]
        if any(self.sql(q) != '1' for q in checks): raise SystemExit('Falló la verificación del administrador/bootstrap/integración.')
        caps = self.sql("SELECT c.code FROM role_capability rc JOIN app_role r ON r.id=rc.role_id JOIN capability c ON c.id=rc.capability_id WHERE r.name='ADMIN_SISTEMA' ORDER BY c.code").splitlines()
        expected = {'USUARIOS_VER','USUARIOS_CREAR','USUARIOS_EDITAR','USUARIOS_ESTADO','ROLES_VER','ROLES_GESTIONAR','CAPACIDADES_GESTIONAR'}
        if set(caps) != expected: raise SystemExit('Capacidades iniciales del administrador inesperadas.')
        versions = self.sql('SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank').splitlines()
        if versions != [str(n) for n in range(1,30)]: raise SystemExit('Migraciones incompletas o fuera de orden.')
        files = self.compose('exec','-T','backend','find','/app/uploads','-type','f', stdout=subprocess.PIPE,text=True).stdout
        if files.strip(): raise SystemExit('El almacenamiento no está vacío.')
        print(json.dumps({'tables':counts,'migrations':versions,'admin_capabilities':caps,'attached_files':0},indent=2))
    def backup(self, destination, passphrase):
        destination = Path(destination).resolve()
        destination.mkdir(parents=True,mode=0o700,exist_ok=True)
        target = destination / (self.project+'-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'.tar.gpg')
        if target.exists(): raise SystemExit('Ya existe el respaldo.')
        running = self.compose('ps','--status','running','--services',stdout=subprocess.PIPE,text=True).stdout.splitlines()
        self.compose('stop','frontend','backend')
        try:
            with tempfile.TemporaryDirectory(prefix='sm-backup-',dir=destination) as temp:
                temp = Path(temp)
                with (temp/'database.sql').open('wb') as out:
                    self.compose('exec','-T','mysql','sh','-c',
                        'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" exec mysqldump -u root --single-transaction --routines --triggers --events --hex-blob --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"',stdout=out)
                with (temp/'uploads.tar').open('wb') as out:
                    self.compose('run','--rm','--no-deps','-T','--entrypoint','tar','backend','-C','/app/uploads','-cf','-','.',stdout=out)
                secret_names = ('DB_PASSWORD_FILE','MYSQL_ROOT_PASSWORD_FILE','JWT_SECRET_FILE','IDENTITY_MASTER_KEY_FILE','ADMIN_BOOTSTRAP_PASSWORD_FILE')
                with tarfile.open(temp/'payload.tar','w') as archive:
                    archive.add(temp/'database.sql',arcname='database.sql')
                    archive.add(temp/'uploads.tar',arcname='uploads.tar')
                    archive.add(self.path,arcname='config.env')
                    archive.add(ROOT/'images.lock.env',arcname='images.lock.env')
                    archive.add(ROOT/'RELEASE.json',arcname='RELEASE.json')
                    for name in secret_names: archive.add(self.values[name],arcname='secrets/'+name)
                run(['gpg','--batch','--pinentry-mode','loopback','--passphrase-file',str(passphrase),
                     '--symmetric','--cipher-algo','AES256','--output',str(target),str(temp/'payload.tar')])
                target.chmod(0o600)
                (Path(str(target)+'.sha256')).write_text(hashlib.sha256(target.read_bytes()).hexdigest()+'  '+target.name+'\n')
        finally:
            restart = [s for s in ('backend','frontend') if s in running]
            if restart: self.compose('start',*restart)
        print(target)

    def restore(self, archive, passphrase):
        self.fresh()
        # Restore only into new secrets and new volumes; never overwrite an installation.
        keys = ('DB_PASSWORD_FILE','MYSQL_ROOT_PASSWORD_FILE','JWT_SECRET_FILE','IDENTITY_MASTER_KEY_FILE','ADMIN_BOOTSTRAP_PASSWORD_FILE')
        for name in keys:
            target = Path(self.values[name])
            if not target.is_absolute() or target.exists() or ROOT in target.resolve().parents:
                raise SystemExit('La restauración requiere rutas externas de secretos NUEVAS: '+name)
        with tempfile.TemporaryDirectory(prefix='sm-restore-') as temp:
            temp = Path(temp)
            run(['gpg','--batch','--pinentry-mode','loopback','--passphrase-file',str(passphrase),
                 '--output',str(temp/'payload.tar'),'--decrypt',str(archive)])
            expected = {'database.sql','uploads.tar','config.env','images.lock.env','RELEASE.json'} | {'secrets/'+k for k in keys}
            with tarfile.open(temp/'payload.tar') as payload:
                if set(payload.getnames()) != expected or any(not m.isfile() for m in payload.getmembers()):
                    raise SystemExit('Formato de respaldo no válido.')
                payload.extractall(temp, filter='data')
            if envfile(temp/'images.lock.env') != self.images:
                raise SystemExit('Restaure usando las mismas imágenes del respaldo antes de actualizar.')
            old = envfile(temp/'config.env')
            if old['DB_NAME'] != self.values['DB_NAME'] or old['DB_USER'] != self.values['DB_USER']:
                raise SystemExit('Mantenga DB_NAME y DB_USER; aísle la restauración mediante un proyecto Docker nuevo.')
            for name in keys:
                target = Path(self.values[name])
                target.parent.mkdir(parents=True,mode=0o700,exist_ok=True)
                if target.parent.stat().st_mode & 0o077: raise SystemExit('El directorio de secretos debe tener modo 0700.')
                with target.open('xb') as out: out.write((temp/'secrets'/name).read_bytes())
                target.chmod(0o444)
            self.config()
            self.compose('up','-d','--no-build','--pull','never','--wait','--wait-timeout','180','mysql')
            with (temp/'database.sql').open('rb') as source:
                self.compose('exec','-T','mysql','sh','-c',
                    'MYSQL_PWD="$(cat /run/secrets/db_password)" exec mysql --ssl-mode=REQUIRED -h 127.0.0.1 -u "$MYSQL_USER" -D "$MYSQL_DATABASE"',stdin=source)
            with (temp/'uploads.tar').open('rb') as source:
                self.compose('run','--rm','--no-deps','-T','--entrypoint','tar','backend','-C','/app/uploads','-xpf','-',stdin=source)
            self.compose('up','-d','--no-build','--pull','never','--wait','--wait-timeout','300','backend')
        print('Restauración terminada en volúmenes nuevos. Acceso frontend pendiente de aprobación.')

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('action',choices=['init-secrets','check','initialize','verify-initial','status','stop','start','activate','backup','restore'])
parser.add_argument('--env')
parser.add_argument('--directory')
parser.add_argument('--passphrase-file')
parser.add_argument('--archive')
args = parser.parse_args()
os.umask(0o077)
if args.action == 'init-secrets':
    if not args.directory: parser.error('--directory requerido')
    init_secrets(args.directory)
else:
    if not args.env: parser.error('--env requerido')
    installation = Installation(args.env)
    if args.action == 'check': installation.config()
    elif args.action == 'initialize':
        installation.config(); installation.fresh()
        installation.compose('up','-d','--no-build','--pull','never','--wait','--wait-timeout','300','mysql','backend')
        installation.verify()
    elif args.action == 'verify-initial': installation.verify()
    elif args.action == 'status': installation.compose('ps')
    elif args.action == 'stop': installation.compose('stop')
    elif args.action == 'start': installation.compose('start','mysql','backend')
    elif args.action == 'activate':
        installation.config()
        if input('Con aprobación final, escriba el nombre DNS para habilitar acceso: ') != installation.values['SERVER_NAME']: raise SystemExit('Cancelado.')
        installation.compose('up','-d','--no-build','--pull','never','--wait','--wait-timeout','300')
    elif args.action == 'backup':
        if not args.directory or not args.passphrase_file: parser.error('--directory y --passphrase-file requeridos')
        installation.backup(args.directory,args.passphrase_file)
    elif args.action == 'restore':
        if not args.archive or not args.passphrase_file: parser.error('--archive y --passphrase-file requeridos')
        installation.restore(args.archive,args.passphrase_file)
