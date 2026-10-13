# Cierre: interfaz administrativa y limpieza local — 13/10/2026

Se retiró exclusivamente el editor «Roles y capacidades» del frontend: sección, selector, controles, estado, estilos, métodos HTTP y tipos sin otros consumidores. Se retiraron sus cuatro tests específicos; las 84 pruebas frontend restantes aprobaron y la imagen de producción compiló. Las advertencias CSS previas no bloquean el build. Backend y autorización no se modificaron.

Archivos de código: `src/app/pages/admin/admin.ts`, `admin.html`, `admin.css`, `admin.spec.ts` y `src/app/services/usuarios.service.ts`.

Antes de limpiar se confirmó el binding real 127.0.0.1:8080, DB_NAME=v20_manual, alias mysql-audit y conexiones MySQL desde la IP del backend a esa base. El contenedor MySQL es san-miguel-v20-audit, expuesto exclusivamente en 127.0.0.1:13317.

El usuario eligió explícitamente conservar `admin` (id 8) frente a `qa_integracion_admin_20261013` (id 9). Se preservaron exactamente el registro de admin, su hash de contraseña, su rol ADMIN_SISTEMA y sus preferencias. Tiene las capacidades de creación y gestión de usuarios. No se cambió ni mostró su contraseña ni se probó manualmente el login.

Se detuvo el backend, se respaldaron base y adjuntos y se ensayaron todos los DELETE en una transacción con ROLLBACK. Después se ejecutó la transacción definitiva con COMMIT. Las claves foráneas permanecieron activas; se anularon únicamente referencias cíclicas entre registros destinados a eliminación. No se usó TRUNCATE ni se reiniciaron secuencias.

Resultado: 1 administrador activo; 0 cuentas MUNICIPIO/FISCALIZADOR; 0 fiscalizadores; 0 expedientes, solicitudes, programaciones, diligencias, actas, participantes, evidencias, documentos, revisiones, aclaraciones e historiales operativos. Se eliminaron 19 archivos únicamente de las carpetas de adjuntos del volumen sm-manual-uploads-20261013.

Se compararon y conservaron sin cambios los 28 registros de Flyway y sus checksums, 3 roles, 41 capacidades, 42 asociaciones rol-capacidad, configuración global e integración DNI. El manifiesto previo de 30 archivos de migraciones/recuperación sigue idéntico. No se ejecutó V27 ni se modificaron otras bases, volúmenes o servicios QA/productivos.

Respaldo privado y evidencia: `.local/cleanup-v20-manual-20261013/` (SQL, TAR y result.json), fuera de Git. El respaldo conserva los datos anteriores para recuperación; no forma parte del almacenamiento activo. Logs: `/tmp/sm-hide-role-tests.log` y `/tmp/sm-hide-role-build.log`.

La sesión anterior de la cuenta QA eliminada ya no sirve. Iniciar sesión con `admin` y su contraseña habitual. No se generaron nuevas cuentas, expedientes ni consultas externas para verificarlo.

Inicio tras reiniciar Ubuntu:

```bash
cd /home/willian/muni/San_miguel
sudo systemctl start docker
docker start san-miguel-v20-audit
bash backend/scripts/start-integrated-local.sh
```

Detención sin borrar datos:

```bash
cd /home/willian/muni/San_miguel
docker compose -p sm-manual-integrated -f compose.manual.yml stop
docker stop san-miguel-v20-audit
```
