# Fase 2A: expediente y programación

## Modelo

El modelo anterior concentraba datos del administrado, solicitud, programación, resultado y reporte en `Solicitud`. La nueva estructura agrega `ExpedienteFiscalizacion`, `ProgramacionFiscalizacion` y `ExpedienteHistorial`. La solicitud permanece como origen vinculado opcional; documentos y PDFs permanecen en sus ubicaciones y columnas actuales.

La migración V13 crea un expediente por cada solicitud existente, con origen `SOLICITUD`, estado `ABIERTO` y relación única nullable. Conserva los datos de administrado como referencia del registro heredado. Crea programación cuando hay fecha/hora o un resultado, mapeando REALIZADA, NO_REALIZADA y EXPIRADO a sus estados de visita. Si el resultado está pendiente, asigna VENCIDA si pasó el horario Lima, o PROGRAMADA si todavía es futuro. Los eventos históricos migrados se identifican como `MIGRACION_V13`; no se inventa el actor original.

## Estados

- Expediente: `ABIERTO`, `EN_FISCALIZACION`, `PENDIENTE_REVISION`, `CERRADO`, `CANCELADO`. La lógica de esta fase crea expedientes en `ABIERTO`; no implementa revisión ni cierre.
- Programación/visita: `PROGRAMADA`, `EN_CURSO`, `REALIZADA`, `NO_REALIZADA`, `VENCIDA`, `CANCELADA`, `REPROGRAMADA`.
- Tipo de visita: `PROGRAMADA`, `INOPINADA`. La visita inopinada admite fecha/hora internas, sin requisito de notificación.

## API y autorización

- `GET /api/expedientes`, `GET /api/expedientes/{codigo}`: `EXPEDIENTE_VER`.
- `POST /api/expedientes`: `EXPEDIENTE_CREAR`.
- `PUT /api/expedientes/{codigo}`: `EXPEDIENTE_EDITAR`.
- `POST /api/expedientes/{codigo}/programaciones`: `PROGRAMACION_CREAR` + `FISCALIZADOR_ASIGNAR`.
- `POST /api/programaciones/{id}/reprogramar`: `PROGRAMACION_REPROGRAMAR` + `FISCALIZADOR_ASIGNAR`.
- `POST /api/programaciones/{id}/cancelar`: `PROGRAMACION_CANCELAR`.
- `GET /api/programaciones/mis-asignaciones`: capacidad operativa `TAREAS_PROPIAS_VER`, con filtro por fiscalizador autenticado.

Las nuevas capacidades de gestión de expediente/programación se asignan a MUNICIPIO; ADMIN_SISTEMA conserva ninguna capacidad operativa y FISCALIZADOR solo consulta sus asignaciones propias. Cada mutación registra actor, fecha/hora, evento y valores relevantes. La reprogramación marca la visita antigua como `REPROGRAMADA`, conserva su fila y crea otra `PROGRAMADA` enlazada. La cancelación también conserva la fila y motivo.

Al consultar expedientes o asignaciones, se marca `VENCIDA` una programación `PROGRAMADA` pasada según `America/Lima`, registrando el evento. El expediente permanece `ABIERTO`.

## Compatibilidad y límites

Los endpoints `/api/solicitudes` siguen operativos. Crear/editar solicitudes, asignar su fecha y fiscalizador y registrar resultados sincroniza el expediente/programación vinculado. La programación nueva también actualiza los campos heredados que consumen temporalmente el portal fiscalizador y la agenda previa. La columna `solicitud.estado` y el resultado antiguo se conservan por compatibilidad, pero ya no se les cambia automáticamente el estado a EXPIRADO.

La pantalla `/expedientes` permite listar y consultar origen, estado, programaciones e historial. La creación, edición, reprogramación y cancelación están disponibles por API, aún sin formularios nuevos de UI. No se implementan actas, evidencia, revisión municipal, conformidad, sanción ni módulos posteriores.

V13 es aditiva; no elimina solicitudes, usuarios, historial ni archivos. Al validarla en MySQL, V14 corrigió la precisión temporal de los eventos migrados: `solicitud.fecha_registro` solo contiene una fecha y no demuestra que el evento ocurrió a medianoche, así que los eventos señalan la instalación de V13 registrada por Flyway. V15 convierte esa marca UTC a America/Lima, que es la zona usada por el modelo y la interfaz. V13 y V14 permanecen intactas y sus checksums no se modifican.
