# San Miguel: integración real y ensayo productivo

Entrega del 13/10/2026, rama `willian/funcional-san-miguel`. Este informe actualiza los comandos y estados de las entregas anteriores. No se publicó el sistema ni se hizo push.

## Aplicaciones disponibles

| Montaje | URL | Backend y base |
|---|---|---|
| Aplicación local persistente | http://localhost:4200 | `sm-manual-integrated`, Java 21, `v20_manual` en `san-miguel-v20-audit` |
| Ensayo equivalente a producción | https://localhost:18443 | Perfil `prod`, `v20_prod_rehearsal`, MySQL propio y privado |
| Ensayo por red privada | https://192.168.1.67:18443 | Mismo frontend/API del ensayo HTTPS |

4200 ahora lo sirve **Nginx Docker**, no Angular CLI. Se comprobó `Server: nginx`, el binding real `127.0.0.1:4200→80` y el proxy `/api`. Frontend y backend usan las imágenes construidas con los archivos actuales; la compilación pública utiliza API relativa, sin `http://localhost:8080` dentro del bundle. El frontend HTTPS publica solamente loopback y la IP privada indicada. MySQL y backend del ensayo HTTPS no publican puertos.

La interfaz quedó abierta y autenticada con un administrador **sintético**, en `/admin`. Los montajes y sus volúmenes son independientes; no comparten expedientes entre sí. El backend local expone también http://localhost:8080/actuator/health, comprobado `UP`; ese resultado no sustituye los flujos que se detallan abajo.

El montaje local utiliza el perfil de desarrollo para preservar sus sesiones y su base existente. Su contraseña MySQL de aislamiento y JWT de desarrollo **no son credenciales productivas**. El montaje HTTPS sí usa secretos nuevos de ensayo, contraseña fuerte, JWT fuerte, usuario SQL propio y las verificaciones del perfil `prod`.

## Configuración y secretos

**Causa del error IDENTITY_MASTER_KEY:** el archivo privado `backend/.env.local` ya tenía una clave AES-256 válida, pero el arranque Java no lo importaba. No se generó ni sustituyó esa clave.

Antes de configurar se auditaron las credenciales: original y respaldo detenidos en V20 no tienen la tabla posterior de integración; `v20_manual` y `qa_sanmiguel` no tenían credenciales cifradas. Se conservó el archivo original, permisos 600, excluido de Git. El arranque standalone importa sus propiedades mediante Spring; Docker lee la copia **idéntica** de la clave mediante `configtree:/run/secrets/`. El script integrado compara ambas claves y se detiene si divergen.

Los secretos de este ensayo están en `.local/integration-20261013`, excluida de Git y del contexto Docker, dentro de directorios 700. No se incluyen valores en este informe. Los archivos son privados; los que necesita el backend tienen ACL de lectura exclusivamente para UID 10001, además de su propietario, sin acceso de grupo u otros. Compose monta los secretos en modo lectura; no están en `Config.Env`. El backend corre como usuario `app`, UID 10001.

Se verificó guardado cifrado, disponibilidad de descifrado y persistencia tras reinicio usando una credencial **sintética e inválida para el proveedor**, únicamente en el ensayo HTTPS. Se dejó ese proveedor desactivado; no se envió ninguna consulta externa. En el montaje local, Factiliza está seleccionado, **sin token inventado**, listo para que el usuario ingrese su credencial.

**DNI/Factiliza:** las consultas reales quedaron expresamente a cargo del usuario. No se verificaron token válido, plan, respuesta de un DNI real ni errores reales del proveedor. Los tests existentes de contrato usan `MockRestServiceServer`; sus respuestas exitosas y errores simulados no certifican a Factiliza. El formulario permite ingreso manual y no oculta errores. Configuración de integración fue rechazada con 403 a municipio y fiscalizador; no se retornan tokens guardados.

Para esas pruebas: `/admin` → Configuración → Factiliza → ingresar token → Guardar integración → consultar solamente un DNI autorizado. No pegar secretos en chats ni capturas. Mantener y respaldar la clave maestra: cambiarla sin recifrar con la anterior impediría recuperar las credenciales existentes.

## Qué se verificó realmente

| Alcance | Evidencia y resultado |
|---|---|
| **Interfaz: autenticación** | Login y redirección de ADMIN_SISTEMA, MUNICIPIO y FISCALIZADOR; carga y refresco del panel con sesión válida |
| **Interfaz: administración** | Configuración, selección/guardado de Factiliza sin token; catálogo y guardado de capacidades de MUNICIPIO sin conceder permisos nuevos; visualización de responsables municipales |
| **Interfaz: municipio** | Listado autorizado; creación de `FIS-2026-00016` sin dependencia de procedencia; datos sintéticos; enlace con coordenadas, vista previa/confirmación; PDF inicial; calendario grande con ocupación real, selección y visitas ordenadas; programación y reprogramación |
| **Interfaz: fiscalizador** | Visita propia, panel sin tarjetas redundantes, próximas visitas independientes, mapa/recorrido; números 1–4 y colores azul/verde/naranja/morado coincidentes en lista y SVG de marcadores; inicio de diligencia, identificación confirmada, avance, evidencia documental y finalización con acta |
| **Interfaz: revisión y cierre** | Diligencia llegó a pendientes; revisión con acta/evidencia de lectura; conformidad registrada e informe generado; expediente aparece `CERRADO`, conservando autores e historial |
| **API real HTTP/HTTPS + MySQL** | Creación de usuarios/identidades propias, roles/capacidades, dos fiscalizadores por municipal, configuración/preferencias, expediente multipart, documentos, programación/reprogramación/cancelación, calendario, diligencias, participante, evidencia, actas PDF, aclaración/respuesta, reanudación de revisión, seguimiento, conformidad, cierre e informe PDF |
| **Seguridad real** | 401 anónimo, 403 a municipio/fiscalizador ajeno y administrador sin capacidad operativa; 409 por identidad compartida; configuración de integración solo para administrador; rechazo CORS de origen no autorizado; ruta directa `/uploads/…` devuelve 404 |
| **Persistencia** | Huellas completas de base y 12 archivos del ensayo idénticas antes/después de reiniciar backend/frontend; todos los archivos fuente del montaje local conservados al copiar al nuevo volumen; documentos siguen descargándose con autorización |
| **Cliente independiente** | Desde otro namespace Docker se verificó frontend, login y API autorizada por IP privada, con TLS y hostname verificados y el origen HTTPS correspondiente |

La interfaz de capacidades reutiliza los endpoints existentes y respeta el atributo `configurable` del catálogo. **Los tres roles actuales son configurables**; ADMIN_SISTEMA mantiene por defecto únicamente sus capacidades administrativas. Debe conservar `ROLES_VER`, `ROLES_GESTIONAR` y `CAPACIDADES_GESTIONAR` para poder recuperar la administración: la interfaz y el backend impiden retirarlas. No se agregaron permisos operativos al abrir o guardar el formulario sin cambios. Cualquier modificación de permisos es explícita y el backend vuelve a comprobarlos; los usuarios deben renovar su sesión para actualizar la navegación visible. La auditoría administrativa amplia sigue siendo un alcance posterior; se conservan la autoría operativa, el historial de responsabilidad municipal y el actor de configuración DNI.

La creación de usuarios con nueva contraseña y la asignación municipal se comprobaron por API real; no se afirma haber realizado la entrada de nuevas credenciales desde la interfaz. Aclaraciones y seguimiento también se completaron por API, no en un segundo ciclo de interfaz. Desactivación/archivo y sus variantes de conservación histórica mantienen la cobertura automatizada anterior; no se archivaron ni borraron cuentas existentes durante este recorrido. Los casos alternativos de resultados, firma y permisos no recorridos en pantalla quedan cubiertos por tests, sin equipararlos a una revisión manual de todas sus variantes.

**GPS:** se pulsó la solicitud real; el navegador no obtuvo ubicación y mostró “Puedes continuar sin GPS”. La diligencia continuó correctamente con GPS no disponible. No se simuló una posición física. Validar captura y recorrido desde un celular con permisos y HTTPS confiable sigue pendiente.

La revisión responsive comprobó 1280 px y 390 px: ancho del documento 1265/375 px, respectivamente, sin desbordamiento horizontal del formulario. Las capturas del navegador integrado pueden aparecer escaladas; complementan las observaciones DOM, no equivalen a pruebas en un teléfono físico. Evidencias en `docs/validacion-integrada/`.

## Errores corregidos

1. Clave maestra existente no cargada: importación persistente y montaje seguro de secretos, sin regeneración.
2. **Desplazamiento de cinco horas en Docker:** la JVM usaba UTC con el contrato JDBC/Hibernate municipal. Se fijaron `TZ=America/Lima` y `-Duser.timezone=America/Lima` en ambos montajes. La revisión de la visita mostró primero 22:30 y después **17:30**, coincidiendo con MySQL y con la hora solicitada. Se añadieron comprobaciones reales petición = SQL = API.
3. Validación productiva CORS incompleta: ahora revisa cada origen y rechaza HTTP mezclado, entradas vacías, comodines, credenciales, rutas y parámetros.
4. Falta de editor de capacidades en la interfaz: añadido dentro de administración existente, reutilizando servicios, sin un módulo nuevo.
5. Arranque local con procesos de terminal y un Angular antiguo reteniendo 4200: reemplazado por Docker persistente. El script identifica y detiene únicamente procesos Java/npm/Angular de este checkout. Se añadió red de entrada al frontend: una red exclusivamente interna impedía publicar el puerto. Se verificó el binding efectivo y `Server: nginx`.
6. Secretos del Compose productivo en environment: ahora archivos montados mediante Compose secrets y configtree, compatibles con el usuario no privilegiado.
7. Acceso directo a archivos privados: rutas `/uploads/` bloqueadas en Nginx; descargas reales únicamente mediante los controladores autenticados.
8. Posibilidad de bloquear a todos los administradores retirando la gestión de roles: validación de los tres permisos mínimos, sin asignación de permisos operativos. La comprobación utiliza el rol persistido, también si se solicita la ruta con otra capitalización.

Se corrigieron también supuestos del verificador nuevo sobre el DTO plano de diligencia y la reanudación tras aclaración. Sus ejecuciones iniciales incompletas se conservaron; no se presentan como bugs del servidor ni como recorridos aprobados. Los registros sintéticos creados por el Docker anterior a la corrección horaria permanecen intactos y pueden tener horas desfasadas: no se trasladarán a producción. Los recorridos finales nuevos compararon explícitamente sus horas con la base.

## Resultados finales

| Validación | Aprobadas | Fallidas / errores |
|---|---:|---:|
| Backend Java 21, unidad e integración | **183** | **0**, ninguna omitida |
| Frontend, 22 archivos de pruebas | **88** | **0** |
| Recorrido API Docker HTTPS final | **76** | **0** |
| Recorrido API local final | **73** | **0** |

Compilación/empaquetado backend, build frontend e imágenes Docker correctos. Bundle inicial 381,18 kB; persisten únicamente advertencias CSS de `expediente-detalle` (18,22 kB) y `fiscalizador` (15,35 kB). El editor nuevo tiene cuatro pruebas; la validación de seguridad productiva, cuatro; la recuperación administrativa, tres. Tras la suite completa se verificaron nuevamente esas tres de forma focalizada, incluyendo la capitalización alternativa, y se empaquetó el JAR final. Las comprobaciones API son solicitudes reales y verificaciones SQL, no tests con servicios simulados.

El reinicio final adicional conservó exactamente las huellas de ambas bases y de **19 archivos locales y 18 del ensayo HTTPS**. Evidencia: `.local/integration-20261013/final-persistence.json`.

Logs finales: `/tmp/sm-integration-back-final.log`, `/tmp/sm-integration-front-final.log`, `/tmp/sm-integration-front-build.log`, `/tmp/sm-integration-api-docker-verified.log`, `/tmp/sm-integration-api-manual-verified.log`, `/tmp/sm-integration-lan-client.log`. Evidencias privadas, cuentas sintéticas y huellas: `.local/integration-20261013/`.

## Flyway y datos originales

- V20–V29 y la recuperación previa permanecen intactas; coincidieron todas las huellas del manifiesto previo. No se editaron migraciones históricas.
- `v20_manual` y `v20_clean`: **28 migraciones hasta V29**, sin V27.
- Base **nueva** del ensayo productivo: **29 migraciones hasta V29**, incluida V27. Antes de los fixtures se verificaron un solo usuario `qa_admin_inicial`, cero fiscalizadores, cero expedientes y `production_seed_guard` verificado/bootstrapped. V27 eliminó únicamente la semilla reconocida generada al inicializar esa base vacía; nunca se ejecutó en una base previa de desarrollo.
- Original `san_miguel`: intacta, V20 todavía fallida; sin repair, baseline ni regularización. Huella final idéntica: `58a0308a69f680ba7351a5e65273982cab78a90385fdf0409fbafa15815f337c`.
- Las identidades ambiguas del legado siguen pendientes como consta en `backend-v20-recuperacion.md`. No se seleccionaron propietarios ni se movieron autores.

No se borraron PDFs, registros previos, respaldos ni volúmenes. Los archivos del montaje local se copiaron de `backend/target/manual` a un volumen **nuevo**, conservando las fuentes y verificando sus hashes. La nueva producción deberá usar otro proyecto/volúmenes vacíos, sin reutilizar estos fixtures ni las credenciales de QA.

## Comandos actuales

Desde `/home/willian/muni/San_miguel`, con Docker, Compose, Python y `setfacl`:

```bash
# Aplicación local persistente, con los datos existentes de prueba:
docker start san-miguel-v20-audit
bash backend/scripts/start-integrated-local.sh

# Ensayo productivo YA PREPARADO, con sus volúmenes existentes:
docker compose --env-file .local/integration-20261013/compose.env \
  -p sm-integration-20261013 -f compose.prod.yml -f compose.qa.yml up -d

# Consultar estado:
docker compose -p sm-manual-integrated -f compose.manual.yml ps
docker compose --env-file .local/integration-20261013/compose.env \
  -p sm-integration-20261013 -f compose.prod.yml -f compose.qa.yml ps
```

Para construir cambios futuros, usar `build` con el mismo proyecto/archivos del ensayo y después `up -d`; el montaje local reutiliza esas imágenes. No correr simultáneamente `npm start` o el JAR standalone en 4200/8080. El script standalone de la entrega anterior conserva su utilidad, pero no es el comando canónico mientras Docker ocupa esos puertos. No usar `down -v`, `prune`, restauraciones sobre bases existentes ni V27 fuera de una instalación nueva validada.

Verificadores reproducibles: `bash backend/scripts/verify-v20-isolated.sh`, `npm test -- --watch=false`, `npm run build`, `python3 backend/scripts/verify-integrated-api.py --target docker` o `--target manual`. El verificador API requiere `requests`, conserva todos sus registros y crea nuevos fixtures al ejecutarlo; no repetirlo para consultar estado.

Acceso local: usuario sintético `qa_integracion_admin_20261013`, ya autenticado en el navegador entregado. No se muestran contraseñas. Sus credenciales generadas están en un archivo privado bajo `.local/integration-20261013`, permisos 600; los archivos de cuentas de cada recorrido también son privados. Desde `/admin` puedes crear tus propios usuarios con contraseñas elegidas por ti y asignar sus fiscalizadores. La cuenta inicial del ensayo HTTPS es `qa_admin_inicial`; su contraseña igualmente permanece en su archivo privado.

## Pendientes y bloqueos para publicar

1. **Factiliza real**, a cargo del usuario: token válido, DNI autorizado, plan/cuota, respuestas y errores reales. Configuración/cifrado están preparados; disponibilidad comercial del proveedor no está certificada.
2. **Dominio y certificado público válido**, renovación y acceso de dispositivos reales. El certificado actual es autofirmado y exclusivo del ensayo, válido 30 días. Para probarlo en otro dispositivo debe confiarse manualmente en el certificado público de QA, sin desactivar validación TLS. La prueba desde el cliente Docker no sustituye una computadora/celular físicos.
3. **GPS físico y ronda manual completa de variantes**; no reemplazar estas verificaciones por tests de contrato ni por healthcheck.
4. Preparar secretos **productivos propios**, custodiar y respaldar clave maestra/JWT, respaldos de base y archivos y restauración operativa; usar un proyecto nuevo y únicamente el administrador inicial. La preparación de QA no autoriza la publicación definitiva.
5. El contenedor original de desarrollo mantiene su binding previo **0.0.0.0:3307**. No se alteró por la restricción sobre el entorno original. El montaje productivo no expone MySQL, pero no debe publicarse un servidor que conserve ese puerto de desarrollo accesible sin un plan de aislamiento autorizado.
6. Advertencias CSS no bloqueantes; auditoría administrativa amplia pendiente del módulo posterior acordado. Revisar con el responsable la política de capacidades y el límite **orientativo**, que advierte y mantiene validación de conflictos, sin impedir matemáticamente exceder la carga.

No se declara listo para publicación pública. Hay aplicación integrada y recorridos reales verificados; permanecen expresamente los controles externos, de dispositivos e infraestructura indicados.

## Archivos de esta fase

Infraestructura: `compose.prod.yml`, `compose.qa.yml`, `compose.manual.yml`, `.env.prod.example`, `.gitignore`, `.dockerignore`, `docker/nginx.prod.conf`, `docker/nginx.manual.conf`.

Arranque/verificación: `backend/scripts/start-manual-isolated.sh`, `backend/scripts/start-integrated-local.sh`, `backend/scripts/verify-integrated-api.py`.

Backend: `ProductionStartupGuard.java`, `ProductionStartupGuardTest.java`, `RoleCapabilityService.java` y `RoleCapabilityServiceTest.java`. Frontend: `src/app/pages/admin/admin.ts`, `admin.html`, `admin.css`, `admin.spec.ts`.

Informe, inventario y capturas de esta fase se añaden a `docs/`. Los demás cambios locales corresponden a los avances anteriores conservados. No se retiraron otros componentes ni migraciones; los métodos existentes de roles/capacidades ahora se reutilizan desde la interfaz.
