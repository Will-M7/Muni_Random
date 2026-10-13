# Estado de entrega — 13 de octubre de 2026

**Preparado para instalación en la intranet; NO desplegado en la municipalidad.**
No existe todavía servidor, DNS ni certificado municipal confirmado. No se activó acceso.

Base Git: `willian/funcional-san-miguel`, HEAD
`081d87c926c5c1cf454234a4d94cdf717dcc3325`, con modificaciones y archivos nuevos sin commit.
Se incorporan los archivos actuales, no una descarga de main. Identidad exacta del paquete:
`RELEASE.json`, `SOURCE.sha256`, `images.lock.env` y `IMAGE-INSPECT.json`.
No se realizó commit ni push.

## Comprobaciones ejecutadas

| Verificación | Resultado |
|---|---|
| Frontend | Compilación production correcta; 84 pruebas, 22 archivos, sin fallos |
| Backend Java 21 / Spring Boot 3.5.9 | Compilación y empaquetado correctos; 172 pruebas aprobadas; 16 de integración condicionadas omitidas |
| Pruebas de integración omitidas | Requieren las bases/fixtures QA anteriores; no se habilitaron para preservar esos entornos |
| MySQL aislado | 8.4.11; instancia y volúmenes nuevos, sin puertos publicados |
| Flyway | 29 migraciones correctas, V1–V29 en orden, incluida V27; sin repair ni baseline |
| Hibernate | Esquema validado; readiness correcto |
| Estado inicial por SQL | 1 usuario activo ADMIN_SISTEMA, 1 relación de rol, 7 capacidades administrativas, 0 permisos operativos |
| Datos operativos | 0 fiscalizadores, solicitudes, expedientes, programaciones, diligencias, revisiones, evidencias, actas e historiales operativos |
| Almacenamiento | 0 archivos; volumen independiente persistente con permisos para el usuario backend |
| Proveedor DNI | DISABLED, sin claves ni tokens almacenados; sin consultas reales |
| Reinicio MySQL/backend | Mismos conteos y administrador; migraciones no repetidas |
| Respaldo | Archivo cifrado GnuPG generado a partir del ensayo sin datos operativos |
| Restauración | Segundo proyecto y volúmenes nuevos; mismos conteos, BCrypt y cinco secretos conservados; backend healthy |
| Docker Compose / Nginx | Configuración válida; plantilla HTTPS/redirección validada con nginx -t en contenedor sin puertos publicados |
| Transporte MySQL | require_secure_transport=ON; conexión comprobada con TLS; JDBC productivo exige TLS |
| Código aprobado | Huellas de 124 archivos frontend y migraciones históricas idénticas antes/después |
| API frontend | Bundle productivo sin URL http://localhost:8080; configuración del mismo origen |

El ensayo utilizó una CA y certificado **exclusivamente locales**, no incluidos en la entrega,
para comprobar la configuración. No demuestra HTTPS válido en equipos municipales.
El respaldo ensayado está vacío de adjuntos: se comprobó el mecanismo y la conservación
de claves, no la restauración de documentos reales ni el rendimiento con una base grande.
Los ensayos y sus secretos se conservan fuera del paquete bajo `.local/production-validation/`;
no deben transportarse a producción. Sus contenedores se detienen al cerrar la preparación,
sin borrar volúmenes. Los entornos previos de desarrollo no se modificaron.

## Ajustes limitados a infraestructura/aprovisionamiento

- Perfil prod explícito: Hibernate validate, Flyway sin clean/baseline/out-of-order,
  conexión MySQL cifrada y configuración sin credenciales por defecto.
- Protección anterior a las migraciones: acepta base vacía o instalación productiva con
  V27 verificada; rechaza convertir una base de desarrollo o un arranque parcial inseguro.
- Bootstrap productivo solo cuando no existen usuarios. No cambia contraseñas existentes
  ni crea otro administrador si cambia el nombre configurado.
- Plantilla Nginx productiva con DNS configurable, HTTP→HTTPS y HSTS. No se modificó
  el Nginx del entorno manual actual.
- Imágenes entregables, manifiestos, generación interactiva de secretos, respaldo cifrado,
  restauración en volúmenes nuevos y plantillas de programación de respaldos.

No se modificaron funcionalidades, interfaces, contratos API, integración DNI ni SQL históricos.
No se ejecutaron pruebas manuales, navegación automatizada, consultas DNI, recorridos API
ni creación de registros operativos. Se usaron pruebas automatizadas y verificaciones técnicas.

## Pendientes y limitaciones

1. Ingeniero: SO/arquitectura, recursos, IP, Docker/Compose, acceso de instalación y nombre DNS.
2. Certificado municipal y confianza de CA en computadoras/celulares; renovación y responsables.
3. Credenciales productivas nuevas mediante `init-secrets` en el servidor; nunca usar las del ensayo.
4. Subredes autorizadas/firewall, conectividad de salida a mapas/proveedores y acceso SSH restringido.
5. Destino externo, frecuencia/retención, RPO/RTO y monitoreo de respaldos; instalar el timer solo tras acordarlos.
6. Aprobación final del destino, versión y comprobaciones antes de activar Nginx.
7. Las imágenes son linux/amd64. Otro tipo de CPU requiere reconstrucción y validación.

Advertencias conocidas: CSS de expediente-detalle y fiscalizador supera el umbral de aviso
(no el de error); algunas migraciones históricas usan VALUES() deprecado por MySQL, sin
impedir su ejecución. No se reescribieron migraciones para quitar advertencias.
Se conservó el JWT existente en almacenamiento del navegador; no se cambió la autenticación.
TLS interno MySQL cifra la conexión; sslMode=REQUIRED no valida identidad de certificado
del servidor MySQL, que permanece aislado dentro de la red Docker del mismo host.

Operación, instalación, respaldo, recuperación y actualización: `ops/OPERACION.md`.
