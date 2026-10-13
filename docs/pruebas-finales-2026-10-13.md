# San Miguel: entrega para pruebas manuales — 13/10/2026

Rama `willian/funcional-san-miguel`. Trabajo sobre los cambios locales existentes, sin push ni despliegue. No se modificaron configuraciones productivas ni se ejecutó V27.

## Servicios disponibles

| Servicio | Dirección / estado comprobado |
|---|---|
| Angular | http://localhost:4200 — HTTP 200 |
| Spring Boot / Java 21 | http://localhost:8080/actuator/health — `{"status":"UP"}` |
| MySQL 8.4.11 de pruebas | `127.0.0.1:13317`, contenedor `san-miguel-v20-audit` |
| Base de pruebas manuales | `v20_manual`, 28 migraciones exitosas, última V29, sin V27 |

El frontend usa `http://localhost:8080`. El preflight CORS desde `http://localhost:4200` respondió 200 con el origen autorizado. Los procesos se dejaron desacoplados del terminal; PID del backend en `/tmp/sm-manual-backend.pid` y del frontend en `/tmp/sm-manual-frontend.pid`. Logs: `/tmp/sm-functional-backend-manual.log` y `/tmp/sm-functional-frontend-manual.log`.

## Funciones terminadas

1. `/inicio` y `/` redirigen por rol y capacidades: administración, fiscalizaciones o portal fiscalizador. Se retiraron los cuatro archivos del componente Inicio y se conservó autenticación JWT.
2. Formulario de fiscalización con secciones, validación cercana al campo, contraste, botones redondeados y regreso al listado. No solicita dependencia de procedencia; backend tampoco la exige. La columna y sus valores históricos permanecen.
3. Calendario mensual grande, año, selección y teclado; una consulta por mes. Días sin carga blancos, carga baja verde, desde 80 % hasta capacidad amarillo, exceso rojo. Se consultan visitas autorizadas del ámbito municipal; canceladas, reemplazadas y concluidas no ocupan capacidad. Selección muestra las visitas ordenadas por hora, detalle y estado vacío real. Se actualiza por cambios de agenda y dispone de actualización manual.
4. Portal fiscalizador sin las tres tarjetas redundantes. Se conservan recorrido, ubicación, mapa y acceso a consulta/diligencia.
5. Próximas visitas en `/fiscalizador/proximas`, ordenadas y accesibles desde navegación.
6. Números y colores deterministas compartidos entre recorrido y marcadores Leaflet, sin duplicados; se mantienen rutas, GPS y Google Maps.
7. ADMIN_SISTEMA asigna explícitamente un responsable municipal a varios fiscalizadores desde el panel existente. V29 añade esa relación 1:N y su historial de cambios con actor. No cambia la identidad de acceso del fiscalizador ni crea una relación N:M. Se verifican capacidades, ámbito municipal y propiedad operativa.

Los métodos de ubicación están disponibles con el backend aislado: el fallo observado correspondía a la indisponibilidad del backend. La pantalla distingue conexión, sesión, permisos y error de API; una configuración vacía tiene estado propio y no se disfraza de error. Las pruebas HTTP verificaron `/api/settings/me` y la creación sin dependencia.

El límite administrativo existente sigue siendo **orientativo**: se indica carga y exceso, manteniendo las comprobaciones de disponibilidad, conflicto horario y responsabilidad del backend. No se cambió su significado a una prohibición absoluta de agendar más visitas.

## Validación y alcance visual

- Backend: **176 aprobadas, 0 fallidas, 0 errores, 0 omitidas**; compilación y empaquetado Java 21 correctos. Se conservó el resultado válido del 13/10 a las 04:42; no hubo cambios posteriores en fuentes Java ni migraciones. Incluye autorización, identidad, historial, agenda, gestión municipal y recuperación sintética de V20.
- Frontend: **84 aprobadas, 0 fallidas**, en 22 archivos. Se repitió a las 05:30 después de los últimos ajustes responsive.
- Build de producción: correcto; bundle inicial **381,18 kB**, sin advertencia del presupuesto inicial. Quedan advertencias CSS de `expediente-detalle` (18,22 kB) y `fiscalizador` (15,35 kB); no impiden el build.
- Flyway validó las 28 migraciones al arrancar el JAR final sobre `v20_manual`; V29 también está aplicada y comprobada en `v20_clean`. V27 no aparece en sus historiales.
- `git diff --check` y sintaxis del script de arranque: correctos. Se probó el reinicio seguro y el rechazo de un nombre de administrador existente antes de solicitar contraseña.
- Revisión móvil previa: corregido el desbordamiento de 397 px en un viewport de 376 px; la comprobación DOM posterior dio 361 px, sin desbordamiento horizontal. Se revisó también a 390 px. Evidencia disponible en `validacion-visual/calendario-movil.png`.
- **No se completó la última pasada visual de escritorio y móvil en esta continuación:** la herramienta de navegador rechazó abrir la URL local por su política de seguridad. No se intentó eludirla. La revisión visual manual de las seis funciones queda como pendiente explícito; los resultados automatizados no sustituyen esa revisión.

Evidencia de pruebas: `/tmp/sm-functional-back-final.log`, `/tmp/sm-functional-front-final.log`, `/tmp/sm-functional-front-build.log` y `backend/target/surefire-reports`.

## Datos y Flyway preservados

La original `san_miguel` del puerto 3307 permanece intacta y con V20 fallida. Su huella reproducible sigue siendo `58a0308a69f680ba7351a5e65273982cab78a90385fdf0409fbafa15815f337c`, idéntica a la anterior. No se ejecutó repair ni una corrección de cuentas allí.

Las huellas de las migraciones históricas, V28, V27 de producción, preflight de identidad y herramienta de recuperación coinciden con el manifiesto previo. V29 es nueva. En `v20_backup`, la copia restaurada conserva V20 `success=0`, checksum `1487232706`; no se resolvieron arbitrariamente sus identidades. La recuperación se verificó con escenarios sintéticos independientes.

La base manual no contiene visitas ficticias: el conteo comprobado fue cero. Contiene cuentas y catálogos de desarrollo de las migraciones originales y el administrador aislado creado durante la preparación. No se infieren responsabilidades municipales; inicialmente habrá que asignarlas desde administración para poder programar con esos fiscalizadores.

Siguen pendientes las decisiones de FIS-001 (cuentas 3/12/16/20/24), FIS-002 (13/17/21/25) y Mayta Arotaype (8). FIS-001 con cuenta 3 es solo una propuesta provisional; no se aplicó a los datos reales. Opciones, consecuencias y procedimiento de recuperación: [backend-v20-recuperacion.md](backend-v20-recuperacion.md).

## Arranque y acceso seguro

Desde `/home/willian/muni/San_miguel`, si los servicios están detenidos:

```bash
docker start san-miguel-v20-audit
bash backend/scripts/start-manual-isolated.sh
# En otro terminal:
npm start -- --host 127.0.0.1 --port 4200
```

El script fija Java 21, base `v20_manual`, puerto 13317, perfil `manual-qa`, ubicación de migraciones sin producción y target 29. Comprueba etiqueta y puerto del contenedor. No crea/restaura bases ni cambia credenciales existentes.

Usuarios disponibles en la base manual: `admin-pruebas` (ADMIN_SISTEMA), `municipio` y `fiscalizador`. No se publican sus contraseñas. Para disponer de un acceso administrativo con una contraseña elegida por ti, ejecuta en un terminal interactivo:

```bash
bash backend/scripts/start-manual-isolated.sh --restart --create-admin
```

Elige un nombre nuevo y escribe/confirma una contraseña de al menos ocho caracteres mediante entrada oculta. El script rechaza nombres existentes; el bootstrap crea solo la cuenta nueva en `v20_manual`, conserva las otras cuentas y reinicia únicamente el backend manual identificado por PID, ejecutable y directorio. Después entra en `/login`. El administrador nuevo puede crear cuentas de prueba de los otros roles desde `/admin`, dar a cada fiscalizador su propia identidad y asignarles un responsable municipal. ADMIN_SISTEMA no recibe permisos operativos automáticos. Para retomar un backend detenido sin reinicio, usa `--create-admin` sin `--restart`.

## Recorrido de pruebas manuales

| Ruta | Comprobación |
|---|---|
| `/login`, `/inicio`, `/` | Entrada y redirección según rol; refresco del navegador |
| `/admin` | Crear usuarios de prueba, preferencias y responsabilidad municipal |
| `/expedientes` | Listado autorizado |
| `/expedientes/nuevo` | Formulario sin dependencia, métodos de ubicación, calendario, leyenda, selección y visitas del día |
| `/fiscalizador` | Resumen, ubicación, mapa y acciones sin tarjetas redundantes |
| `/fiscalizador/proximas` | Visitas futuras en una lista independiente |
| `/fiscalizador/ruta` | Coincidencia de números/colores en lista y mapa |
| `/fiscalizador/diligencia/:programacionId` | Atención de una programación propia existente |

Para comprobar colores con ocupación real, crea y programa tus fiscalizaciones en la base manual mediante las cuentas autorizadas; no se insertaron visitas para colorear artificialmente la pantalla. Prueba también cancelación y reprogramación, y verifica que otra cuenta sin responsabilidad no acceda al expediente.

## Archivos y pendientes antes de producción

Inventario completo de archivos locales modificados/agregados/eliminados: [pruebas-finales-archivos.txt](pruebas-finales-archivos.txt). Principales grupos: rutas/guards/login, formulario y calendario, portal fiscalizador/identificadores de visita, administración/servicios Angular, control de ámbito municipal/agenda/gestión del backend, V29 y pruebas. Los avances de identidad/V20/V28 permanecen en el mismo estado local.

Pendientes: completar la revisión visual manual; resolver las identidades ambiguas antes de recuperar la base heredada; revisar las dos advertencias CSS cuando corresponda; validar con el ingeniero el alcance municipal y el límite orientativo. La instalación productiva nueva, su administrador inicial, secretos y V27 requieren su procedimiento posterior: no se ejecutaron en esta entrega. No se borraron PDFs, datos, respaldos ni volúmenes.
