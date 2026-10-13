#!/usr/bin/env python3
"""Black-box QA against the real running application; no mocks or SQL mutations.

Keeps all synthetic records and private credentials. Never calls an external DNI
provider. Requires the dedicated QA administrator; never accepts production URLs.
"""
import argparse
import base64
import datetime as dt
import json
import os
from pathlib import Path
import secrets
import subprocess
import uuid
from zoneinfo import ZoneInfo
import requests

parser = argparse.ArgumentParser()
parser.add_argument('--target', choices=['manual', 'docker'], required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
private = root / '.local/integration-20261013'
base = 'http://localhost:8080' if args.target == 'manual' else 'https://localhost:18443'
admin_name = 'qa_integracion_admin_20261013' if args.target == 'manual' else 'qa_admin_inicial'
password_file = private / ('manual_admin_password' if args.target == 'manual' else 'admin_password')
session = requests.Session()
session.verify = True if args.target == 'manual' else str(private / 'tls.crt')
run = uuid.uuid4().hex[:8]
evidence = {'target': args.target, 'run': run, 'checks': [], 'resources': {}}
output = private / f'api-{args.target}-{run}.json'
accounts = {'admin': {'username': admin_name, 'password': password_file.read_text()}}
tokens = {}

def persist():
    output.write_text(json.dumps(evidence, indent=2))
    output.chmod(0o600)

def call(method, path, actor=None, body=None, expected=200, binary=False, **kw):
    headers = {'Origin': base}
    if actor:
        headers['Authorization'] = 'Bearer ' + tokens[actor]
    response = session.request(method, base + path, headers=headers, json=body, timeout=30, **kw)
    good = response.status_code == expected
    evidence['checks'].append({'method': method, 'path': path, 'actor': actor,
                               'expected': expected, 'actual': response.status_code, 'passed': good})
    persist()
    if not good:
        # Deliberately omit response bodies: authentication and provider responses
        # may contain secrets or personal data.
        raise AssertionError(f'{method} {path}: expected {expected}, got {response.status_code}')
    if binary:
        return response.content
    return response.json() if response.content and 'json' in response.headers.get('Content-Type', '') else None

def login(actor):
    response = call('POST', '/api/auth/login', body=accounts[actor])
    tokens[actor] = response['token']
    return response

def create(actor, role):
    account = {'username': f'qa_{actor}_{run}', 'password': secrets.token_urlsafe(24)}
    accounts[actor] = account
    response = call('POST', '/api/usuarios', 'admin',
                    dict(account, nombre=f'Prueba integrada {actor} {run}', rol=role), expected=201)
    evidence['resources'][actor] = {'username': account['username'], 'fiscalizadorId': response.get('fiscalizadorId')}
    # Passwords stay in a separate ignored, permission-restricted file.
    f = private / f'accounts-{args.target}-{run}.json'
    f.write_text(json.dumps(accounts)); f.chmod(0o600)
    login(actor)
    return response

def schedule(code, date, time, inspector):
    response=call('POST', f'/api/expedientes/{code}/programaciones', 'municipio',
                  {'fiscalizadorId': inspector, 'fecha': date.isoformat(), 'hora': time, 'tipoVisita': 'PROGRAMADA'}, expected=201)
    sql=f"SELECT CONCAT(fecha,'T',hora) FROM programacion_fiscalizacion WHERE id={int(response['id'])}"
    if args.target=='manual':
        cmd=['docker','exec','-e','MYSQL_PWD=isolated_v20_only','san-miguel-v20-audit','mysql','-uroot','v20_manual','-Nse',sql]
    else:
        cmd=['docker','exec','sm-integration-20261013-mysql-1','sh','-c',
             'MYSQL_PWD="$(cat /run/secrets/mysql_root_password)" mysql -uroot v20_prod_rehearsal -Nse "$1"','qa',sql]
    stored=subprocess.check_output(cmd,text=True).strip()
    expected=date.isoformat()+'T'+time+':00'
    good=stored==expected and response['fecha']==date.isoformat() and response['hora']==time+':00'
    evidence['checks'].append({'check':'fecha/hora solicitada == MySQL == API','programacion':response['id'],'passed':good})
    persist()
    assert good,'Discrepancia de fecha/hora entre petición, base y API'
    return response

def conclude(revision, kind, follow=False):
    return call('POST', f'/api/revisiones/{revision}/concluir', 'municipio',
                {'conclusion': kind, 'fundamentoConclusion': 'Conclusión sintética de ensayo integrado',
                 'descripcionConclusion': 'Datos exclusivamente de pruebas', 'requiereSeguimiento': follow,
                 'requiereDerivacion': False, 'plazoConclusion': None})

try:
    admin = login('admin')
    assert admin.get('rol') == 'ADMIN_SISTEMA'
    roles = call('GET', '/api/admin/roles', 'admin')
    call('GET', '/api/admin/capacidades', 'admin')
    for role in roles:
        if role['configurable']:
            call('PUT', f"/api/admin/roles/{role['nombre']}/capacidades", 'admin', {'capacidades': role['capacidades']})
    create('municipio', 'MUNICIPIO'); create('otro_municipio', 'MUNICIPIO')
    inspector = create('fiscalizador', 'FISCALIZADOR')['fiscalizadorId']
    second = create('otro_fiscalizador', 'FISCALIZADOR')['fiscalizadorId']
    for identity in [inspector, second]:
        call('PUT', '/api/admin/fiscalizadores-gestion/' + identity, 'admin', {'municipioUsername': accounts['municipio']['username']})
    call('GET', '/api/admin/fiscalizadores-gestion', 'admin')
    call('PUT', '/api/usuarios/' + accounts['fiscalizador']['username'] + '/roles', 'admin', {'roles': ['FISCALIZADOR'], 'fiscalizadorId': second}, expected=409)
    call('GET', '/api/settings/me', 'municipio')
    call('PUT', '/api/settings/users/' + accounts['municipio']['username'], 'admin',
         {'mapaInterno': True, 'googleExterno': True, 'googleUrl': True, 'descargarJornadaHtml': True})
    call('GET', '/api/catalogos/fiscalizadores', 'municipio')
    call('GET', '/api/usuarios', 'municipio', expected=403)
    call('GET', '/api/expedientes', 'admin', expected=403)
    # Check encryption on real persistence without querying Factiliza. Keep this
    # synthetic credential in the rehearsal only; the manual panel stays clean.
    if args.target == 'docker':
        call('PUT', '/api/identity-integration', 'admin', {'provider': 'FACTILIZA', 'apiKey': 'synthetic-QA-not-a-provider-credential-' + run})
        configuration = call('GET', '/api/identity-integration', 'admin')
        assert configuration['credentialConfigured']
        call('PUT', '/api/identity-integration', 'admin', {'provider': 'DISABLED'})
    today = dt.datetime.now(ZoneInfo('America/Lima')).date()
    data = {'origen': 'INICIATIVA_MUNICIPAL', 'objetoFiscalizacion': 'Inspección sintética integrada ' + run,
            'nombreAdministrado': 'Administrado sintético', 'administradoApellidos': run,
            'tipoAdministrado': 'PERSONA_NATURAL', 'documentoAdministrado': '00000000',
            'direccionFiscalizada': 'Dirección sintética de ensayo', 'referenciaDomicilio': 'Sin datos personales',
            'latitud': -15.40, 'longitud': -70.10}
    created = call('POST', '/api/expedientes/fiscalizaciones', 'municipio', expected=201,
                   files={'fiscalizacion': (None, json.dumps({'expediente': data, 'metodoUbicacion': 'MAPA_INTERNO'}), 'application/json')})
    code = created['codigo']; evidence['resources']['expediente'] = code
    call('GET', '/api/expedientes/' + code, 'otro_municipio', expected=403)
    call('GET', '/api/expedientes/' + code + '/documentos', 'otro_municipio', expected=403)
    first = schedule(code, today, '10:00', inspector)
    future = schedule(code, today + dt.timedelta(days=2), '10:00', inspector)
    evidence['resources']['programacion'] = first['id']
    moved = call('POST', f"/api/programaciones/{future['id']}/reprogramar", 'municipio',
                 {'fecha': (today + dt.timedelta(days=3)).isoformat(), 'hora': '11:00', 'tipoVisita': 'PROGRAMADA',
                  'fiscalizadorId': inspector, 'motivo': 'Reprogramación sintética integrada'})
    call('POST', f"/api/programaciones/{moved['id']}/cancelar", 'municipio', {'motivo': 'Cancelación de ensayo'})
    # Separate open cases keep future visits for UI/calendar/map checks without
    # blocking the lifecycle case's final closure.
    future_codes = []
    for i in range(3):
        future_case = call('POST', '/api/expedientes', 'municipio',
                           dict(data, objetoFiscalizacion=f'Visita futura sintética {run}-{i}',
                                latitud=-15.40-i*.001, longitud=-70.10-i*.001), expected=201)
        future_codes.append(future_case['codigo'])
        schedule(future_case['codigo'], today + dt.timedelta(days=1), f'{9+i:02}:00', inspector)
    evidence['resources']['expedientes_futuros'] = future_codes
    call('GET', '/api/programaciones/agenda/mensual?mes=' + today.strftime('%Y-%m'), 'municipio')
    call('GET', '/api/fiscalizador/mis-expedientes', 'fiscalizador')
    call('GET', '/api/fiscalizador/mis-tareas/proximas', 'fiscalizador')
    call('GET', '/api/fiscalizador/ruta?fecha=' + today.isoformat(), 'fiscalizador')
    pid = first['id']
    call('GET', f'/api/fiscalizador/programaciones/{pid}/diligencia', 'otro_fiscalizador', expected=403)
    workspace = call('POST', f'/api/fiscalizador/programaciones/{pid}/diligencia/iniciar', 'fiscalizador', {'estadoGps': 'NO_DISPONIBLE'})
    did = workspace['id']; evidence['resources']['diligencia'] = did
    call('PATCH', f'/api/fiscalizador/diligencias/{did}', 'fiscalizador',
         {'situacionPersona': 'IDENTIFICADA', 'personaNombres': 'Persona sintética', 'personaApellidos': 'Ensayo',
          'personaTipoDocumento': 'DNI', 'personaNumeroDocumento': '00000000', 'personaCondicion': 'Responsable de prueba',
          'hechosConstatados': 'Hechos sintéticos para verificar persistencia y reportes', 'ocurrencias': 'Sin hechos reales'})
    call('POST', f'/api/fiscalizador/diligencias/{did}/participantes', 'fiscalizador',
         {'tipoParticipante': 'OTRO', 'nombre': 'Testigo sintético', 'apellidos': 'Ensayo'})
    png = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/mO8AAAAASUVORK5CYII=')
    ev = call('POST', f'/api/fiscalizador/diligencias/{did}/evidencias', 'fiscalizador',
              params={'tipo': 'FOTO', 'descripcion': 'Evidencia sintética de integración'},
              files={'archivo': ('evidencia-qa.png', png, 'image/png')})
    evidence['resources']['evidencia'] = ev['id']
    act = call('POST', f'/api/fiscalizador/diligencias/{did}/finalizar', 'fiscalizador',
               {'firmaFiscalizado': 'FIRMA_PENDIENTE', 'copiaEntregada': False, 'medioEntrega': 'PENDIENTE', 'estadoGpsCierre': 'NO_DISPONIBLE'})
    aid = act['id']; evidence['resources']['acta'] = aid
    pdf = call('GET', f'/api/actas/{aid}/pdf', 'municipio', binary=True)
    assert pdf.startswith(b'%PDF')
    call('GET', f'/api/actas/{aid}/pdf', expected=401)
    call('GET', f'/api/actas/{aid}/pdf', 'otro_municipio', expected=403)
    call('GET', f'/api/actas/{aid}/pdf', 'otro_fiscalizador', expected=403)
    call('POST', f'/api/expedientes/{code}/documento', 'municipio', files={'documento': ('sustento-qa.pdf', pdf, 'application/pdf')})
    call('GET', f'/api/expedientes/{code}/documento', 'municipio', binary=True)
    call('GET', f'/api/actas/{aid}/evidencias/{ev["id"]}', 'municipio', binary=True)
    rev = call('POST', f'/api/revisiones/diligencias/{did}/iniciar', 'municipio')['revision']['id']
    evidence['resources']['revision'] = rev
    clarification = call('POST', f'/api/revisiones/{rev}/aclaraciones', 'municipio', {'mensaje': 'Aclaración sintética de ensayo'})
    pending = call('GET', '/api/revisiones/mis-aclaraciones', 'fiscalizador')
    clarification_id = clarification['id']
    assert any(x['id'] == clarification_id for x in pending)
    call('POST', f'/api/revisiones/aclaraciones/{clarification_id}/responder', 'otro_fiscalizador', {'respuesta': 'Respuesta ajena'}, expected=403)
    call('POST', f'/api/revisiones/aclaraciones/{clarification_id}/responder', 'fiscalizador', {'respuesta': 'Respuesta sintética con autoría preservada'})
    call('POST', f'/api/revisiones/diligencias/{did}/iniciar', 'municipio')
    conclude(rev, 'RECOMENDACION_MEJORAS_CORRECCIONES', True)
    follow = call('POST', f'/api/revisiones/{rev}/seguimientos', 'municipio',
                  {'fecha': today.isoformat(), 'hora': '15:00', 'tipoVisita': 'PROGRAMADA', 'fiscalizadorId': inspector, 'motivo': 'Seguimiento sintético'})
    fp = follow['id']
    wd = call('POST', f'/api/fiscalizador/programaciones/{fp}/diligencia/iniciar', 'fiscalizador', {'estadoGps': 'NO_DISPONIBLE'})['id']
    call('PATCH', f'/api/fiscalizador/diligencias/{wd}', 'fiscalizador', {'situacionPersona': 'IDENTIFICADA', 'personaNombres': 'Persona sintética', 'personaApellidos': 'Ensayo', 'personaTipoDocumento': 'DNI', 'personaNumeroDocumento': '00000000', 'personaCondicion': 'Responsable de prueba', 'hechosConstatados': 'Mejora verificada en ensayo'})
    call('POST', f'/api/fiscalizador/diligencias/{wd}/finalizar', 'fiscalizador', {'firmaFiscalizado': 'FIRMA_PENDIENTE', 'copiaEntregada': False, 'medioEntrega': 'PENDIENTE', 'estadoGpsCierre': 'NO_DISPONIBLE'})
    r2 = call('POST', f'/api/revisiones/diligencias/{wd}/iniciar', 'municipio')['revision']['id']
    conclude(r2, 'CONFORMIDAD')
    final = call('GET', '/api/expedientes/' + code, 'municipio')
    assert final['estado'] == 'CERRADO'
    report = call('GET', f'/api/revisiones/{r2}/informe.pdf', 'municipio', binary=True)
    assert report.startswith(b'%PDF')
    evidence['resources']['informe_revision'] = r2
    evidence['resources']['pdf_sha256'] = __import__('hashlib').sha256(pdf).hexdigest()
    evidence['completed'] = True
finally:
    persist()
    good = sum(x['passed'] for x in evidence['checks'])
    print(f"Comprobaciones reales {args.target}: {good} aprobadas, {len(evidence['checks'])-good} fallidas; evidencia privada: {output}")
