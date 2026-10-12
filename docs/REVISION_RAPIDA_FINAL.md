# Revisión rápida final — San Miguel

Fecha: 09/10/2026, America/Lima. Rama comprobada: `willian/funcional-san-miguel`.
Revisión limitada a menos de 3 minutos; no constituye una prueba integral en navegador.

## 1. Flujos revisados

- **ADMIN_SISTEMA:** inspección de creación de MUNICIPIO/FISCALIZADOR, vínculo operativo transaccional, contraseña mínima, preferencias individuales y contratos de integración DNI. Los endpoints administrativos delegan la comprobación ADMIN al servicio. Inspeccionada la separación de cuota PERUAPI_COM/RENIEC y el permiso de prueba DNI; sin consultas externas.
- **MUNICIPIO:** inspección de rutas, creación con documentos, borrador de ubicación, programación/asignación, consulta de expediente y actas, adjuntos municipales y revisión/conclusión/seguimiento. Los contratos frontend y controladores revisados coinciden. Se conserva la selección de conclusión y las restricciones esenciales de seguimiento/cierre; los textos complementarios de conclusión son opcionales.
- **FISCALIZADOR:** inspección de agenda, inicio sin GPS, guardado, evidencia y retirada lógica, finalización/acta y descarga HTML. El inicio bloquea la programación y reutiliza una diligencia activa; se comprueban propiedad y fecha. La descarga verifica la preferencia individual. El HTML construye Google Maps desde pendientes y sus marcas no llaman al backend.

Estas comprobaciones son estáticas salvo los casos cubiertos por las pruebas indicadas abajo. No se inició Spring Boot ni se comprobó una sesión real de cada rol.

## 2. Bug encontrado y corregido

Al regresar del mapa, `restaurarBorrador()` sobrescribía siempre el tipo de visita con PROGRAMADA, aunque el usuario hubiera elegido INOPINADA. Ahora conserva INOPINADA y utiliza PROGRAMADA como valor predeterminado para borradores sin un tipo válido.

Archivos modificados en este bloque:

- `src/app/pages/expedientes/expediente-nuevo.ts`.
- `src/app/pages/expedientes/expediente-nuevo.spec.ts`: regresión para conservar tipo de visita, programación y datos del administrado/dirección.
- Este informe.

## 3. Pendientes y límites de la revisión

- **Estado de cuota DNI desactualizado:** una pantalla municipal abierta con `cuotaDni.exhausted=true` mantiene deshabilitado el botón aunque ADMIN cambie a RENIEC o llegue la renovación diaria. La cuota se consulta al iniciar el componente y después de consultar DNI; el botón bloqueado no permite esa actualización. Solución temporal: volver a abrir Crear fiscalización. El ingreso manual permanece disponible. Detectado por inspección; no corregido en este bloque breve.
- Pendiente la validación manual de los tres roles, apertura de PDF, HTML mediante `file://` y Google Maps. No se declaran aprobadas pruebas visuales.
- La persistencia real, las migraciones y las credenciales DNI no se probaron contra MySQL ni proveedores externos.

## 4. Pruebas realmente ejecutadas

- `npm run build`: **aprobado**; compilación de plantillas Angular incluida.
- Angular focalizado: `npm test -- --watch=false --include='src/app/pages/expedientes/expediente-nuevo.spec.ts' --include='src/app/pages/diligencia/diligencia.spec.ts' --include='src/app/pages/admin/admin.spec.ts'`: **19 pruebas aprobadas en 3 archivos**, incluida la regresión nueva. Servicios simulados; sin consumo DNI real.
- Maven con Java 21: `mvn -f backend/pom.xml -Dtest=UsuarioFiscalizadorAltaTest,RevisionFiscalizacionConclusionTest,DiligenciaFiscalizacionServiceTest test`: **BUILD SUCCESS, 34 pruebas aprobadas**, cero fallos/errores. Incluyó las fases de compilación Java y de pruebas, con clases existentes actualizadas; repositorios simulados, sin arrancar el backend contra MySQL.
- `git diff --check`: **aprobado**.
- No se ejecutaron suites completas.

## 5. Migraciones revisadas, sin aplicar

- V22: archivado de usuarios.
- V23: contador diario persistente de PerúAPI.
- V24: retirada lógica de evidencias, autor, fecha y motivo.
- V25: categoría de documentos/actas adjuntas; valor predeterminado DOCUMENTO para registros anteriores.

Inspección estática: cambios aditivos, sin borrado de registros. No se consultó `flyway_schema_history`; el estado efectivo de V22–V24 en MySQL no está comprobado. V25 quedó pendiente según la entrega anterior. No se aplicó ninguna migración ni se modificó su contenido. Antes de un arranque con Flyway se debe verificar el estado y respaldo de `san_miguel_pruebas_v20` y obtener autorización para las pendientes.

## 6. Veredicto

**LISTO CON LIMITACIONES** para continuar la validación local, sujeto a las migraciones pendientes y las pruebas manuales descritas. El bug de restauración quedó corregido y probado; persiste la limitación de refresco de cuota DNI.

Todos los cambios siguen locales. No se hicieron commit, push, pull, merge, cambio de rama, reset, clean, operaciones MySQL ni llamadas reales a PerúAPI.
