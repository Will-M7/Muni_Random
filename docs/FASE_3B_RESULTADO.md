# Resultado de validación — Fase 3B

Fecha: 2026-09-30
Entorno: MySQL 8.4.11, Java 21.0.12.1, Spring Boot 3.5.9, Angular CLI (Vite test runner).

## Resumen

Se continuó desde el estado persistido. La base contiene V18 aplicada y validada, pero no contiene expediente ni otra entidad con la marca `VALIDACION_REVISION_FASE3B` o `FASE3B`. Por ello no se recrearon ni alteraron datos operativos y no se pudo retomar el recorrido manual de revisión: no existía expediente candidato persistido. No se modificaron migraciones V1–V18.

## Migraciones y esquema

- `flyway_schema_history`: V18 (`municipal review and followup`) figura exitosa.
- El arranque temporal ejecutó `Successfully validated 18 migrations`; V18 era la versión actual y Flyway no aplicó migraciones.
- Hibernate inicializó el `EntityManagerFactory` con `ddl-auto: validate`; no hubo error de validación del esquema.
- La API alcanzó estado `Started` en el puerto 18137 y se detuvo al concluir las comprobaciones.
- La advertencia de Flyway indica que MySQL 8.4 es más reciente que la versión probada oficialmente por Flyway (hasta 8.1); en este entorno la validación de las 18 migraciones sí terminó correctamente.

## Entidades y flujo funcional

V18 agrega `revision_fiscalizacion`, `solicitud_aclaracion_fiscalizacion` y las referencias de programación de seguimiento. El código implementa la bandeja de diligencias finalizadas, creación/reanudación de revisión, consulta de ciclos por expediente, aclaración, respuesta limitada al fiscalizador responsable, conclusión, programación de seguimiento e informe PDF.

El recorrido manual solicitado (MUNICIPIO inicia revisión, consulta acta/evidencias y solicita aclaración; fiscalizador propietario responde; MUNICIPIO retoma, concluye y programa seguimiento) **no se ejecutó de extremo a extremo** porque la búsqueda de marca no encontró diligencia, acta o expediente de validación. No se insertó un expediente sustituto en la base compartida. Esta es la limitación funcional principal de esta reanudación.

## Conclusiones y derivación

El enum contiene exactamente `CONFORMIDAD`, `RECOMENDACION_MEJORAS_CORRECCIONES`, `ADVERTENCIA`, `RECOMIENDA_INICIO_PROCEDIMIENTO`, `MEDIDA_CORRECTIVA` y `OTRA`. La lógica exige fundamento, descripción para las conclusiones distintas de conformidad/recomendación de inicio y denominación para `OTRA`; exige derivación para `RECOMIENDA_INICIO_PROCEDIMIENTO` y prohíbe seguimiento con `CONFORMIDAD`.

La derivación se registra como bandera y evento `DERIVACION_RECOMENDADA`. El informe declara que la recomendación no determina responsabilidad ni impone sanción. En el código revisado, concluir no crea multa, sanción ni procedimiento sancionador automático. Sin un recorrido integrado no se verificó un PDF de conclusión descargado ni se ejecutaron las seis variantes contra persistencia; las pruebas del paquete tampoco contienen casos específicos para esas seis conclusiones. Queda pendiente añadir/verificar pruebas de servicio por variante y del contenido binario del informe.

## Seguimiento y cierre

La ruta implementada establece `EN_SEGUIMIENTO` cuando la revisión final requiere seguimiento. La programación resultante se asocia al mismo expediente y a la revisión origen; se conservan diligencias y revisiones anteriores en los ciclos del detalle.

El cierre normal se rechaza si hay aclaración pendiente, otra revisión pendiente o programación activa; una conclusión sin seguimiento cierra el expediente. La edición del expediente y operaciones de programación (crear, reprogramar, cancelar) invocan la protección común para `CERRADO` y `CANCELADO`. El seguimiento exige estado `EN_SEGUIMIENTO`, revisión finalizada y evita duplicar programación por revisión. No fue posible ejercitar estas transiciones con filas integradas de Fase 3B.

## Seguridad

- La configuración exige autenticación para `/api/**`; durante la comprobación temporal, revisión y administración sin token respondieron 401.
- `mvn test package` ejecutó las pruebas MockMvc de separación de roles: ADMIN_SISTEMA no puede revisar/concluir y FISCALIZADOR no puede concluir; los intentos válidos respondieron 403. También se cubrió que el fiscalizador con capacidad de respuesta sólo ve su ruta de aclaraciones, y la lógica de servicio comprueba que la diligencia sea propia.
- Los controladores limitan la revisión, conclusión, cierre y seguimiento a capacidades municipales. La respuesta de aclaraciones requiere `ACLARACION_RESPONDER`; no hay capacidad de concluir, cerrar o crear seguimiento para FISCALIZADOR. Las capacidades de operación de diligencias propias se mantienen separadas.
- No se verificó un recorrido con JWT real de un usuario MUNICIPIO/FISCALIZADOR porque no había expediente sintético que operar.

## Cuenta sintética y datos técnicos

- `admin3b` existe con rol `ADMIN_SISTEMA`; no se halló expediente creado por esa cuenta, revisión o aclaración en la que figure como actor, ni evento de historial con ese nombre.
- Se desactivó de forma segura (sin borrar cuentas ni datos): `app_user.activo=0`. Verificación final: `admin3b`, `ADMIN_SISTEMA`, inactiva. No se incluyen credenciales ni tokens.
- No se hallaron expedientes con `VALIDACION_REVISION_FASE3B` ni con la marca genérica `FASE3B` en código, detalle de origen, objeto u observaciones. No se tocaron expedientes legacy ni marcas V13, UI Fase 2B o Fase 3A.
- No se creó ningún expediente, revisión, aclaración, programación, diligencia o conclusión durante esta continuación.

## Informe PDF

La generación produce PDF con expediente, acta/diligencia, revisor, fecha, conclusión, fundamento, detalle, seguimiento y derivación recomendada; se conserva nombre interno y checksum SHA-256. El endpoint descarga el recurso PDF generado. La inclusión de texto que excluye sanción/responsabilidad está presente. La descarga y lectura de un informe persistido de Fase 3B no se pudo probar porque no hay revisión concluida de validación.

## Pruebas ejecutadas

- Backend con Java 21: `mvn test package` — **BUILD SUCCESS**, 52 pruebas, 0 fallas, 0 errores, 0 omitidas.
- Frontend: `npm test -- --watch=false` — **27 pruebas aprobadas** en 11 archivos.
- Frontend: `npm run build` — **BUILD SUCCESS**.
- Flyway y Hibernate: validados en el arranque real contra MySQL, V18 vigente y esquema compatible.
- HTTP sin autenticación en 18137: `/api/revisiones/pendientes` = 401; `/api/admin/capacidades` = 401.
- `git diff --check` — sin errores.
- El primer intento aislado del plugin `mvn flyway:validate` no pudo resolver su plugin porque el caché Maven estaba en una ruta de solo lectura. La validación Flyway efectiva se completó al iniciar Spring Boot contra la base.

## Correcciones realizadas en esta continuación

- Desactivación de la cuenta técnica `admin3b` tras comprobar que no tenía datos operativos asociados.
- No se hicieron cambios a migraciones ni se duplicaron datos.
- Se creó este informe de resultado.

## Limitaciones

No fue posible efectuar el flujo manual completo ni probar contra filas integradas las seis conclusiones, el PDF descargado, la programación del mismo expediente, el historial o los rechazos de cierre posteriores. Para cerrar esos puntos hace falta disponer de un fixture integrado Fase 3B o crear uno aislado y controlado en una base de pruebas, sin reutilizar producción/legacy.

## Validación integrada Fase 3B.1

Esta sección complementa el estado histórico anterior: las limitaciones descritas allí eran ciertas al momento de aquella validación. En esta continuación se creó y recorrió un fixture sintético de forma controlada. No se modificaron V1–V18, no se creó V19 y no se avanzó a otra fase.

### Fixture y recorrido HTTP/JWT

- Marca: `VALIDACION_REVISION_FASE3B_INTEGRADA`; expediente `FIS-2026-00011`, id técnico 11. Los datos del administrado, hechos, participante y evidencias son ficticios.
- El fixture se creó mediante el flujo de API y persistencia normal: expediente, primera programación, diligencia del fiscalizador propietario, participante ficticio, hechos sintéticos, evidencia y acta. El expediente pasó a `PENDIENTE_REVISION`.
- MUNICIPIO consultó la bandeja y acta/evidencias, inició la revisión y solicitó aclaración. Se verificaron estado `REQUIERE_ACLARACION`, solicitud `PENDIENTE`, mensaje, actor y fecha. El fiscalizador propietario consultó y respondió: quedó `RESPONDIDA` con actor y timestamp; el acta, los hechos y las evidencias originales conservaron sus valores/bytes. MUNICIPIO retomó la revisión.
- La primera conclusión fue `RECOMENDACION_MEJORAS_CORRECCIONES`, con fundamento, descripción y plazo sintéticos, `requiereSeguimiento=true`. La revisión quedó `FINALIZADA`; el expediente quedó `EN_SEGUIMIENTO` y se registraron sus eventos.
- La segunda programación se creó para el mismo expediente y con referencia a la revisión origen; la programación anterior permaneció. Se ejecutó la segunda diligencia con hechos/evidencia sintéticos y se generó su segunda acta. Después MUNICIPIO inició la segunda revisión y registró `CONFORMIDAD` con fundamento. La revisión quedó `FINALIZADA`, sin seguimiento ni aclaraciones pendientes ni programación activa, y el expediente quedó `CERRADO`.
- Estado final consultado en BD: **1 expediente, 2 programaciones, 2 diligencias, 2 actas, 2 revisiones, 1 aclaración y 2 evidencias**. Las dos programaciones figuran `REALIZADA`; las revisiones `FINALIZADA`. El fixture permanece como evidencia técnica, según lo solicitado.

### Conclusiones

Se añadieron pruebas de servicio para las seis variantes; 7 pruebas cubren sus reglas y escenarios de seguimiento:

- `CONFORMIDAD`: fundamento obligatorio, seguimiento prohibido y cierre cuando no hay pendientes.
- `RECOMENDACION_MEJORAS_CORRECCIONES`: permite cierre sin seguimiento y conserva `EN_SEGUIMIENTO` cuando se solicita seguimiento.
- `ADVERTENCIA`: exige fundamento y descripción; se probó con y sin seguimiento. No crea multa, sanción ni responsabilidad automática.
- `RECOMIENDA_INICIO_PROCEDIMIENTO`: exige fundamento y derivación; persiste el indicador y `DERIVACION_RECOMENDADA`. No crea multa, sanción, resolución, procedimiento, deuda ni responsabilidad automática. La consulta de tablas/eventos tampoco detectó módulos/filas sancionadoras generados.
- `MEDIDA_CORRECTIVA`: requiere descripción y fundamento; conserva literalmente la medida redactada por MUNICIPIO.
- `OTRA`: exige denominación y descripción, además del fundamento; se rechaza si falta la denominación.

El cierre integrado generó el informe de conclusión. Se comprobó archivo físico, metadata/nombre interno, SHA-256, descarga HTTP 200 con `application/pdf`, firma `%PDF-` y contenido de Municipalidad, código, acta, revisor, conclusión, fundamento, seguimiento y fecha. La lectura del PDF confirma la advertencia de que no determina responsabilidad ni impone sanción; no atribuye multa automática.

### Cierre, historial y seguridad

- Después de CERRADO, la API rechazó con HTTP 409 crear programación, reprogramar, cancelar, modificar expediente, iniciar revisión, cambiar conclusión e iniciar diligencia.
- El historial del fixture conserva actores y timestamps ordenables e incluye eventos de creación, programación, diligencias, actas, inicio/retoma de revisión, solicitud/respuesta de aclaración, conclusión, seguimiento, programación del seguimiento, generación de informe y cierre.
- Con JWT reales de prueba: MUNICIPIO realizó las operaciones autorizadas; FISCALIZADOR propietario ejecutó su diligencia y respondió su aclaración; otro fiscalizador recibió 403 al leer/editar diligencia ajena o responder aclaración ajena. FISCALIZADOR recibió 403 al concluir o crear seguimiento. ADMIN_SISTEMA recibió 403 al revisar/concluir y ejecutar diligencia. Las rutas protegidas sin autenticación responden 401. Las peticiones de autorización utilizaron payloads válidos, por lo que los rechazos observados fueron 401/403 y no errores de validación 400.
- Las cuentas temporales con nombres `f3b_*`, así como las cuentas sintéticas usadas para la primera revisión, quedaron inactivas. La cuenta preexistente `admin3b` permaneció inactiva; no fue reactivada ni se borraron cuentas reales. No se registran credenciales ni tokens aquí.

### UI

Con sesión MUNICIPIO autenticada se cargaron en navegador la bandeja Pendientes de revisión, el detalle con acta/evidencias y la cronología del expediente en 1366×900 y 420×860. En las tres rutas no hubo overflow horizontal. El fixture se mantuvo cerrado y no se reabrió ni se mutó otro fixture para recrear formularios activos. Por esa razón queda pendiente la inspección manual visual de los formularios editables de aclaración/conclusión/seguimiento y de la bandeja de aclaraciones todavía pendientes del fiscalizador; sus operaciones sí fueron ejercitadas por HTTP/JWT en las pruebas integradas.

En una primera navegación automatizada rápida apareció un `NG0103` de detección de cambios. La comprobación posterior, con navegación controlada a la bandeja, renderizó sin reproducir la excepción; por el momento se clasifica como aviso de la secuencia acelerada de navegación, no como defecto funcional confirmado. La consola del servidor de desarrollo reportó el primer aviso.

### Pruebas y estado de cierre

- Backend Java 21 (`21.0.12.1`): `mvn test package` — **BUILD SUCCESS**, 61 tests, 0 fallas, 0 errores, 2 omitidos por guardas idempotentes que detectan que el fixture ya existe/cerró.
- Los tests de servicio de las conclusiones ejecutaron sus 7 escenarios. La prueba HTTP/JWT de continuación del fixture se ejecutó previamente de forma dirigida y pasó; el `mvn test package` final no la repitió para evitar mutar o recrear el fixture cerrado.
- Frontend: `npm test -- --watch=false` — **27 aprobadas en 11 archivos**; `npm run build` — **BUILD SUCCESS**.
- Arranque real contra MySQL validó Flyway (18 migraciones, V18 vigente) y Hibernate con `ddl-auto=validate`; V18 consta exitosa en BD. Se apagaron la API temporal y el navegador/servidor UI temporales. No se detuvieron procesos de otros usuarios en 8080/8081. La configuración local del frontend se restauró a `http://localhost:8080`.
- `git diff --check` — sin errores.
- Durante la primera ejecución se corrigió el harness de pruebas para consultar el actor de aclaración sin acceder a una asociación lazy fuera de sesión, seleccionar inequívocamente la cuenta MUNICIPIO recién creada y permitir temporalmente que el actor sintético original autenticara la generación del informe; esas cuentas se desactivaron al final. No se detectó ni corrigió defecto de dominio que requiriera cambiar migraciones o servicios.

**Resultado al cierre de Fase 3B.1:** backend y recorrido integrado API/JWT, seguimiento, segundo ciclo, cierre, PDF, bloqueos y autorización estaban verificados. En ese momento quedaba pendiente la inspección visual manual; su resultado actualizado figura en la sección Fase 3B.2.

## Validación visual final Fase 3B.2

Esta sección actualiza la limitación visual anotada en Fase 3B.1. La validación se hizo en navegador Chromium real sobre la UI autenticada, en escritorio (1366×900) y móvil (420×860). No se modificaron entidades, servicios de dominio ni migraciones; V18 sigue siendo la última y el esquema se validó en el arranque de Spring Boot.

### Fixture y recorrido visual

- Se creó por flujo normal API/UI el fixture sintético `VALIDACION_UI_REVISION_FASE3B`, expediente `FIS-2026-00012`. No se usaron datos personales reales ni SQL para alterar estados. Se mantuvo como evidencia técnica.
- MUNICIPIO abrió Pendientes de revisión, inició la revisión, consultó acta/evidencia y envió una aclaración sintética. La revisión cambió a `REQUIERE_ACLARACION`; se comprobó el formulario, obligatoriedad del mensaje, cancelar/enviar y el estado de envío.
- El FISCALIZADOR propietario consultó la aclaración y respondió desde la bandeja. Se observaron expediente, mensaje y timestamp, sin controles municipales. Después de la respuesta se mostró `RESPONDIDA`, la respuesta, actor y fecha; MUNICIPIO retomó la revisión `EN_REVISION` con acta y evidencias intactas.
- El formulario de conclusión mostró las seis opciones: `CONFORMIDAD`, `RECOMENDACION_MEJORAS_CORRECCIONES`, `ADVERTENCIA`, `RECOMIENDA_INICIO_PROCEDIMIENTO`, `MEDIDA_CORRECTIVA` y `OTRA`. Se verificaron campos condicionales, fundamento, texto descriptivo, denominación de OTRA, derivación y controles de seguimiento/plazo. En el fixture se concluyó `RECOMENDACION_MEJORAS_CORRECCIONES`, con seguimiento, fundamento, recomendación y plazo sintéticos. La UI reflejó revisión `FINALIZADA` y expediente `EN_SEGUIMIENTO`.
- Se creó una programación futura `PROGRAMADA` en el mismo expediente, con referencia a revisión origen y motivo sintético. El expediente mostró historial y programación; no se creó otro expediente ni se ejecutó una segunda diligencia (no era necesaria para esta inspección y el ciclo completo ya consta en Fase 3B.1). Estado final del fixture: `EN_SEGUIMIENTO`, con dos programaciones en el mismo expediente, una diligencia/acta, una revisión, una aclaración y un informe de conclusión.

### Hallazgos y correcciones de UI

- Al alternar entre conclusiones, quedaban valores condicionales de la selección anterior. Se corrigió el cambio de tipo para limpiar los campos propios de la opción, evitando contaminación entre conclusiones.
- El texto de ayuda de `OTRA` era confuso. Se ajustó a una descripción propia de otra conclusión y se hizo explícita la denominación obligatoria.
- Un envío incompleto llegaba al backend y producía un 409 genérico. La UI ahora valida los requisitos condicionales y deshabilita el envío incompleto, con ayudas legibles.
- Se añadieron fecha de solicitud visible, ayudas de obligatoriedad, acciones explícitas Cancelar/Enviar y estados de carga para solicitud/respuesta de aclaración, además de Cancelar/Guardar para programación de seguimiento.
- El formulario de seguimiento ahora indica el código del expediente y la revisión origen. Los botones, selects, campos de fecha/hora y áreas de texto se mantienen utilizables en viewport móvil.

### Responsive, consola y red

- A 1366×900 y 420×860 se inspeccionaron solicitud de aclaración, aclaración/respuesta del fiscalizador, conclusión, campos condicionales, seguimiento e historial/timeline. En los recorridos controlados, el ancho de documento móvil permaneció en 420 px (sin scroll horizontal); los botones apilan/ajustan su distribución y los campos conservan legibilidad.
- La respuesta real del fiscalizador y la conclusión/seguimiento guardaron correctamente. En verificaciones posteriores de estados de formulario se usaron respuestas GET controladas únicamente para renderizar; esas interacciones no hicieron POST ni alteraron datos. Cancelar limpió los borradores. Los formularios bloquearon valores incompletos antes de emitir la solicitud.
- No se reprodujo `NG0103` en la inspección controlada posterior a las correcciones. Tampoco se observaron errores JavaScript/Angular, 4xx inesperados, 5xx, duplicación de solicitudes ni bucles en las comprobaciones posteriores. El 409 inicial de conclusión incompleta fue reproducible antes de corregir la validación y dejó de emitirse después.

### Incidencia de datos y alcance

En la primera navegación acelerada del navegador, el automatizador seleccionó la primera fila de la bandeja en vez del expediente visual objetivo: el expediente preexistente FIS-2026-00010 (fixture de Fase 3A) pasó de pendiente a revisión activa y recibió una solicitud sintética de aclaración. Fue un efecto no intencional de la validación; no se intentó revertirlo ni modificar manualmente su estado. Debe inspeccionarse por el equipo responsable antes de responder o continuar esa revisión. El fixture correcto de Fase 3B.2 es FIS-2026-00012. Se deja esta incidencia explícita para no presentar como intacta la fila Fase 3A.

### Pruebas

- Frontend `npm test -- --watch=false`: **27 aprobadas en 11 archivos** después de restaurar el `apiUrl` temporal a `http://localhost:8080`. Una ejecución inicial, mientras el archivo local apuntaba temporalmente a 18137 para navegador, falló 3 expectativas que tenían URL fija 8080; se repitió tras la restauración y pasó.
- Frontend `npm run build`: **BUILD SUCCESS**.
- Backend Java 21 (`21.0.12.1`) `mvn test package`: **BUILD SUCCESS**, 61 pruebas, 0 fallas, 0 errores, 2 omitidas por guardas de integración que detectan que los fixtures ya existen.
- Flyway/Hibernate: el arranque real registró 18 migraciones validadas, V18 vigente, sin migración pendiente, e inicialización del EntityManager. No se cambió V1–V18 ni se creó migración nueva.
- `git diff --check`: sin errores tras consolidar esta sección.

**FASE 3B COMPLETAMENTE CERRADA.** Los formularios editables solicitados quedaron inspeccionados y operativos en escritorio/móvil, con los defectos concretos descritos corregidos. La incidencia accidental de FIS-2026-00010 queda documentada para seguimiento y no se alteró más.
