# Entrega para la intranet municipal

Este paquete conserva el sistema aprobado. No publica servicios por sí solo. El destino,
DNS, certificado, acceso y política de respaldos deben confirmarse con el ingeniero.
No usar los secretos, bases ni volúmenes del ensayo técnico como producción.

## Requisitos del servidor

- Linux con Docker Engine y Compose v2, Python 3.12 o posterior, OpenSSL, GnuPG y espacio
  para imágenes, base, documentos y respaldos. Las imágenes de esta entrega son **linux/amd64**;
  si el servidor es ARM64, reconstruir y volver a validar allí antes de activar.
- Dimensionamiento inicial orientativo: 4 CPU, 8 GB RAM y disco SSD con espacio y monitoreo
  acordes al volumen de documentos. El ingeniero debe confirmar recursos y crecimiento.
- IP privada fija y nombre DNS resoluble desde la red municipal. Definir qué VLAN, Wi-Fi,
  VPN y equipos están autorizados. No basta con estar en el mismo proveedor de Internet.
- Certificado HTTPS con SAN para ese nombre, clave correspondiente y cadena completa.
  Puede ser emitido por la CA interna municipal si su raíz se instala como confiable
  en TODOS los equipos y celulares autorizados. No aceptar advertencias TLS ni usar
  el certificado del ensayo. HTTPS es necesario para GPS en navegadores.
- Docker debe iniciar con el servidor. Firewall del host y red: permitir solo 80/443 desde
  las subredes autorizadas; SSH solo para mantenimiento. Sin NAT/router hacia Internet.
  Tener en cuenta las reglas propias de Docker: no confiar únicamente en UFW.
- MySQL y backend no publican puertos. Solo el backend tiene salida para los servicios
  externos existentes. La interfaz/mapas y Factiliza pueden necesitar salida a Internet;
  **no confundir salida con publicación**. Un entorno sin salida no garantiza mapas,
  consulta DNI ni recorridos externos. Las pruebas funcionales las realiza el usuario.
- Definir ubicación externa de respaldos cifrados, responsable, retención, RPO/RTO,
  alarma de fallos y prueba periódica de restauración. No dejar la única copia en el servidor.

## Verificar la entrega e instalar sin activar acceso

Extraer el paquete en un directorio de versión propio, conservando versiones anteriores.
El archivo exterior SHA256 permite comprobar la transferencia; SOURCE.sha256 identifica
exactamente los archivos incluidos, también los cambios locales sin commit.

```bash
sha256sum -c SOURCE.sha256
sha256sum -c IMAGES.sha256
docker load -i images.tar
```

Los identificadores de imagen en `images.lock.env` son inmutables (SHA256); no se descarga
`main` ni se reconstruye código en el servidor. `RELEASE.json` registra rama, HEAD, estado
Git y hash del código. `IMAGE-INSPECT.json` registra arquitectura e identificadores.

Crear secretos **en el servidor**, fuera del paquete y con terminal interactiva:

```bash
sudo python3 ops/production.py init-secrets --directory /etc/san-miguel/secrets
```

El usuario define una contraseña nueva (20 caracteres como mínimo, máximo 72 bytes por
BCrypt) sin eco. Se generan claves independientes de MySQL, JWT, cifrado y respaldo.
No pegar secretos en chat, argumentos, correo, Git o informes. El directorio es 0700;
los archivos montados son legibles por el UID no privilegiado del contenedor pero el
directorio impide acceso a otros usuarios del host. Docker monta cada secreto solo en
los servicios autorizados. Quien controla Docker controla los datos: limitar ese grupo.
Custodiar `backup_passphrase` **fuera del servidor**, separada de los respaldos.

Copiar `.env.prod.example` a `/etc/san-miguel/production.env`, modo 0600, y completar:

- COMPOSE_PROJECT_NAME exclusivo `sm-prod-municipalidad` (mantenerlo en actualizaciones).
- APP_BIND_IP: IP privada REAL del servidor, HTTP_PORT=80, HTTPS_PORT=443.
- SERVER_NAME: nombre DNS definitivo; CORS_ORIGINS=`https://NOMBRE_DEFINITIVO`.
- DB_NAME: nombre NUEVO exclusivo, DB_USER: cuenta propia sin root.
- ADMIN_BOOTSTRAP_USERNAME: el usuario definido al generar secretos.
- DB_PASSWORD_FILE=`/etc/san-miguel/secrets/db_password`.
- MYSQL_ROOT_PASSWORD_FILE=`/etc/san-miguel/secrets/mysql_root_password`.
- JWT_SECRET_FILE=`/etc/san-miguel/secrets/jwt_secret`.
- IDENTITY_MASTER_KEY_FILE=`/etc/san-miguel/secrets/identity_master_key`.
- ADMIN_BOOTSTRAP_PASSWORD_FILE=`/etc/san-miguel/secrets/admin_password`.
- TLS_CERT_PATH, TLS_KEY_PATH: archivos de cadena y clave con permisos protegidos.
- TLS_CA_PATH: certificado público de la CA interna, si corresponde.

```bash
sudo python3 ops/production.py check --env /etc/san-miguel/production.env
sudo python3 ops/production.py initialize --env /etc/san-miguel/production.env
sudo python3 ops/production.py status --env /etc/san-miguel/production.env
```

`initialize` rechaza volúmenes existentes y arranca solo MySQL/backend. Comprueba por SQL
las 29 migraciones, un administrador activo, sus siete capacidades exclusivamente
administrativas, cero datos operativos y almacenamiento vacío. No crea expedientes ni
realiza consultas DNI/API. Hibernate valida el esquema y el healthcheck valida readiness.
La configuración DNI queda DISABLED y sin credenciales; el administrador la configura después.

V1–V26 generan la semilla histórica; V27 la reconoce y retira dentro de ese arranque
aislado; V28/V29 completan el esquema. No se alteraron SQL históricos. La nueva protección
del perfil prod rechaza cualquier base previa sin V27 válida ANTES de migrar. Si el primer
aprovisionamiento se interrumpe antes de terminar V27, detener e investigar: no ejecutar
repair, baseline, borrado de tablas ni limpiezas automáticas. Nunca aplicar este procedimiento
sobre san_miguel, v20_manual ni una base QA existente.

Revisar destino, versión, conteos, estado, DNS, confianza del certificado, firewall y respaldo.
**Obtener confirmación final del responsable antes de habilitar acceso**, incluso en intranet:

```bash
sudo python3 ops/production.py activate --env /etc/san-miguel/production.env
```

El comando pide escribir el DNS confirmado. Arranca Nginx con HTTPS, redirige HTTP y bloquea
acceso directo a `/uploads/`. Las descargas privadas siguen los controladores autenticados.
No se cambia el contrato JWT existente (token en almacenamiento del navegador); mantener
equipos gestionados, HTTPS y acceso restringido. No se afirma que un cambio de infraestructura
elimine todos los riesgos de XSS o de equipos comprometidos.

## Operación diaria

```bash
sudo python3 ops/production.py status --env /etc/san-miguel/production.env
sudo python3 ops/production.py stop --env /etc/san-miguel/production.env
# Reiniciar núcleo sin habilitar entrada:
sudo python3 ops/production.py start --env /etc/san-miguel/production.env
# Habilitar nuevamente el acceso ya autorizado:
sudo python3 ops/production.py activate --env /etc/san-miguel/production.env
```

Para comandos directos, desde ESTA versión:

```bash
docker compose --env-file images.lock.env --env-file /etc/san-miguel/production.env \
  -p sm-prod-municipalidad -f compose.prod.yml -f compose.release.yml logs --tail 100 backend
```

No compartir logs sin revisarlos; pueden contener metadatos de personas durante operación real.
MySQL y adjuntos viven en volúmenes `sm-prod-municipalidad_mysql_prod_data` y
`sm-prod-municipalidad_uploads_prod_data`. **Nunca `down -v`, `volume prune`, `system prune
--volumes` ni eliminar volúmenes.** No regenerar la clave maestra al reiniciar/actualizar:
es imprescindible para descifrar el token del proveedor. Bootstrap no modifica contraseñas
ni añade otro administrador si la base productiva ya contiene una cuenta.

## Respaldo cifrado y restauración

```bash
sudo python3 ops/production.py backup --env /etc/san-miguel/production.env \
  --directory /RUTA_RESPALDOS_CONFIRMADA \
  --passphrase-file /etc/san-miguel/secrets/backup_passphrase
```

El respaldo detiene frontend/backend para mantener consistencia entre base y archivos,
mantiene MySQL activo, exporta la base completa (incluido Flyway), adjuntos, configuración,
identidad de imágenes y claves de recuperación; cifra todo con GnuPG AES256 y vuelve a
iniciar únicamente los servicios que estaban activos. La pausa depende del volumen de datos.
Requiere espacio temporal protegido para los datos sin cifrar; ejecutarlo en disco cifrado.
No incluye el passphrase de respaldo ni la clave TLS; custodiar esa frase por separado y
reemitir/restaurar TLS por el procedimiento de la municipalidad. Copiar `.tar.gpg` y `.sha256`
a almacenamiento externo protegido y supervisar el código de salida. No hay borrado de
respaldos ni retención automática sin una política aprobada.

Se incluyen plantillas `san-miguel-backup.service.example` y `san-miguel-backup.timer.example`
para systemd (propuesta: 03:00 America/Lima). No están instaladas ni habilitadas. Una vez
confirmadas las rutas, el horario, la pausa y la política, el ingeniero puede instalarlas
sin `.example` en `/etc/systemd/system`, ejecutar `systemctl daemon-reload` y
`systemctl enable --now san-miguel-backup.timer`. Supervisar fallos con `systemctl status`
y `journalctl -u san-miguel-backup.service`; acordar además la copia externa y su monitoreo.

Restaurar **siempre en un proyecto Docker NUEVO y volúmenes NUEVOS**:

1. Verificar SHA256 del respaldo y cargar las imágenes de la versión que lo produjo.
2. Preparar otro env externo: otro COMPOSE_PROJECT_NAME `sm-prod-recuperacion-FECHA`,
   nuevas rutas de secretos todavía inexistentes, misma DB_NAME/DB_USER, nombre/TLS válidos.
   No ejecutar init-secrets ni initialize: la restauración recupera las claves originales.
3. Ejecutar:

```bash
sudo python3 ops/production.py restore --env /etc/san-miguel/restore.env \
  --archive /RUTA/RESPALDO.tar.gpg --passphrase-file /RUTA_SEGURA/backup_passphrase
sudo python3 ops/production.py status --env /etc/san-miguel/restore.env
```

El script rechaza volúmenes existentes, secretos que ya existen e imágenes distintas del
respaldo. No publica Nginx. Revisar SQL/volúmenes/salud, aprobar el cambio de destino y
usar activate. No usar verify-initial después de comenzar la operación real: exige cero datos.
Ante error no elimina volúmenes ni intenta reparar; conservar evidencia y revisar.

## Actualizaciones futuras

1. Preparar otra versión con manifiestos y pruebas; conservar el paquete anterior.
2. Hacer un respaldo cifrado consistente y comprobar su copia externa.
3. Cargar imágenes nuevas; mantener proyecto, env, secretos y volúmenes existentes.
4. Desde el directorio nuevo usar el mismo Compose con `up -d --no-build --pull never
   --wait`; confirmar previamente cualquier nueva migración. No ejecutar initialize.
5. Revisar readiness/Flyway/Hibernate. Las pruebas de aceptación corresponden al usuario.
6. Si hubo cambio de esquema, no ejecutar una imagen antigua sobre la base nueva sin
   compatibilidad demostrada: restaurar el respaldo en volúmenes nuevos con su versión.

Renovar el certificado antes de expirar y recargar/recrear Nginx en mantenimiento.
Vigilar salud, disco, certificados, respaldos y actualizaciones de seguridad de imágenes.
El certificado/DNS/firewall, programación de respaldos y restauración periódica dependen
del servidor real y no se consideran completados por entregar este paquete.
