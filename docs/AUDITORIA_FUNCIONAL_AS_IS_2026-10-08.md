# Auditoría funcional AS-IS — Municipalidad Distrital de San Miguel

**Fecha de corte:** 2026-10-08

**Proyecto:** `/home/willian/muni/San_miguel`

**Rama auditada:** `avance-gabriela`

**Alcance:** lectura estática integral de Git, Angular, Spring Boot, entidades, repositorios y Flyway. No es una auditoría de despliegue.

## Convenciones y límites de evidencia

- **CÓDIGO:** comportamiento comprobado por implementación y citado con archivo/línea.
- **PRUEBA HISTÓRICA:** resultado documentado por el proyecto; no se volvió a ejecutar en esta auditoría.
- **NO VERIFICADO:** requiere ejecución o consulta de una base aislada. No se consultó ni modificó la base existente, no se levantaron servicios y no se ejecutaron pruebas.
- Las referencias remotas Git son las que ya existían localmente. No se hizo `fetch`, por lo que no se afirma que representen hoy el servidor remoto.

## 1. Resumen ejecutivo

El sistema contiene dos modelos que conviven y se sincronizan: el flujo heredado de **Solicitudes** y el flujo estructurado de **Expedientes**. El segundo ya implementa programación, diligencia propia, evidencias, acta, revisión municipal, aclaración, conclusión, seguimiento y cierre. La solicitud puede originar un expediente y la lógica de compatibilidad replica programación/resultados entre ambos modelos (`ExpedienteFiscalizacionService.java:181-229`; `FASE2A_EXPEDIENTE_FISCALIZACION.md:29-33`). No deben considerarse módulos independientes sin relación, pero tampoco son equivalentes: Predios permanece aislado y Solicitudes conserva operaciones legacy.

El control de acceso es efectivamente dinámico en backend: las autoridades se recalculan desde roles/capacidades persistidos en cada petición JWT (`SecurityConfig.java:24-29`; `JwtAuthenticationFilter.java:15-25`). Angular usa la copia de capacidades guardada al iniciar sesión (`src/app/services/auth.service.ts:6-7`), de modo que los cambios de rol se aplican de inmediato en API pero el menú/guards pueden quedar desactualizados hasta volver a autenticarse.

Hallazgos funcionales prioritarios:

1. **CORREGIR:** en diligencia, Angular envía `AUSENTE`, pero Java solo admite `ADMINISTRADO_AUSENTE`; seleccionar “Persona ausente” hace que el PATCH falle al deserializar (`src/app/pages/diligencia/diligencia.html:24`; `SituacionPersona.java:3`; `ApiDtos.java:84-87`).
2. **CORREGIR:** el portal ofrece “Iniciar fiscalización” para visitas `VENCIDA`, pero el backend solo permite `PROGRAMADA` (`src/app/pages/fiscalizador/fiscalizador.ts:104-116`; `DiligenciaFiscalizacionService.java:58-66`).
3. **REVISAR/CORREGIR:** una revisión guarda el revisor que la inicia, pero solicitar aclaración y concluir no comprueban que el usuario actual sea ese revisor; cualquier cuenta con las capacidades puede continuarla (`RevisionFiscalizacionService.java:55-70,86-114,137`).
4. **COMPLETAR:** la creación de expediente encadena tres operaciones con permisos distintos. Puede persistir el expediente y después fallar documento o programación, dejando un alta parcial (`expediente-nuevo.ts:70-90`; `ExpedienteFiscalizacionController.java:26-36`).
5. **CORREGIR:** varias pantallas se habilitan con una sola capacidad, pero al cargar llaman endpoints adicionales; roles personalizados válidos pueden abrir la ruta y recibir 403 o perder toda la pantalla.
6. **EVALUAR ELIMINACIÓN / REVISAR:** `/usuarios` duplica parcialmente la pestaña de usuarios de `/admin`; `/predial` persiste predios aislados y su enlace al mapa no devuelve coordenadas a ese formulario; `/nueva-cita` solo es alias legacy de `/nueva-solicitud`.

La documentación histórica afirma que el 30/09/2026 se validaron V1–V18, 61 pruebas backend (2 omitidas), 27 frontend y un flujo integrado completo (`docs/FASE_3B_RESULTADO.md:74-122`). Esa evidencia precede los commits visuales del 07/10 y **no se tomó como resultado vigente de esta rama**.

## 2. Estado Git auditado

| Elemento | Estado verificado |
|---|---|
| Rama | `avance-gabriela` |
| HEAD | `109cf96b435b9afd86a414962b835f56b88ddf8d` |
| Commit | `imagenes/bg_muni_facade.jpg`, 2026-10-07 22:30:05 -05:00 |
| Árbol antes del informe | limpio: `## avance-gabriela...origin/avance-gabriela` |
| Remote | `origin` → `https://github.com/Will-M7/Muni_Random.git` (fetch/push) |
| Comparación con referencia local `origin/avance-gabriela` | 0 atrás / 0 adelante |
| Comparación con referencia local `origin/main` | 0 atrás / 2 adelante |
| `origin/main` local | `821147c` |
| Sincronización remota | **NO VERIFICADA**: no se ejecutó `fetch` |

No existían cambios sin guardar al iniciar. No se modificaron ni limpiaron cambios de `avance-gabriela`. Este informe es el único archivo creado.

## 3. Inventario funcional

### 3.1 Mapa de pantallas y módulos

| Módulo/pantalla | Ruta Angular | Acceso de ruta | Propósito y operaciones comprobadas | Endpoint/entidades | Estado, problemas y dependencias |
|---|---|---|---|---|---|
| Login | `/login` | Público | Usuario/contraseña, mostrar contraseña, iniciar sesión, redirigir por capacidad | `POST /api/auth/login`; `AppUser`, roles | Activo. “Recordar sesión” es solo visual: la sesión siempre queda en `localStorage` (`login.html:37-44`; `auth.service.ts:7`). No permite seleccionar rol aunque backend acepta uno opcional. |
| Inicio | `/inicio` | `SOLICITUD_VER` | Accesos a registrar y consultar solicitudes | Sin llamada propia | Activo pero muestra “Registrar” sin comprobar `SOLICITUD_CREAR`; un rol personalizado puede ser redirigido por guard. |
| Registro/edición de solicitud | `/nueva-solicitud`, `/:codigo`; alias `/nueva-cita` | crear: `SOLICITUD_CREAR`; editar: `SOLICITUD_EDITAR` | DNI/RENIEC, datos personales, PDF, ubicación, fecha/hora, fiscalizador, borrador local, crear/editar | `/api/reniec`, `/api/solicitudes`, catálogo; `Solicitud`, `Fiscalizador`, expediente sincronizado | Activo legacy. Crear permite asignar sin `FISCALIZADOR_ASIGNAR`; editar exige ambas capacidades en backend. “Eliminar archivo” no elimina el PDF ya persistido. |
| Solicitudes | `/solicitudes` | `SOLICITUD_VER` | Listar, filtrar, detalle modal, PDF, mapa, reporte, editar, cambiar estado | `/api/solicitudes/**`; `Solicitud`, `SolicitudHistorial` | Activo legacy. Botones de crear/editar/estado no se ocultan por capacidades. DELETE existe en API pero no en UI. |
| Mapa | `/mapa` | `SOLICITUD_VER` | Leaflet, búsqueda Nominatim, clic en mapa, guarda coordenadas en `localStorage` y retorna | OSM/Nominatim desde navegador; sin endpoint propio | Activo para solicitud/expediente. Ruta impide usarlo a quien solo tenga `EXPEDIENTE_CREAR`. Predial no configura retorno correcto. |
| Predios | `/predial` | `SOLICITUD_CREAR` | Dirección, referencia, tipo vivienda, código predial, registrar | `POST /api/predios`; `Predio` | Aislado/incompleto. No hay listado, edición, consulta ni vínculo con Solicitud/Expediente. El mapa retorna a solicitud y el formulario no consume coordenadas. |
| Expedientes | `/expedientes` | `EXPEDIENTE_VER` | Listado, filtros por origen/estado/visita/fiscalizador/fecha, acceso a revisión y detalle | `GET /api/expedientes`; `ExpedienteFiscalizacion`, programaciones | Activo. “Nuevo expediente” aparece sin `EXPEDIENTE_CREAR`. |
| Nuevo expediente | `/expedientes/nuevo` | `EXPEDIENTE_CREAR` | Origen, solicitud opcional, administrado, RENIEC, mapa, PDF y primera programación opcional | crear expediente, documento, programación, solicitudes, RENIEC, catálogo | Activo pero compuesto/no atómico. Requiere de hecho capacidades adicionales. No existe UI posterior para agregar primera programación si se omite. |
| Detalle expediente | `/expedientes/:codigo` | `EXPEDIENTE_VER` | Datos, PDFs, programaciones, reprogramar/cancelar, ciclos, actas, historial | expedientes, actas, revisiones, programaciones | Parcial por acoplamiento: carga actas (`ACTA_VER`) y ciclos (`REVISION_VER`) junto con el expediente; si falla una promesa, se pierde la carga global (`expediente-detalle.ts:18-22`). No hay edición ni adjunto posterior pese a existir API. |
| Bandeja revisión | `/revisiones` | `REVISION_VER` | Filtra diligencias realizadas/finalizadas e inicia/retoma revisión | `/api/revisiones/pendientes`, `/diligencias/{id}/iniciar`; `RevisionFiscalizacion` | Activo. El botón llama `REVISION_INICIAR` sin comprobarla en UI. El filtro ofrece `NO_REALIZADA`, pero el backend solo devuelve diligencias `FINALIZADA` con resultado fijo `REALIZADA`. |
| Detalle revisión | `/revisiones/:id` | `REVISION_VER` | Lectura de hechos/evidencias/acta, aclaración, conclusión, informe, seguimiento | `/api/revisiones/**`, catálogo; revisión, aclaración, programación | Activo. Acciones no se ocultan por capacidades específicas. Catálogo se intenta aunque el usuario no pueda crear seguimiento. |
| Portal fiscalizador | `/fiscalizador`, `/ruta`, `/historial` | tareas/ruta | Une tareas legacy y expedientes, detalle, documentos, resultado legacy, ruta heurística, aclaraciones | `/api/fiscalizador/**`, aclaraciones | Activo con coexistencia legacy/nativa. Una falla en cualquiera de cinco llamadas paralelas hace fallar toda la carga (`fiscalizador.ts:84-98`). Expedientes sin coordenadas se excluyen de la lista activa. |
| Diligencia | `/fiscalizador/diligencia/:programacionId` | `DILIGENCIA_INICIAR` | Iniciar, autosave, persona, participantes, evidencias, finalizar/no realizada, acta/copia firmada | `/api/fiscalizador/programaciones/**`, `/diligencias/**`, `/actas/**` | Implementación extensa con ownership backend. Ruta exige iniciar incluso para reabrir una diligencia ya iniciada, aunque editar/ver se rigen por otras capacidades. Bug `AUSENTE`. |
| Usuarios simple | `/usuarios` | `USUARIOS_VER` | Lista y crea solo MUNICIPIO/FISCALIZADOR | `/api/usuarios`; `AppUser`, `AppRole` | Duplicado parcial/legacy frente a `/admin`; sin estado ni roles múltiples. MUNICIPIO por defecto ya no posee estas capacidades. |
| Administración | `/admin` | `ROLES_VER` | Usuarios, alta, roles múltiples, activar/desactivar, capacidades de rol | `/api/usuarios`, `/api/admin/roles`, `/capacidades` | Activo para ADMIN por defecto. La ruta solo exige `ROLES_VER`, pero la carga requiere también `USUARIOS_VER` y `CAPACIDADES_GESTIONAR`; controles no se segmentan. No crea/elimina roles ni capacidades. |
| Perfil | `/perfil` | autenticado | Muestra datos de sesión y vuelve a ruta principal | Sin endpoint | Activo. Muestra capacidades/rol de la sesión, potencialmente obsoletos. Texto de pie indica “prototipo con almacenamiento local”, incompatible con la arquitectura real (`perfil.html`). |

Rutas y guards: `src/app/app.routes.ts:19-44`. Servicios HTTP centralizados: `src/app/services/verificacion.service.ts:30-84`. Menú principal: `src/app/app.html:8-18` y `src/app/app.ts:15-19`.

### 3.2 Backend, entidades y persistencia

Controladores activos: autenticación, usuarios, administración, catálogo, solicitudes, RENIEC, predios, expedientes, programaciones, fiscalizador legacy, diligencias, actas y revisiones (`backend/src/main/java/.../controller`). Todos los `/api/**`, salvo `/api/auth/**`, requieren autenticación (`SecurityConfig.java:37-42`).

Entidades principales:

- Seguridad: `AppUser` ↔ `AppRole` ↔ `Capability`; columna enum `Rol` conservada por compatibilidad.
- Legacy: `Solicitud`, `SolicitudHistorial`, `Fiscalizador`, `Predio`.
- Flujo estructurado: `ExpedienteFiscalizacion`, `ProgramacionFiscalizacion`, `ExpedienteHistorial`, `DiligenciaFiscalizacion`, `DiligenciaParticipante`, `EvidenciaFiscalizacion`, `ActaFiscalizacion`, `RevisionFiscalizacion`, `SolicitudAclaracionFiscalizacion`.
- Repositorios JPA existen para todas ellas; Predio solo dispone del `JpaRepository` básico y una ruta POST.

Flyway contiene **18 archivos V1–V18**. V11 introduce roles/capacidades, V12 separa administración de usuarios y directorio de fiscalizadores, V13 expedientes/programación y migración desde solicitudes, V17 diligencias/actas y V18 revisión/seguimiento. Que los archivos existan está comprobado; que estén aplicados en la base actual es **NO VERIFICADO**. El informe histórico afirma V18 validada el 30/09 (`docs/FASE_3B_RESULTADO.md:10-16,113-120`).

### 3.3 Funciones backend sin interfaz equivalente

- `DELETE /api/solicitudes/{codigo}` no tiene botón actual (`SolicitudController.java:12`).
- `PUT /api/expedientes/{codigo}` no tiene formulario de edición (`ExpedienteFiscalizacionController.java:33`).
- `POST /api/expedientes/{codigo}/documento` solo se usa durante el alta; no existe acción posterior en detalle.
- `POST /api/expedientes/{codigo}/programaciones` solo se usa como primera programación dentro del alta; un expediente creado sin ella no tiene botón “Programar”.
- `GET /api/programaciones/mis-asignaciones` existe, pero el frontend usa principalmente `/fiscalizador/mis-expedientes` (`ProgramacionFiscalizacionController.java:20`; `verificacion.service.ts:43`).
- `GET /api/actas/{id}` y evidencia vía acta existen, sin consumo claro en Angular; Angular descarga PDF/copia y usa evidencia de diligencia/revisión.

## 4. Matriz de roles y capacidades

### 4.1 Modelo dinámico comprobado

- Los roles sembrados son exactamente `ADMIN_SISTEMA`, `MUNICIPIO`, `FISCALIZADOR`; todos están marcados configurables (`V11__capability_based_authorization.sql:31-34`).
- Un usuario puede tener varios roles. Sus capacidades efectivas son la unión; no hay denegaciones explícitas ni precedencia negativa (`SecurityConfig.java:24-29`; `AuthService.java:8-18`).
- El login devuelve todos los roles/capacidades y un rol principal; Angular decide la portada por prioridad: roles, tareas, expedientes, solicitudes, perfil (`auth.service.ts:7`). El rol principal no limita la unión.
- El backend vuelve a cargar usuario/roles por petición JWT; desactivar usuario o retirar capacidades surte efecto sin renovar token. Angular no refresca su copia hasta nuevo login.
- No existe API para crear, renombrar o eliminar roles/capacidades. “Dinámico” significa reasignar roles existentes a usuarios y reemplazar capacidades de roles existentes (`RoleCapabilityService.java:14-22`; `AdminController.java:13-15`).
- Aunque la tabla permitiría nuevos nombres, `UsuarioRequest` exige el enum `Rol` y `UsuarioService` usa `Rol.valueOf` al conservar la columna legacy; la API actual solo soporta los tres nombres (`ApiDtos.java:50-55`; `UsuarioService.java:27-49`).

### 4.2 Capacidades por rol sembrado

| Rol | Capacidades vigentes según V11–V18 |
|---|---|
| ADMIN_SISTEMA | `USUARIOS_VER`, `USUARIOS_CREAR`, `USUARIOS_EDITAR`, `USUARIOS_ESTADO`, `ROLES_VER`, `ROLES_GESTIONAR`, `CAPACIDADES_GESTIONAR` |
| MUNICIPIO | `SOLICITUD_VER`, `SOLICITUD_CREAR`, `SOLICITUD_EDITAR`, `SOLICITUD_ESTADO_CAMBIAR`, `PROGRAMACION_VER`, `PROGRAMACION_GESTIONAR` (legacy, sin enforcement observado), `FISCALIZADOR_ASIGNAR`, `FISCALIZADORES_DISPONIBLES_VER`, `EXPEDIENTE_VER`, `EXPEDIENTE_CREAR`, `EXPEDIENTE_EDITAR`, `PROGRAMACION_CREAR`, `PROGRAMACION_REPROGRAMAR`, `PROGRAMACION_CANCELAR`, `ACTA_VER`, `REVISION_VER`, `REVISION_INICIAR`, `ACLARACION_SOLICITAR`, `REVISION_CONCLUIR`, `EXPEDIENTE_CERRAR`, `SEGUIMIENTO_CREAR`, `INFORME_CONCLUSION_VER` |
| FISCALIZADOR | `TAREAS_PROPIAS_VER`, `FISCALIZACION_EJECUTAR`, `RESULTADO_REGISTRAR`, `RUTA_VER`, `DOCUMENTO_TAREA_VER`, `DILIGENCIA_INICIAR`, `DILIGENCIA_EDITAR_PROPIA`, `DILIGENCIA_FINALIZAR`, `EVIDENCIA_AGREGAR`, `ACTA_GENERAR`, `ACTA_VER`, `ACTA_FIRMADA_SUBIR`, `ACLARACION_RESPONDER` |

Evidencia: `V11:36-70`, `V12:1-14`, `V13:63-76`, `V17:93-109`, `V18:57-73`.

### 4.3 Qué ve y puede hacer cada rol por defecto

| Rol | Inicio/menú y pantallas | Operaciones reales | Prohibiciones/ownership |
|---|---|---|---|
| ADMIN_SISTEMA | Redirige a `/admin`; menú Administración. No tiene módulos operativos por defecto. | Lista/crea/desactiva usuarios, asigna múltiples roles, lista roles/capacidades y reemplaza capacidades. | No puede solicitudes, expedientes, diligencias o revisiones salvo que se agreguen capacidades al rol. No hay protección de “último admin” ni de auto-desactivación. |
| MUNICIPIO | Inicio de solicitudes, Fiscalizaciones, Nuevo expediente, Revisiones; no Usuarios por defecto. | CRUD parcial de solicitudes, alta/consulta de expedientes, programación/reprogramación/cancelación, actas, revisión, aclaración, conclusión, seguimiento e informes. | No ejecuta diligencias propias por defecto. Listados de solicitudes/expedientes son globales; no existe ownership municipal por registro. |
| FISCALIZADOR | Topbar general se oculta; portal propio e historial. | Ve tareas vinculadas a su `Fiscalizador`, documentos propios, resultado legacy, diligencia/evidencia/acta propia y respuesta a aclaraciones propias. | Servicios validan que la programación/diligencia/aclaración corresponda a su fiscalizador (`DiligenciaFiscalizacionService.java:47-57,84-111,249-254`; `RevisionFiscalizacionService.java:92-97,127`). No puede gestión municipal. |

### 4.4 Matriz ROL × MÓDULO × OPERACIÓN real por defecto

Leyenda: **S** autorizado; **P** limitado a registros propios; **—** no autorizado. La matriz refleja seeds, no roles modificados posteriormente.

| Módulo / operación | ADMIN | MUNICIPIO | FISCALIZADOR | Capacidad/enforcement |
|---|:---:|:---:|:---:|---|
| Usuarios: ver/crear/roles/estado | S | — | — | `USUARIOS_*`; roles acepta `USUARIOS_EDITAR OR ROLES_GESTIONAR` |
| Roles: ver / cambiar capacidades | S | — | — | `ROLES_VER`; `ROLES_GESTIONAR`; catálogo caps `CAPACIDADES_GESTIONAR` |
| Solicitudes: ver/crear/editar/estado | — | S | — | capacidades homónimas; editar además `FISCALIZADOR_ASIGNAR` |
| Solicitudes: eliminar | — | S | — | `SOLICITUD_EDITAR`; solo API |
| Predio: crear | — | S | — | `SOLICITUD_CREAR` |
| RENIEC | — | S | — | `SOLICITUD_VER` |
| Expediente: ver/crear/editar | — | S | — | `EXPEDIENTE_*` |
| Programación: crear/reprogramar/cancelar | — | S | — | crear/reprogramar + `FISCALIZADOR_ASIGNAR`; cancelar propia cap |
| Tareas/ruta/documentos | — | — | P | `TAREAS_PROPIAS_VER`, `RUTA_VER`, `DOCUMENTO_TAREA_VER` + ownership |
| Resultado legacy | — | — | P | `FISCALIZACION_EJECUTAR` + `RESULTADO_REGISTRAR` + ownership |
| Diligencia iniciar/editar/finalizar | — | — | P | capacidades específicas; finalizar además `ACTA_GENERAR` |
| Evidencia/acta propia/copia firmada | — | — | P | evidencia/copia con ownership; `ACTA_VER` se restringe por presencia de `TAREAS_PROPIAS_VER` |
| Acta global | — | S | — | `ACTA_VER` sin `TAREAS_PROPIAS_VER` |
| Revisión/ver/iniciar/aclarar/concluir | — | S | — | caps de revisión; concluir además `EXPEDIENTE_CERRAR` |
| Responder aclaración | — | — | P | `ACLARACION_RESPONDER` + ownership |
| Seguimiento/informe | — | S | — | `SEGUIMIENTO_CREAR`, `INFORME_CONCLUSION_VER` |

### 4.5 Inconsistencias de acceso y escalamiento

1. **Roles configurables sin límites de dominio.** Un ADMIN puede otorgar capacidades operativas a cualquier rol, incluido ADMIN; es comportamiento intencional del modelo, pero no existe regla de separación de funciones ni aprobación dual.
2. **Administración sensible sin salvaguardas.** No se impide desactivar la propia cuenta, desactivar al último administrador o retirar todas las capacidades administrativas (`UsuarioService.java:41-49`; `RoleCapabilityService.java:18-22`). Riesgo de bloqueo administrativo, no de bypass técnico.
3. **Roles múltiples y ACTA_VER.** `ActaFiscalizacionController` decide `ownOnly` por tener `TAREAS_PROPIAS_VER`, no por rol. Un usuario híbrido MUNICIPIO+FISCALIZADOR obtiene solo sus actas aunque también tenga permiso municipal global; un rol custom con `ACTA_VER` pero sin tareas obtiene todas (`ActaFiscalizacionController.java:19-30`; `DiligenciaFiscalizacionService.java:187-219`).
4. **Revisor sin ownership.** Guardar `revisor_id` no reserva la revisión. Las capacidades permiten que otro municipal solicite/concluya.
5. **Frontend desactualizado.** La seguridad real sigue en backend, pero el usuario puede ver acciones retiradas y recibir 403, o no ver nuevas acciones hasta reloguear.
6. **Anotaciones de AdminController.** La clase exige `ROLES_VER`, pero los métodos tienen `CAPACIDADES_GESTIONAR` o `ROLES_GESTIONAR`. En Spring Method Security la anotación de método reemplaza la de clase; por tanto esos endpoints no necesariamente exigen ambas. Esto debe considerarse política efectiva, no suma implícita (`AdminController.java:9-15`).

## 5. Inventario de pantallas, formularios y botones

| Pantalla | Controles y métodos | Servicios/endpoints | Validación, errores y acciones problemáticas |
|---|---|---|---|
| Login | usuario, contraseña, mostrar/ocultar, recordar, `iniciarSesion()` | login | Error visible. Checkbox recordar sin efecto real. |
| Inicio | “Registrar solicitud”, “Consultar solicitudes” | navegación | Registrar visible sin cap específica. |
| Nueva solicitud | DNI con autocompleta, nombres/apellidos, teléfono, domicilio/referencia, PDF, mapa, fecha/hora/fiscalizador, observaciones; guardar/cancelar/quitar archivo | RENIEC, catálogo, solicitudes | Cliente exige datos centrales y PDF; backend valida DNI/coords/PDF. No exige fiscalizador backend. Quitar archivo no borra documento existente. |
| Solicitudes | búsqueda/estado, tarjetas, detalle, documento, mapa, editar, estado, reporte, nueva | solicitudes/documentos/reportes | Acciones mutables visibles solo por estado, no por capacidad; `cambiarEstado()` carece de manejo local robusto del error. DELETE desconectado. |
| Mapa | buscar, resultado, clic, confirmar, cancelar | Nominatim/Leaflet/localStorage | Búsqueda externa sin proxy. El destino depende de `retorno`; Predial no lo define. |
| Predial | dirección, referencia, tipo, código, ubicación, registrar | predios | Campos requeridos; manejo de error. Coordenadas permanecen nulas por integración rota. |
| Expedientes | actualizar, revisión, nuevo, 6 filtros, tarjetas | expedientes | Filtros cliente. Botón nuevo sin cap. |
| Nuevo expediente | origen/detalle/objeto, solicitud, DNI/RENIEC, administrado, teléfono/dirección, Google Maps/mapa, PDF, check programación, tipo/fecha/hora/fiscalizador | 6 endpoints | Validación cliente y backend, pero operación secuencial parcial. Texto dice que puede programarse después, pero no hay UI para ello. |
| Detalle expediente | PDFs, ubicación, reprogramar/cancelar, ciclos/revisar, actas, historial; modal fecha/hora/tipo/fiscalizador/motivo | expedientes/programaciones/actas/revisiones | Botones no miran capacidades. El método de guardar solo valida motivo explícitamente; backend completa validación. Carga todo-o-error. |
| Bandeja revisión | actualizar, 5 filtros, revisar/continuar/ver | revisiones | Botón siempre intenta iniciar incluso cuando el usuario solo tiene ver. Error genérico. |
| Detalle revisión | abrir evidencia/acta/copia, aclarar/cancelar, 6 conclusiones y campos condicionales, informe, seguimiento | revisiones, actas, catálogo | Buenas validaciones condicionales cliente/backend. No segmenta botones por capacidades. `programarSeguimiento()` confía en disabled de template y backend. |
| Portal fiscalizador | panel/historial, GPS, calcular ruta, abrir Maps, detalle, PDF, resultado/observaciones/reporte, aclaración/respuesta | tareas legacy/nativas y revisiones | Promesas acopladas. “Iniciar” en vencida contradice backend. Coordenadas ausentes ocultan tareas nativas. Ruta es heurística cliente, no optimizador servidor. |
| Diligencia | iniciar GPS, persona, autosave, participantes, evidencias, sustento, finalizar/acta, no realizada, copia firmada | diligencia/acta | Ownership fuerte. Bug enum ausencia. No hay confirmación antes de acciones terminales. Copia firmada no se puede sobrescribir por backend. |
| Usuarios | formulario y refrescar | usuarios | Solo roles enum, validación cliente mínima; backend exige 8 caracteres y coincidencia. Duplicado parcial. |
| Admin | tabs, alta, refrescar, multiselect roles, estado, selector de rol, checkboxes auto-save | usuarios/admin | Cada checkbox persiste inmediatamente, sin confirmación. Falta gating por operación; errores parciales al cargar. |
| Perfil | continuar | navegación | Sin refresco de sesión; texto histórico engañoso. |

No se encontró un botón literalmente sin handler, pero sí controles cuyo resultado no cumple su etiqueta: recordar sesión, quitar PDF persistido, mapa de Predial y “programar más adelante”.

## 6. Flujos operativos actuales

### 6.1 Inicio y cierre de sesión

1. Usuario envía credenciales; backend busca cuenta activa y verifica BCrypt (`AuthService.java:8-18`).
2. Puede validar un rol solicitado, aunque Angular no lo envía. JWT contiene sujeto y `fiscalizadorId`; capacidades vienen en respuesta, no son la fuente de autorización posterior (`JwtService.java:18-23`).
3. Angular guarda toda la sesión en `localStorage` y redirige según prioridad de capacidades.
4. Cada request añade Bearer; 401 limpia sesión, 403 no (`auth.interceptor.ts`).
5. Cerrar sesión elimina almacenamiento local; no hay revocación server-side.

### 6.2 Usuarios, roles y capacidades

1. ADMIN lista usuarios/roles/capacidades.
2. Crea usuario con un rol inicial de los tres enum. Contraseña y confirmación se validan; no se crea ni enlaza automáticamente un registro `Fiscalizador`.
3. Puede reemplazar la colección completa de roles, pero no dejarla vacía; el backend impide asignar ADMIN a quien no tenga `ROLES_GESTIONAR`.
4. Puede activar/desactivar cualquier usuario.
5. Puede reemplazar capacidades de un rol configurable. No crea/elimina roles ni capacidades.
6. Permisos backend cambian en la siguiente petición; interfaz en el siguiente login.

### 6.3 Solicitud legacy

1. MUNICIPIO ingresa DNI; RENIEC rellena nombres o permite ingreso manual ante 404/503.
2. Completa contacto/dirección, PDF, ubicación, fecha/hora y opcional fiscalizador.
3. Backend valida DTO y PDF; almacena archivo, crea `Solicitud` e historial.
4. `ExpedienteFiscalizacionService.sincronizarSolicitud` crea/actualiza expediente y programación compatibles.
5. MUNICIPIO consulta, edita o cambia estado. No hay eliminación UI.
6. Fiscalizador propietario ve tarea legacy y registra `REALIZADA` (reporte PDF obligatorio en UI/servicio) o `NO_REALIZADA` (observación); se sincroniza con expediente/programación.

### 6.4 Expediente y programación

1. MUNICIPIO elige uno de siete orígenes; opcionalmente vincula solicitud no usada y copia datos.
2. Registra objeto, administrado, ubicación y documento opcional.
3. `POST /expedientes` crea `ABIERTO` y evento.
4. Si hay PDF, un segundo POST lo adjunta. Si pidió visita, un tercer POST crea `PROGRAMADA`, valida futuro/disponibilidad/fiscalizador activo y replica al legacy si aplica (`ExpedienteFiscalizacionService.java:98-110`).
5. Reprogramar solo admite `PROGRAMADA` o `VENCIDA`: marca anterior `REPROGRAMADA`, crea nueva fila y conserva enlace/motivo (`:113-130`).
6. Cancelar admite los mismos estados, conserva fila y motivo; limpia programación legacy (`:133-142`).
7. Las consultas pueden mutar estado `PROGRAMADA → VENCIDA` y registrar evento (`:173-179`); por ello incluso GET no es puramente de lectura funcional.

### 6.5 Diligencia, evidencias y acta

1. Fiscalizador consulta solo asignaciones propias. El usuario debe estar vinculado a `Fiscalizador`.
2. Iniciar exige programación propia `PROGRAMADA` y no vencida. Registra hora/GPS (o estado no disponible), cambia programación a `EN_CURSO` y expediente `ABIERTO → EN_FISCALIZACION` (`DiligenciaFiscalizacionService.java:55-80`).
3. Autosave cambia diligencia a `EN_DESARROLLO`; se registran persona, hechos y observaciones. Participantes y evidencia se agregan con ownership.
4. Puede finalizar: exige hechos, situación de persona, identidad completa cuando corresponde, constancias de negativa y medio de entrega coherente (`:151-169`).
5. Finalizar deja diligencia `FINALIZADA`, visita `REALIZADA`, expediente `PENDIENTE_REVISION`, genera PDF/JSON de acta y revisión `PENDIENTE` (`:170-184`).
6. Alternativamente registra `NO_REALIZADA` desde `PROGRAMADA` o `EN_CURSO`; cierra la visita y devuelve el expediente a `ABIERTO`, sin acta/revisión (`:122-148`).
7. La copia firmada se agrega una sola vez, separada del PDF generado (`:204-210`).

### 6.6 Revisión, aclaración, resultado municipal y seguimiento

1. MUNICIPIO ve únicamente diligencias `FINALIZADA` con acta; las `NO_REALIZADA` no entran a revisión (`RevisionFiscalizacionService.java:42-63`). Aunque la bandeja ofrece ese resultado como filtro, el backend marca todas las filas como `REALIZADA`.
2. Iniciar crea o toma revisión `PENDIENTE`, asigna revisor y pasa a `EN_REVISION`; tras una respuesta puede reanudar desde `REQUIERE_ACLARACION` (`RevisionFiscalizacionService.java:55-70`).
3. Puede solicitar aclaración: crea `PENDIENTE`, revisión `REQUIERE_ACLARACION`.
4. Solo el fiscalizador responsable puede listar/responder; la respuesta no modifica acta/hechos y queda `RESPONDIDA` (`:92-97,127`).
5. MUNICIPIO reanuda y concluye. Hay seis conclusiones, fundamento obligatorio y reglas condicionales. No se crea sanción/multa; la derivación es una bandera/evento.
6. Sin seguimiento y sin programación activa, cierra expediente. Con seguimiento, queda `EN_SEGUIMIENTO` y genera informe PDF/checksum (`:100-114,145-147`).
7. Seguimiento crea una programación única ligada a la revisión y conserva el mismo expediente (`:117-123`). Un nuevo ciclo puede volver a diligencia/revisión y cerrar.

### 6.7 Documentos, mapas, RENIEC y Predios

- Solicitudes y expedientes almacenan metadata en BD y archivo en filesystem; actas, evidencias e informes usan almacenamiento específico. La persistencia se comprueba por código, no por archivos actuales.
- PDF de solicitud/expediente, reporte legacy, acta, copia firmada, evidencias e informe tienen descargas autenticadas.
- Mapa interno guarda coordenadas temporalmente en navegador; Google Maps solo abre enlaces externos. GPS de diligencia se persiste con estado disponible/no autorizado/no disponible.
- RENIEC está abstraído por proveedor; 404 permite entrada manual y falta de configuración devuelve 503 según documentación/código.
- Predio solo crea una fila independiente. No participa en los demás flujos.

## 7. Modelo de estados

### 7.1 Solicitud legacy

Enum: `EN_ESPERA`, `VERIFICADO`, `OBSERVADA`, `EXPIRADO`, mostrados como “En espera”, “Verificado”, “Observada” y “Expirado” (`EstadoSolicitud.java:3-16`; `solicitud.model.ts:3-7`). La API permite cambiar directamente entre los tres primeros y rechaza asignar manualmente `EXPIRADO`; este último se conserva para registros históricos (`SolicitudService.java:38-49`). La UI lo presenta deshabilitado. Este estado no equivale al estado de expediente ni de programación.

### 7.2 Expediente

`ABIERTO → EN_FISCALIZACION → PENDIENTE_REVISION → CERRADO` o `EN_SEGUIMIENTO`; `NO_REALIZADA` devuelve a `ABIERTO`. `CANCELADO` existe, pero no se encontró operación actual que cancele el expediente completo. Desde `EN_SEGUIMIENTO`, una nueva diligencia vuelve al recorrido de campo. Servicios bloquean editar/programar/cancelar sobre `CERRADO` o `CANCELADO` (`ExpedienteFiscalizacionService.java:83-95,98-142`; método `asegurarNoCerrado`).

### 7.3 Programación

- `PROGRAMADA → EN_CURSO → REALIZADA`.
- `PROGRAMADA|EN_CURSO → NO_REALIZADA`.
- `PROGRAMADA → VENCIDA` automáticamente al consultar/iniciar después de hora.
- `PROGRAMADA|VENCIDA → REPROGRAMADA` + nueva `PROGRAMADA`.
- `PROGRAMADA|VENCIDA → CANCELADA`.
- Iniciar desde VENCIDA/CANCELADA/REPROGRAMADA/terminada se rechaza.

### 7.4 Diligencia y acta

`INICIADA → EN_DESARROLLO → FINALIZADA`; también `INICIADA|EN_DESARROLLO → NO_REALIZADA`, y creación directa `NO_REALIZADA` desde visita programada. Solo estados editables aceptan guardar/evidencia/participantes. Acta no tiene enum de ciclo; se crea al finalizar y conserva estado de firma (`FIRMA_PENDIENTE`, `FIRMADA_MANUALMENTE`, negativas). La copia firmada es un adjunto único.

### 7.5 Revisión y aclaración

- Revisión: `PENDIENTE → EN_REVISION → REQUIERE_ACLARACION → EN_REVISION → FINALIZADA`.
- Aclaración: `PENDIENTE → RESPONDIDA`; `CERRADA` existe en enum pero no se halló transición que la use. Al concluir, aclaraciones respondidas no pasan a CERRADA.
- Una revisión finalizada no se reabre. Concluir se bloquea con aclaración pendiente, otra revisión pendiente o programación activa cuando intenta cerrar.

### 7.6 Sincronizaciones artificiales/duplicadas

- Solicitud mantiene `estado`, fecha/hora/fiscalizador y resultado; Expediente/Programación mantienen estados equivalentes más ricos. Servicios copian entre ambos (`ExpedienteFiscalizacionService.java:181-229`). Es compatibilidad deliberada, pero incrementa riesgo de divergencia.
- `PROGRAMACION_GESTIONAR` quedó en seeds legacy, mientras operaciones nuevas usan capacidades específicas; no se halló `@PreAuthorize` que la consuma.
- `CANCELADO` de expediente y `CERRADA` de aclaración están modelados sin flujo visible/servicio actual.

## 8. Inconsistencias y funciones incompletas

| Prioridad | Categoría | Hallazgo comprobable | Evidencia/impacto |
|---|---|---|---|
| Alta | CORREGIR | `AUSENTE` frontend ≠ `ADMINISTRADO_AUSENTE` backend | Impide guardar/finalizar ese caso; `diligencia.html:24`, `SituacionPersona.java:3`. |
| Alta | CORREGIR | UI permite iniciar visita vencida; backend la rechaza | 409 evitable; portal vs `DiligenciaFiscalizacionService.java:58-64`. |
| Alta | REVISAR | Revisión no está reservada al `revisor_id` | Otro usuario con capacidades puede aclarar/concluir; `RevisionFiscalizacionService.java:66,86-114`. |
| Alta | COMPLETAR | Alta de expediente no atómica y dependiente de caps no declaradas en ruta | Puede dejar expediente sin documento/programación. |
| Media | CORREGIR | Detalle de expediente exige de hecho `EXPEDIENTE_VER` + `ACTA_VER` + `REVISION_VER` | `Promise.all` convierte 403 parcial en fallo global. |
| Media | CORREGIR | Admin exige de hecho múltiples capacidades, pero ruta/controles solo `ROLES_VER` | Roles personalizados generan 403 evitables. |
| Media | CORREGIR | Revisión y expediente muestran acciones sin comprobar caps de mutación | 403 evitables; seguridad backend se mantiene. |
| Media | COMPLETAR | No se puede programar después desde UI aunque el alta lo promete | Endpoint existe, interfaz falta. |
| Media | COMPLETAR | No hay edición/adjunto posterior de expediente | Endpoints existen, interfaz falta. |
| Media | CORREGIR | Mapa de Predio no retorna al formulario ni rellena lat/lon | `predial.html/ts`; mapa usa retorno por defecto de solicitud. |
| Media | CORREGIR | Quitar PDF en edición de solicitud no elimina el persistido | Solo borra selección local; backend conserva documento sin nuevo archivo. |
| Media | REVISAR | Crear solicitud puede asignar fiscalizador sin `FISCALIZADOR_ASIGNAR`, editar sí la exige | Política asimétrica en `SolicitudController.java:9-10`. |
| Media | REVISAR | Expedientes asignados sin coordenadas desaparecen de tareas activas Angular | El backend los devuelve; filtro cliente los excluye. |
| Media | CORREGIR | La bandeja de revisión ofrece filtro `NO_REALIZADA`, pero nunca recibe esas diligencias | `revision-bandeja.html:34-35`; `RevisionFiscalizacionService.java:45-52`. |
| Media | REVISAR | GET/listados vencen programaciones y escriben historial | Efecto lateral documentado en servicios; relevante para auditoría y concurrencia. |
| Baja | EVALUAR ELIMINACIÓN | `/usuarios` duplica parte de `/admin` | Dos UIs con capacidades y validaciones distintas. |
| Baja | EVALUAR ELIMINACIÓN | Predio aislado | Solo POST, sin consumidores. |
| Baja | MANTENER/LEGACY | Alias `/nueva-cita` | Redirección compatible, no módulo distinto. |
| Baja | CORREGIR | Texto de Perfil describe prototipo local | Contradice backend/persistencia real. |
| Baja | REVISAR | Coordenadas/documentación mezclan San Miguel–San Román/Puno y textos “Lima” | `verificacion.service.ts:7-8`; contenido de perfil/datos históricos. Requiere decisión territorial. |

## 9. Funciones duplicadas, legacy u ocultas

- **Solicitudes** es legacy pero activo: no debe eliminarse sin decidir migración, porque aún tiene UI, documentos, resultado y sincronización.
- **Expedientes** es el modelo estructurado y recibe migración/sincronización desde Solicitudes. Es el núcleo del flujo nuevo.
- **Usuarios simple** y **Admin/Usuarios** se solapan. El primero solo crea rol único; el segundo administra roles múltiples/estado.
- **NuevaCitaComponent** conserva nombre/carpeta legacy, pero sirve `/nueva-solicitud`; rutas antiguas redirigen.
- **Programación legacy** en Solicitud convive con `ProgramacionFiscalizacion`; hay copia bidireccional parcial.
- **Resultado legacy con reporte** y **diligencia estructurada con acta/evidencias** conviven en el portal y se fusionan/de-duplican en cliente.
- **Predio** es módulo aislado, no una versión alternativa de Expediente.
- Capacidades `PROGRAMACION_GESTIONAR`, estado de expediente `CANCELADO` y aclaración `CERRADA` no tienen uso funcional completo encontrado.

## 10. Clasificación para toma de decisiones

### MANTENER

- Autorización backend por capacidades recalculadas por petición.
- Ownership de tareas, diligencias, evidencia, documento y aclaración del fiscalizador.
- Conservación de historial al reprogramar/cancelar.
- Diligencia estructurada, snapshot/acta, revisión, seguimiento y múltiples ciclos.
- Conclusión sin creación automática de multa/sanción y con informe/checksum.
- Distinción entre evidencia de código, migraciones y documentación histórica.

### REVISAR

- Si una revisión debe pertenecer exclusivamente al revisor que la inició o ser colaborativa.
- Política para usuarios multirrol y alcance global/propio de actas.
- Si crear solicitud debe requerir `FISCALIZADOR_ASIGNAR` al seleccionar fiscalizador.
- Convivencia temporal o permanente de Solicitudes/Expedientes y fecha de retiro del legacy.
- Si consultar puede vencer y persistir estados o debe hacerlo un proceso explícito.
- Jurisdicción geográfica oficial y textos institucionales.
- Si roles deben seguir limitados a tres nombres o si se requieren roles personalizados reales.

### CORREGIR

- Enum de persona ausente.
- Inicio ofrecido para visitas vencidas.
- Guards/visibilidad frente a capacidades efectivas por acción.
- Cargas todo-o-nada de Admin, Detalle expediente y Portal fiscalizador.
- Retorno del mapa de Predio y semántica de quitar PDF.
- Texto funcional obsoleto del Perfil.

### COMPLETAR

- Programar expediente existente, editarlo y adjuntar/reemplazar sustento desde UI.
- Manejo transaccional/compensación o comunicación explícita del alta en pasos.
- Administración segura de primer/último admin, autorrol y desactivación propia.
- Pruebas actuales de los commits posteriores al informe del 30/09 en entorno aislado.

### EVALUAR ELIMINACIÓN

- Pantalla `/usuarios` si `/admin` es la administración canónica.
- Módulo Predio si no se define vínculo funcional.
- Capacidades/estados sin consumidores después de confirmar compatibilidad.
- Alias/carpeta `nueva-cita` solo cuando ya no sea necesario conservar enlaces.

### NO VERIFICADO

- Estado actual de MySQL, filas, versión Flyway aplicada y coherencia con filesystem.
- Ejecución actual de backend/frontend, navegación real y llamadas externas.
- Resultado actual de pruebas y build en HEAD `109cf96`.
- Disponibilidad/configuración real de RENIEC, Nominatim, OSM y Google Maps.
- Integridad física actual de PDFs/evidencias.

## 11. Preguntas que requieren decisiones del usuario

1. ¿La revisión municipal debe quedar bloqueada al revisor que la inicia, permitir transferencia explícita o ser colaborativa para cualquier usuario con capacidad?
2. ¿Solicitudes seguirá siendo un canal operativo paralelo o debe quedar solo como origen legacy de Expedientes? ¿Cuál es el criterio de corte?
3. ¿Debe poder crearse un expediente sin programación? Si sí, ¿quién y desde qué pantalla crea la primera programación después?
4. ¿La asignación de fiscalizador al crear una Solicitud debe exigir `FISCALIZADOR_ASIGNAR`, igual que la edición y el expediente?
5. ¿Los roles personalizados serán nombres nuevos o únicamente combinaciones de los tres roles sembrados? La API actual no soporta nombres nuevos aunque la tabla sí podría.
6. ¿Qué debe ocurrir al desactivar al usuario actual o al último ADMIN con capacidades administrativas?
7. Para usuarios multirrol MUNICIPIO+FISCALIZADOR, ¿ACTA_VER debe mostrar todas las actas o solo las propias?
8. ¿Predios debe vincularse a Solicitud/Expediente, convertirse en catálogo consultable o retirarse?
9. ¿La jurisdicción oficial es San Miguel, provincia de San Román, Puno, y deben eliminarse todas las referencias a Lima?
10. ¿Una visita vencida se reprograma obligatoriamente o existe una tolerancia de inicio tardío que deba modelarse?
11. ¿La copia firmada debe seguir siendo inmutable o se requiere un procedimiento auditado de reemplazo?
12. ¿Se autoriza en una fase posterior ejecutar builds y suites en una base de prueba aislada para convertir los puntos NO VERIFICADOS en evidencia actual?

## Apéndice A. Comandos reales identificados (no ejecutados)

- Frontend: `npm start`, `npm run build`, `npm test -- --watch=false --no-progress` (`package.json:4-10`; `README.md:22-27,65-73`).
- Backend: `cd backend && mvn spring-boot:run`; pruebas `mvn test`; empaquetado `mvn clean package` (`README.md:12-20,65-73`).
- No existe `mvnw` ni en raíz ni en `backend`; Maven del sistema o Docker es necesario.
- Smoke test: `./scripts/smoke-test.sh`; **muta datos temporalmente** y no fue ejecutado (`README.md:72,85`).

## Apéndice B. Documentación histórica examinada

- `README.md`: instrucciones y arquitectura vigente en buena parte, pero expone credenciales demo de desarrollo y no constituye evidencia de ejecución actual.
- `AUDITORIA_AUTORIZACION_FASE1.md`: historia previa a V11/V12; útil para intención, no para estado AS-IS por sí sola.
- `FASE2A_EXPEDIENTE_FISCALIZACION.md`: explica la transición Solicitud→Expediente; varias limitaciones de UI allí descritas fueron superadas después.
- `docs/FASE_3B_RESULTADO.md`: evidencia histórica detallada de pruebas e integración del 30/09; anterior a los commits visuales actuales.

No se asumió como implementado ningún requisito procedente de otro proyecto ni se interpretó documentación histórica por encima del código actual.
