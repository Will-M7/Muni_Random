# Auditoría y recuperación de V20 — 13 de octubre de 2026, America/Lima

## Alcance y estado

Rama `willian/funcional-san-miguel`. La base original `san_miguel` en 3307 se consultó exclusivamente en lectura. No se ejecutaron migraciones, regularización, `repair`, `baseline`, borrados, cambios de credenciales ni modificaciones de volúmenes sobre ella. Producción y V27 no se usaron. No hubo push ni despliegue.

**La recuperación de la base real queda pendiente de decisiones de identidad. El código y el procedimiento pueden validarse con datos sintéticos sin tomar esas decisiones.**

Respaldo: `/home/willian/respaldos_muni/san_miguel_20261013_011235.sql`, 88 KiB; dump completo de MySQL 8.4.11 con marca final de terminación. SHA-256:

```
67107feeec4166e97ce5fecafe6c53819afce288ad71fe2817b7903c81647271
```

Se restauró sin errores en `v20_backup`, dentro del contenedor nuevo `san-miguel-v20-audit`, MySQL 8.4.11, publicado exclusivamente en `127.0.0.1:13317`. No comparte volúmenes con la base original. Sus 308 filas, incluidas contraseñas y todo el historial Flyway, coinciden exactamente con las filas de la original. El DDL normalizado también coincide; `SHOW CREATE TABLE` en la restauración explicita `CHARACTER SET utf8mb4`, una diferencia de representación sin cambio semántico. Esto verifica el respaldo SQL, **no** los archivos PDF, fotografías o adjuntos externos del directorio de uploads, que necesitan su propio respaldo antes de una intervención real.

Huella del dump de la original usado durante la auditoría: `58a0308a69f680ba7351a5e65273982cab78a90385fdf0409fbafa15815f337c` (opciones reproducibles: `--single-transaction --skip-lock-tables --skip-comments --skip-dump-date --skip-extended-insert --hex-blob --no-tablespaces`). No se publicaron sus contraseñas ni sus filas personales.

## Causa confirmada y esquema parcial

V20 contiene tres sentencias: agregar origen/referencia e índice al expediente; agregar unicidad a `app_user.fiscalizador_id`; crear `expediente_documento`. El segundo ALTER falla por identidades compartidas. MySQL confirma el primer ALTER independientemente de la sentencia siguiente; Flyway registra V20 con `success=0`. Un arranque posterior falla durante validación. Borrar la fila fallida sin reconciliar el esquema llevaría después a columnas duplicadas.

En la original y el respaldo: V1–V19 correctas; V20 checksum `1487232706`, fallida. Existen `dependencia_procedencia VARCHAR(160) NULL`, `referencia_solicitud VARCHAR(160) NULL`, y `uk_expediente_solicitud_externa`. Faltan `uk_app_user_fiscalizador` y `expediente_documento`. No se modificó ninguna migración histórica para resolverlo.

Las pruebas antiguas `RevisionFase3BIntegradaTest` y `RevisionFase3BContinuacionTest` creaban cuentas temporales usando FIS-001/FIS-002 y al finalizar solo las desactivaban, conservando el vínculo compartido. El código confirma ese mecanismo, y los prefijos/roles de las cuentas encontradas coinciden. Desactivar una cuenta no excluye su identidad de un índice UNIQUE. No se presume que esas cuentas sean eliminables.

## Cuentas pendientes y autoría encontrada

| Identidad actual | id | Cuenta | Activa | Referencias de autoría directa encontradas |
|---|---:|---|---|---|
| FIS-001 | 3 | fiscalizador | sí | 2 actas, 4 participantes, 2 evidencias, 15 eventos, 1 respuesta a aclaración |
| FIS-001 | 12 | f3b_fiscal_propietario_63e33da2 | no | 1 acta, 1 participante, 1 evidencia, 5 eventos |
| FIS-001 | 16 | f3b_fiscal_propietario_5c2a40c5 | no | 1 evento, 1 respuesta a aclaración |
| FIS-001 | 20 | f3b_fiscal_propietario_c8634349 | no | ninguna encontrada |
| FIS-001 | 24 | f3b_fiscal_propietario_d62cc97a | no | 1 acta, 1 participante, 1 evidencia, 5 eventos |
| FIS-002 | 13 | f3b_fiscal_ajeno_05d99a0f | no | ninguna encontrada |
| FIS-002 | 17 | f3b_fiscal_ajeno_6089e516 | no | ninguna encontrada |
| FIS-002 | 21 | f3b_fiscal_ajeno_11f999a4 | no | ninguna encontrada |
| FIS-002 | 25 | f3b_fiscal_ajeno_3a91f309 | no | ninguna encontrada |
| sin identidad | 8 | Mayta Arotaype | sí | ninguna encontrada; rol FISCALIZADOR incompleto |

“Ninguna encontrada” no autoriza a eliminar, reasignar ni considerar una cuenta descartable. Las cuentas temporales municipales también tienen autoría: id 11 (1 expediente, 1 programación, 1 revisión, 1 aclaración solicitada y 5 eventos), id 15 (1 evento), id 23 (1 programación, 1 revisión y 9 eventos). La cuenta municipal id 1 tiene 6 expedientes, 10 programaciones, 2 revisiones, 2 aclaraciones y 31 eventos. Se preservan igualmente.

FIS-001 tiene 5 solicitudes, 17 programaciones, 5 diligencias y 1 ubicación. Esas operaciones conservan su responsable original. Hay 12 expedientes, 85 eventos, 4 actas y 4 evidencias. La tabla de diligencias no tiene autores en `registrado_por_id` para estos registros ordinarios: la trazabilidad existente proviene de actores del historial, actas, participantes y evidencias. No se atribuyeron retrospectivamente autores ni se sobrescribieron eventos de migración/sistema.

Decisión recibida: conservar **provisionalmente en el modelo propuesto** FIS-001 con id 3. No se aplicó a ninguna cuenta real. FIS-002 no tiene propietario elegido. Mayta Arotaype continúa pendiente. Las cuentas temporales mantienen sus vínculos actuales en original y respaldo intacto.

Opciones para revisión humana:

1. **Identidad técnica propia por cuenta temporal**, después de verificar su naturaleza sintética: conservar cuentas y todos sus autores; crear registros operativos claramente técnicos e inactivos y cambiar únicamente la vinculación actual autorizada. No mover expedientes, diligencias, actas ni eventos históricos. La identidad actual nueva no debe reinterpretar el responsable histórico anterior. Es una propuesta, no una operación aplicada.
2. **Retirar el rol operativo y archivar cuentas temporales verificadas**, conservando cuenta, hash de contraseña y referencias históricas; retirar solo su vínculo actual tras aprobación expresa. Evita nuevas identidades artificiales. Restaurarlas posteriormente exigiría decidir rol e identidad operativa. No basta con desactivar/archivar manteniendo vínculos duplicados: V20 seguiría fallando.
3. **Identidad real individual confirmada**, cuando una cuenta representa efectivamente a una persona del directorio. Vincular explícitamente a una identidad libre, sin reutilizar FIS-001 ni FIS-002 por similitud de nombre, actividad o ausencia de historial.

Para FIS-002 puede conservarse el registro operativo sin cuenta mientras se confirma una persona real; cada cuenta temporal necesitaría una de las opciones anteriores. Elegir arbitrariamente una de las cuatro está excluido. Para id 8 debe confirmarse su identidad real libre o autorizar un alta operativa individual; nunca se infiere FIS-001/FIS-002. Ninguna opción está ejecutada.

## Cambios implementados

- JPA conserva cuenta → identidad como 1:1 opcional; municipio/admin sin rol FISCALIZADOR no necesitan identidad. Las asignaciones y diligencias siguen siendo N:1 hacia el fiscalizador; no se añadió una tabla N:M entre cuentas y personal.
- `UsuarioService`: retirar el rol operativo también retira su vínculo actual, conservando directorio, asignaciones y autores; editar o reasignar roles a una cuenta fiscalizadora heredada sin identidad exige selección explícita; no se inventa una identidad para completar datos existentes.
- `FiscalizadorRepository`: bloqueo pesimista al vincular una identidad existente. La unicidad SQL de V20 sigue siendo la defensa final frente a concurrencia. Cuentas inactivas/archivadas también reservan su identidad.
- `AppUser`, `AuthService`, `SecurityConfig`, `AppUserRepository`: login y validación de JWT rechazan cuentas archivadas, identidades ausentes/inactivas o vínculos sin rol operativo; roles/capacidades e identidad se cargan antes de cerrar la sesión JPA. Las autoridades se obtienen nuevamente de la base por cada JWT; no se confía en el claim fiscalizador para decidir propiedad.
- `IdentityMigrationPreflight`: antes del primer DDL de V20 comprueba identidades duplicadas y discordancia entre roles efectivos y vínculo. Informa ids/cuentas y detiene la migración, sin regularizar datos.
- **V28 nueva**, `V28__user_operational_identity_foreign_key.sql`: agrega la FK que faltaba en `app_user.fiscalizador_id`, sin cascadas. Un huérfano bloquea la migración; no se elimina automáticamente. V27 sigue exclusiva de `db/production` y no se ejecuta.
- `UserHistoryService`: no confunde el vínculo técnico cuenta-identidad con una operación histórica al introducir la nueva FK. Las operaciones, asignaciones y autores siguen obligando a archivar y conservar registros.
- Las tres pruebas de integración antiguas requieren activación expresa y URL aislada validada **antes de inicializar Spring/Flyway**. Los recorridos ahora crean identidades independientes, conservan sus fixtures de prueba y son repetibles; no comparten FIS-001/FIS-002 ni cambian credenciales preexistentes. La prueba de continuación prepara su propio ciclo persistido, sin depender de ejecución previa ni reactivar actores antiguos.
- Corrección de fechas en tests: programaciones que se ejecutan se realizan durante el día permitido; la prueba de reprogramación usa una fecha relativa futura.

Los endpoints municipales siguen sujetos a capacidades explícitas. ADMIN_SISTEMA conserva solo sus permisos administrativos predeterminados; no recibe permisos de ejecución. Los servicios operativos mantienen comprobación de propiedad contra la identidad autenticada. Los tests HTTP verifican rechazo 403 de lectura/edición/ejecución ajena, aclaraciones ajenas y operación por administradores.

## Recuperación validada, solo en aislamiento

1. Detener escrituras en la copia, verificar backup SQL y archivos, registrar conteos, huellas, esquema y checksum de cada migración. Volver a auditar si cambia cualquier estado.
2. Aprobar un manifiesto explícito por cuenta: id/username, rol y vínculo anteriores, estado, identidad objetivo o retirada de rol/vínculo, justificación y responsable. Aplicarlo **primero en otra copia**, en una transacción, con precondiciones sobre cada valor anterior y rollback ante discrepancias. No se entrega un UPDATE masivo que decida identidades.
3. Verificar roles FISCALIZADOR con identidad propia, ausencia de duplicados/huérfanos y preservación de autores, operaciones y hashes de contraseña. No cambiar asignaciones históricas para acomodar la vinculación actual.
4. `V20Recovery` rechaza cuentas incompatibles, checksums/descripciones/tipos divergentes y estados de esquema no reconocidos. No modifica usuarios. Acepta el estado parcial observado: columnas e índice de expediente existentes; completa solo índice único de cuenta y tabla documental usando las sentencias originales de V20. Verifica tipos, nulabilidad, índices, FKs, motor y collation antes de tocar el historial.
5. Solo tras esa reconciliación, usa `Flyway.repair()` para retirar la entrada fallida y registra V20 con `skipExecutingMigrations(true)` y `target("20")`. Ese registro mantiene el checksum de la migración original; no vuelve a ejecutar ALTERs sobre columnas existentes. Los checksums/descripciones/tipos aplicados se verifican **antes** de repair, y las filas exitosas de V1–V19 se comparan después. No hay baseline ni UPDATE manual para marcar una migración como exitosa.
6. La herramienta también puede reanudar una interrupción entre repair y registro, exclusivamente si V1–V19 están correctas y el esquema V20 está completo. Ante estados diferentes, se detiene para auditoría; no hace rollback destructivo de columnas.
7. Validar V20; aplicar posteriormente V21–V26 y V28 desde `classpath:db/migration`. Verificar conservación de documentos preexistentes y sus autores al agregar categoría en V25. Validar nuevamente Flyway, Hibernate, integración HTTP, login y salud.

La herramienta está en fuentes de pruebas y rechaza cualquier URL fuera de `127.0.0.1:13317/v20_*`. No está registrada como endpoint ni se ejecuta automáticamente al arrancar. Una intervención futura en la original exige un procedimiento revisado específicamente para esa base; el guard no debe quitarse para “probar” en ella.

Flyway documenta que `repair` también puede realinear checksums y que los objetos dejados por DDL no transaccional deben reconciliarse previamente: [Repair](https://github.com/flyway/flyway/blob/main/documentation/Reference/Commands/Repair.md). El registro de una migración cuyo DDL ya fue ejecutado se hace con la API oficial [skipExecutingMigrations](https://github.com/flyway/flyway/blob/main/documentation/Reference/Configuration/Flyway%20Namespace/Flyway%20Skip%20Executing%20Migrations%20Setting.md). Ambos se probaron con la versión instalada 11.7.2; no se actualizaron librerías para ocultar el fallo.

## Comandos reproducibles seguros

Desde `/home/willian/muni/San_miguel`, con acceso a Docker y Maven:

```bash
bash backend/scripts/verify-v20-isolated.sh
npm test -- --watch=false
npm run build
```

El script exige Java 21, valida etiqueta/puerto del contenedor aislado, restaura el respaldo solo si `v20_backup` no existe y ejecuta compilación, pruebas y empaquetado. Nunca borra bases, contenedores ni volúmenes. Cada prueba de migración crea su propia base `v20_test_*`; se conservan como evidencia. Para pruebas sin MySQL: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH mvn -f backend/pom.xml test` (integración se informa como omitida, no como aprobada).

Arranque aislado, después de validar/empaquetar:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
DB_HOST=127.0.0.1 DB_PORT=13317 DB_NAME=v20_clean \
DB_USER=root DB_PASSWORD=isolated_v20_only SERVER_PORT=18087 \
APP_STORAGE_PATH=backend/target/runtime-isolated/solicitudes \
APP_DILIGENCIAS_PATH=backend/target/runtime-isolated/diligencias \
java -jar backend/target/san-miguel-api-0.0.1-SNAPSHOT.jar \
  --server.address=127.0.0.1 \
  --spring.flyway.locations=classpath:db/migration \
  --spring.flyway.target=28 --spring.flyway.baseline-on-migrate=false
```

En otra terminal: `curl --fail http://127.0.0.1:18087/actuator/health`. Frontend: `npm start`; para apuntarlo al puerto alternativo de prueba deben revisarse sus URLs/proxy locales, sin tocar configuración productiva.

Uso explícito de recuperación **solo después de resolver identidades en una copia autorizada**:

```bash
mvn -f backend/pom.xml -DskipTests test-compile dependency:build-classpath \
  -Dmdep.outputFile=target/test-classpath.txt
export RECOVERY_JDBC_URL='jdbc:mysql://127.0.0.1:13317/v20_copia_aprobada?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=America/Lima'
export RECOVERY_DB_USER=root
export RECOVERY_DB_PASSWORD=isolated_v20_only
java -cp "backend/target/test-classes:backend/target/classes:$(cat backend/target/test-classpath.txt)" \
  pe.gob.munisanmiguel.integration.V20Recovery --reconcile-and-repair-isolated
```

No ejecutar esto contra `v20_backup` esperando que seleccione propietarios: su rechazo seguro es parte de la validación. No usar el arranque predeterminado contra `san_miguel` para reparar la base real: aún está bloqueada legítimamente.

## Pendientes antes de producción

Decidir identidades/roles de las cuentas listadas; aprobar y repetir el manifiesto sobre una nueva copia; respaldar y verificar también archivos externos; revisar capacidades efectivamente configuradas; evaluar compatibilidad Flyway/MySQL. Flyway 11.7.2 advierte que esta edición declara MySQL hasta 8.1 como versión probada, aunque todos los ensayos se ejecutan sobre 8.4.11. Las migraciones históricas usan `VALUES()` en upserts, que MySQL advierte como obsoleto; no se alteraron sus checksums. El frontend tiene advertencias de tamaño de bundle/CSS, sin errores de compilación.

El valor histórico `baseline-on-migrate: true` de application.yml sigue intacto para respetar el alcance y la configuración existente; las herramientas y los arranques de esta validación lo fuerzan a false. Revisar esa configuración antes de una intervención productiva; no usar baseline como recuperación.

## Archivos modificados o agregados

- `backend/scripts/verify-v20-isolated.sh`
- `backend/src/main/java/pe/gob/munisanmiguel/migration/IdentityMigrationPreflight.java`
- `backend/src/main/resources/db/migration/V28__user_operational_identity_foreign_key.sql`
- `backend/src/test/java/pe/gob/munisanmiguel/entity/OperationalIdentityTest.java`
- `backend/src/test/java/pe/gob/munisanmiguel/integration/IsolatedDatabaseInitializer.java`
- `backend/src/test/java/pe/gob/munisanmiguel/integration/IsolationGuardTest.java`
- `backend/src/test/java/pe/gob/munisanmiguel/integration/MigrationRecoveryTest.java`
- `backend/src/test/java/pe/gob/munisanmiguel/integration/OperationalRelationsIntegrationTest.java`
- `backend/src/test/java/pe/gob/munisanmiguel/integration/V20Recovery.java`
- `docs/backend-v20-recuperacion.md`
- `backend/src/main/java/pe/gob/munisanmiguel/config/SecurityConfig.java`
- `backend/src/main/java/pe/gob/munisanmiguel/entity/AppUser.java`
- `backend/src/main/java/pe/gob/munisanmiguel/repository/AppUserRepository.java`
- `backend/src/main/java/pe/gob/munisanmiguel/repository/FiscalizadorRepository.java`
- `backend/src/main/java/pe/gob/munisanmiguel/service/AuthService.java`
- `backend/src/main/java/pe/gob/munisanmiguel/service/UserHistoryService.java`
- `backend/src/main/java/pe/gob/munisanmiguel/service/UsuarioService.java`
- `backend/src/test/java/pe/gob/munisanmiguel/integration/AdminSettingsAcceptanceTest.java`
- `backend/src/test/java/pe/gob/munisanmiguel/integration/RevisionFase3BContinuacionTest.java`
- `backend/src/test/java/pe/gob/munisanmiguel/integration/RevisionFase3BIntegradaTest.java`
- `backend/src/test/java/pe/gob/munisanmiguel/service/AuthServiceTest.java`
- `backend/src/test/java/pe/gob/munisanmiguel/service/ExpedienteFiscalizacionServiceTest.java`
- `backend/src/test/java/pe/gob/munisanmiguel/service/UsuarioFiscalizadorAltaTest.java`

## Resultado final de la validación

Validación terminada el 13/10/2026, aproximadamente 02:10 America/Lima.

| Comprobación | Resultado |
|---|---|
| Java de compilación, pruebas y ejecución | OpenJDK 21; compilación release 21 |
| Backend completo, `mvn package` | **168 aprobadas, 0 fallidas, 0 errores, 0 omitidas; BUILD SUCCESS** |
| Pruebas con base real aislada dentro de esa suite | 14: 7 de migraciones/recuperación, 4 de relaciones, 3 de aceptación/HTTP/JWT |
| Frontend, `npm test -- --watch=false` | **68 aprobadas, 0 fallidas**, 20 archivos |
| Frontend, `npm run build` | Correcto; advertencias de tamaño de bundle y dos CSS |
| Base limpia | V1–V26 y V28 correctas: 27 migraciones; V27 ausente |
| Copia sintética con V20 fallida | Recuperada hasta V28, con checksum V20 original y datos previos preservados |
| Backend empaquetado sobre base limpia | Arranca en 127.0.0.1:18087; salud HTTP 200 `{"status":"UP"}` |
| Backend empaquetado sobre copia sintética recuperada | Arranca en 127.0.0.1:18088; salud HTTP 200 `{"status":"UP"}` |
| Respaldo restaurado intacto | Se reproduce literalmente `Detected failed migration to version 20 (fiscalizacion origen y documentos)` al arrancar; permanece V20 `success=0` |
| Original | Huella final idéntica a la huella inicial de auditoría; 21 cuentas y 85 eventos preservados; V20 sigue fallida |
| Migraciones históricas y configuración productiva | Sin cambios; V27 no ejecutada |
| Git | Sin push ni despliegue; modificaciones locales para revisión |

Base sintética recuperada usada para el segundo arranque: `v20_test_0073c29df83041ffb9aa56ce615111be`. El backend aislado en 18087 quedó ejecutándose para revisión; 18088 también queda disponible para comprobar la recuperación. El proceso de prueba sobre `v20_backup` terminó con el fallo esperado y no quedó servidor en 18089.

Evidencia de esta sesión: `/tmp/san-miguel-backend-final.log`, `/tmp/san-miguel-frontend-tests.log`, `/tmp/san-miguel-frontend-build.log`, `/tmp/san-miguel-backup-startup.log`, `/tmp/san-miguel-runtime-clean.log`, `/tmp/san-miguel-runtime-recovered.log`; informes JUnit en `backend/target/surefire-reports`. Los logs y bases sintéticas se conservan. Los dumps privados de comparación en `/tmp` tienen permisos 0600; no se incorporaron al repositorio.

**Pendiente:** decidir y aprobar la regularización de las nueve cuentas listadas, verificar respaldo de archivos externos y repetir el procedimiento completo sobre una copia real regularizada. Por eso no se declara solucionado el arranque de `san_miguel`, aunque el backend corregido y las migraciones estén validados en aislamiento.
