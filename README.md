# Sistema de Verificación Domiciliaria — San Miguel

Aplicación full-stack para registrar solicitudes municipales, revisar expedientes, consultar documentos PDF y geolocalizar domicilios.

## Requisitos

- Node.js 24+ y npm 11+
- Java 21 (requisito del backend; no usar Java 25 para Maven)
- Maven 3.9+ o Docker para usar la imagen Maven
- Docker Compose v2+

## Levantar el sistema

```bash
cp .env.example .env
docker compose up -d
docker compose ps
cd backend
mvn spring-boot:run
```

En otra terminal:

```bash
npm install
npm start
```

Abre `http://localhost:4200`. La URL de la API está centralizada en `src/environments/environment.ts`. El puerto del host de MySQL es `3307` por defecto para no interferir con una instalación local en `3306`; dentro de Docker sigue siendo `3306`.

Si Maven no está instalado:

```bash
docker run --rm --network san_miguel_default -v "$PWD/backend:/app" -w /app -p 8080:8080 maven:3.9-eclipse-temurin-21 mvn spring-boot:run
```

## Credenciales demo

Se crean como datos iniciales de desarrollo mediante Flyway:

- Municipio: `municipio` / `municipio2026`
- Fiscalizador: `fiscalizador` / `fiscal2026`

Las contraseñas se almacenan como BCrypt. Cámbialas antes de un despliegue real.

No se crea una cuenta ADMIN_SISTEMA con una contraseña predeterminada. Para provisionar la primera, inicia el backend con `ADMIN_BOOTSTRAP_USERNAME` y `ADMIN_BOOTSTRAP_PASSWORD` (mínimo 12 caracteres); el arranque la crea solo si ese username no existe y persiste la contraseña con BCrypt.

Las capacidades efectivas se cargan desde `user_role`, `app_role` y `role_capability` en cada petición JWT. Los usuarios demo conservan sus nombres, contraseñas y relaciones con fiscalizadores. La administración técnica está en `/admin`; los endpoints de catálogo están bajo `/api/admin/roles` y `/api/admin/capacidades`.

La administración de cuentas está reservada a ADMIN_SISTEMA. MUNICIPIO consulta el directorio operativo mínimo mediante `GET /api/catalogos/fiscalizadores`, autorizado con `FISCALIZADORES_DISPONIBLES_VER`.

## API principal

- `POST /api/auth/login`
- `GET /api/solicitudes`, `GET /api/solicitudes/{codigo}`
- `POST /api/solicitudes`, `PUT /api/solicitudes/{codigo}`
- `PATCH /api/solicitudes/{codigo}/estado`, `DELETE /api/solicitudes/{codigo}`
- `GET /api/solicitudes/{codigo}/documento`
- `GET /api/catalogos/fiscalizadores`
- `GET /api/reniec/{dni}` consulta el proveedor RENIEC configurado en el backend y devuelve únicamente el DTO usado por el formulario.
- `POST /api/predios`

La autorización de API usa capacidades verificadas en backend. `MUNICIPIO` conserva el flujo actual de solicitudes y programación; `FISCALIZADOR` accede a sus propias tareas y resultados; `ADMIN_SISTEMA` gestiona usuarios y la asignación de capacidades a roles, sin permisos operativos por defecto.

## Pruebas

```bash
npm run build
npm test -- --watch=false --no-progress
cd backend && mvn test && mvn clean package
cd .. && docker compose config
./scripts/smoke-test.sh
```

Las migraciones están en `backend/src/main/resources/db/migration/`. No se usa `ddl-auto=create`: Hibernate valida el esquema de Flyway.

## Nota funcional

Los PDF se guardan en `backend/uploads/solicitudes/` al ejecutar la API desde `backend` (configurable con `APP_STORAGE_PATH`), con nombre interno UUID y metadata en BD. `GET /api/solicitudes/{codigo}/documento` entrega el archivo autenticado con `Content-Disposition: inline`.

### RENIEC

La integración externa se encuentra separada en `ReniecService`, `IdentityProvider` y `ReniecIdentityProvider`. Se configura mediante `RENIEC_BASE_URL` y las credenciales que entregue el proveedor oficial (`RENIEC_CLIENT_ID`, `RENIEC_CLIENT_SECRET` o `RENIEC_API_KEY`). No hay credenciales incluidas en el repositorio. Si falta `RENIEC_BASE_URL`, el endpoint devuelve `503` con `Consulta RENIEC no configurada.`. Los timeouts por defecto son 2 segundos de conexión y 4 segundos de lectura.

El smoke test usa las credenciales demo de desarrollo, crea una solicitud temporal con `backend/src/test/resources/fixture-minimal.pdf`, verifica estado y documento, y elimina esa solicitud al finalizar. Puede apuntarse a otra API con `API_URL`.
# Configuración administrativa dinámica

La migración V21 crea ajustes individuales, el límite orientativo de la agenda y la configuración DNI. Configure `IDENTITY_MASTER_KEY` fuera del repositorio con 32 bytes aleatorios codificados en Base64 antes de guardar credenciales desde `/admin`. Conserve esa clave de forma segura: es necesaria para descifrar las credenciales tras reiniciar. Para RENIEC, configure además `RENIEC_ALLOWED_HOSTS` como lista de dominios HTTPS autorizados por el contrato de la entidad, separados por comas. El panel acepta la URL base del servicio documentado por la entidad y sus credenciales; no presupone un contrato oficial único.

La opción PerúAPI utiliza expresamente **peruapi.com** y su endpoint `https://peruapi.com/api/dni/{dni}` con la cabecera `X-API-KEY`. No utiliza peruapi.net. El límite diario inicial es 5 y solo informa de la carga; la programación mantiene las reglas de conflictos existentes.
