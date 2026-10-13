# San Miguel | Sistema de Fiscalización Municipal

Sistema web de gestión de expedientes y fiscalización domiciliaria de la Municipalidad Distrital de San Miguel. Permite registrar expedientes, programar visitas, gestionar fiscalizadores, realizar diligencias de campo, adjuntar documentos, revisar resultados y conservar la trazabilidad de las actuaciones.

> **Estado del proyecto (13/10/2026):** código funcional preparado para revisión y despliegue controlado. El paquete productivo disponible se construyó para **contenedores Linux/AMD64**. La instalación en la infraestructura definitiva de la municipalidad y su compatibilidad con contenedores Windows **aún deben validarse**.
>
> **Regla de producción:** una base **nueva** debe iniciar con **un solo `ADMIN_SISTEMA`**, sin cuentas municipales ni fiscalizadoras, expedientes, visitas o adjuntos de demostración.

## Contenido

1. [Arquitectura y tecnologías](#arquitectura-y-tecnologías)
2. [Funciones y roles](#funciones-y-roles)
3. [Estructura del repositorio](#estructura-del-repositorio)
4. [Requisitos](#requisitos)
5. [Clonar y ejecutar en desarrollo](#clonar-y-ejecutar-en-desarrollo)
6. [Crear el primer administrador en desarrollo](#crear-el-primer-administrador-en-desarrollo)
7. [Operar el entorno de desarrollo](#operar-el-entorno-de-desarrollo)
8. [Instalación productiva y administrador inicial](#instalación-productiva-y-administrador-inicial)
9. [Compatibilidad con contenedores Windows](#compatibilidad-con-contenedores-windows)
10. [Base de datos, archivos y seguridad](#base-de-datos-archivos-y-seguridad)
11. [Diagnóstico y problemas frecuentes](#diagnóstico-y-problemas-frecuentes)
12. [Colaboración y documentación](#colaboración-y-documentación)

## Arquitectura y tecnologías

```text
Usuarios (PC / celular / navegador)
             |
         HTTPS (producción)
             |
       Nginx + Angular
             |
        API Spring Boot
             |
       MySQL + Flyway
             |
    Datos y documentos PDF
```

| Componente | Tecnología | Función |
| --- | --- | --- |
| Frontend | Angular 22, TypeScript, CSS | Interfaz municipal, fiscalizador y administración |
| Backend | Java **21**, Spring Boot 3.5.9, Maven | API, seguridad y reglas de negocio |
| Base de datos | MySQL 8.4 | Expedientes, usuarios, agendas e historiales |
| Migraciones | Flyway | Versionado controlado del esquema |
| Producción | Docker Compose + Nginx | Servicios, HTTPS y redes |
| Mapas | Leaflet / OpenStreetMap; enlaces de ruta a Google Maps | Ubicación y recorridos de fiscalización |

En desarrollo puede ejecutarse MySQL mediante Docker y Angular/Spring Boot directamente en la máquina. En producción, las imágenes incluyen sus entornos de ejecución: no se necesita instalar Java ni Node.js en el servidor para ejecutar esos contenedores.

## Funciones y roles

- **`ADMIN_SISTEMA`**: administra cuentas, configuraciones e integraciones, y asigna los fiscalizadores a los usuarios municipales responsables. Los permisos administrativos no implican permiso automático para efectuar diligencias.
- **`MUNICIPIO`**: gestiona los expedientes y visitas que le corresponden, programa fiscalizaciones y revisa actuaciones y resultados según sus capacidades.
- **`FISCALIZADOR`**: accede a sus propias visitas y expedientes operativos, mapas, diligencias, evidencias, actas e historial.

El sistema incluye alta y consulta de expedientes, registro de administrados, carga de documentos, ubicación del predio, calendario con ocupación, asignación y reprogramación, resultados de visita, revisión municipal, aclaraciones, seguimiento, conformidad y cierre. La consulta DNI se configura desde administración (p. ej., Factiliza) y **requiere un proveedor/credenciales válidos**; sin ellos, se utiliza el flujo manual permitido por la aplicación.

Rutas principales (sujetas a autenticación y autorización):

| Ruta | Uso |
| --- | --- |
| `/admin` | Administración de usuarios y configuración |
| `/expedientes` | Fiscalizaciones municipales |
| `/expedientes/nuevo` | Crear fiscalización |
| `/revisiones` | Pendientes de revisión |
| `/fiscalizador` | Portal de fiscalizador |
| `/fiscalizador/proximas` | Visitas futuras |

La página `/inicio` ya no es el destino principal: el sistema redirige según el rol y los permisos.

## Estructura del repositorio

```text
San_miguel/
├── src/                           # Aplicación Angular
├── backend/
│   ├── src/main/java/             # Controladores, servicios, entidades, seguridad
│   ├── src/main/resources/         # Configuraciones y migraciones Flyway
│   ├── src/test/                   # Pruebas de backend
│   ├── scripts/                    # Herramientas para desarrollo y QA
│   ├── Dockerfile
│   └── pom.xml
├── docker/                        # Configuraciones Nginx
├── ops/                           # Aprovisionamiento y operación productiva
├── docs/                          # Documentación y evidencias técnicas
├── docker-compose.yml             # MySQL local para desarrollo
├── compose.manual.yml             # Entorno integrado de revisión (no instalación limpia)
├── compose.prod.yml               # Base de configuración productiva
├── compose.release.yml            # Despliegue de imágenes de una versión preparada
├── compose.qa.yml                 # Ajustes de QA
├── .env.example                   # Ejemplo para desarrollo
├── .env.prod.example              # Ejemplo productivo: nunca contiene secretos reales
└── README.md
```

Los archivos `.local/`, `.env` privados, respaldos, volúmenes, credenciales e imágenes empaquetadas **no se suben a Git**. Un clon del repositorio **no incluye** las imágenes ni los secretos del paquete de producción.

## Requisitos

**Desarrollo (Linux, macOS o Windows con herramientas equivalentes):**

- Git.
- Java **21** y Maven 3.9 o superior.
- Node.js 24 y npm 11 (versiones usadas en el proyecto).
- Docker Engine + plugin Docker Compose v2, o Docker Desktop en un sistema de escritorio compatible, para MySQL.
- Puertos locales `4200` (Angular), `8080` (API) y `3307` (MySQL) disponibles, o configuraciones equivalentes.

**Servidor productivo basado en la entrega preparada:** Docker Engine y Compose v2 que ejecuten imágenes **`linux/amd64`**, Python 3.12+, OpenSSL, GnuPG, DNS/IP confirmados, HTTPS confiable, volúmenes persistentes y un destino de respaldos fuera del servidor. La guía operacional recomienda como punto de partida orientativo 4 CPU y 8 GB RAM; el dimensionamiento real depende de la municipalidad.

## Clonar y ejecutar en desarrollo

> Este procedimiento es para una **base local de desarrollo**; las migraciones ordinarias pueden cargar datos de demostración. **No produce una instalación limpia de producción.** No se debe usar contra una base existente de la municipalidad.

```bash
git clone --branch willian/funcional-san-miguel \
  https://github.com/Will-M7/Muni_Random.git
cd Muni_Random
```

**1. Crear configuración local** (los valores de ejemplo de desarrollo no son válidos para producción):

```bash
cp .env.example .env
```

Revisar `.env` y las opciones de `docker-compose.yml` antes de continuar. Docker Compose utiliza estas variables para crear la base `san_miguel` local y publicar MySQL en `3307` por defecto.

**2. Iniciar únicamente MySQL:**

```bash
docker compose -f docker-compose.yml up -d mysql
docker compose -f docker-compose.yml ps
```

Esperar a que MySQL figure como `healthy` antes de iniciar Spring Boot. Si es una base previamente utilizada, **no** borrar volúmenes para forzar un arranque nuevo.

**3. Configurar Java 21 e iniciar la API** (otra terminal):

```bash
cd Muni_Random/backend  # ajustar si ya estás dentro de esa carpeta
java -version
mvn -version
```

Ambos deben indicar Java 21. Si en Ubuntu hay varias versiones instaladas, puede seleccionarse solo en esta terminal:

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH="$JAVA_HOME/bin:$PATH"
```

Después de configurar el administrador (siguiente sección), ejecutar desde `backend/`:

```bash
mvn spring-boot:run
```

**4. Iniciar Angular** (otra terminal, desde la raíz del repositorio):

```bash
npm ci
npm start
```

Abrir `http://localhost:4200`. En este modo de desarrollo la API escucha normalmente en `http://localhost:8080` y la conexión MySQL utiliza `localhost:3307`. En producción la API se publica mediante Nginx y rutas del mismo origen; no se debe llevar la URL `localhost:8080` a clientes externos.

## Crear el primer administrador en desarrollo

El código del backend utiliza `BootstrapAdmin`. Si se proporcionan las variables `ADMIN_BOOTSTRAP_USERNAME` y `ADMIN_BOOTSTRAP_PASSWORD`, crea un usuario `ADMIN_SISTEMA` con contraseña cifrada mediante BCrypt **si no existe ya ese nombre de usuario**. El servicio no sobrescribe automáticamente la contraseña de una cuenta existente.

> Para un clon recién preparado, esta es la vía más directa. Se **distingue del bootstrap productivo**, que requiere controles más estrictos y una base nueva.

En la **misma terminal en que se ejecutará Maven**, desde `backend/`:

```bash
export ADMIN_BOOTSTRAP_USERNAME=admin-local
read -r -s -p "Contraseña nueva para el administrador: " ADMIN_BOOTSTRAP_PASSWORD
echo
export ADMIN_BOOTSTRAP_PASSWORD

mvn spring-boot:run
```

- Elegir una contraseña nueva y robusta; no escribirla directamente en el comando ni publicarla en el repositorio.
- No reutilizar las contraseñas de cuentas de demostración.
- Una vez que Spring Boot arranque correctamente, ingresar a `http://localhost:4200/admin` con el usuario elegido.
- Desde administración pueden crearse cuentas `MUNICIPIO` y `FISCALIZADOR` para el entorno propio.
- Si el nombre de usuario ya existe, el bootstrap **no** lo modifica. No borrar bases ni volúmenes para recuperar el acceso.
- La base de desarrollo puede conservar cuentas semilla; por ello no debe suponerse que hay un único usuario tras este procedimiento.

Para que la integración de identidad pueda guardar credenciales cifradas en desarrollo, también es necesario configurar y conservar `IDENTITY_MASTER_KEY` en un archivo privado cargado por el arranque. No confundirla con la contraseña del administrador. No reemplazar una clave existente si ya hay credenciales cifradas con ella.

## Operar el entorno de desarrollo

```bash
# Ver MySQL
docker compose -f docker-compose.yml ps

# Ver logs de MySQL
docker compose -f docker-compose.yml logs --tail 80 mysql

# Detener MySQL sin eliminar datos
docker compose -f docker-compose.yml stop mysql

# Volver a iniciarlo
docker compose -f docker-compose.yml up -d mysql

# Comprobar la API, si está ejecutándose
curl http://localhost:8080/actuator/health
```

Para detener Maven o Angular iniciados en terminal, usar `Ctrl+C` en cada terminal. El script `backend/scripts/start-integrated-local.sh` y `compose.manual.yml` sirven al entorno integrado de revisión preparado para el equipo; **no son un sustituto universal del aprovisionamiento de un clon recién creado** y pueden depender de configuraciones, bases y secretos locales inexistentes en otra computadora.

## Instalación productiva y administrador inicial

**La instalación productiva no consiste en ejecutar `npm start` ni `mvn spring-boot:run` en el servidor.** Se utilizan las imágenes Docker y el procedimiento documentado en [`ops/OPERACION.md`](ops/OPERACION.md).

La entrega existente dispone de un paquete versionado `san-miguel-intranet-20261013.tar.gz` y archivos de integridad. Se guarda **fuera del repositorio**, dentro de `.local/releases/` en la computadora donde se preparó. Para instalarlo en otro equipo, debe transferirse por un medio autorizado; **clonar Git no descarga ese paquete ni sus imágenes**.

### Secuencia de producción (en servidor Linux/AMD64 compatible)

1. El ingeniero confirma servidor, arquitectura, IP, DNS, certificado HTTPS, puertos autorizados y respaldos.
2. Se transfiere el paquete y se comprueban las huellas SHA-256 y el contenido de entrega.
3. Se cargan las imágenes Docker de la versión elegida siguiendo la guía operativa.
4. **En el servidor**, generar secretos nuevos mediante un procedimiento interactivo:

   ```bash
   sudo python3 ops/production.py init-secrets --directory /etc/san-miguel/secrets
   ```

   El procedimiento productivo exige una contraseña nueva para el administrador (mínimo **20 caracteres**, máximo 72 bytes por BCrypt) y genera las claves de MySQL, JWT, cifrado de identidad y respaldos. **No llevar contraseñas del equipo de desarrollo.**

5. Copiar el ejemplo `.env.prod.example` a `/etc/san-miguel/production.env`, establecer permisos `0600` y completar los valores reales, incluidas rutas a secretos y certificados. No editar ni publicar los secretos dentro de Git.
6. Desde el **directorio de la versión productiva extraída** ejecutar, en orden:

   ```bash
   sudo python3 ops/production.py check --env /etc/san-miguel/production.env
   sudo python3 ops/production.py initialize --env /etc/san-miguel/production.env
   sudo python3 ops/production.py status --env /etc/san-miguel/production.env
   ```

   `initialize` está diseñado **únicamente para volúmenes nuevos**. Verifica las migraciones, la existencia de un único administrador inicial y la ausencia de registros operativos. **No ejecutar sobre bases de desarrollo ni una base previa de la municipalidad.**

7. Tras comprobar DNS, TLS, firewall, respaldos, estado de datos y contar con autorización del responsable, habilitar acceso:

   ```bash
   sudo python3 ops/production.py activate --env /etc/san-miguel/production.env
   ```

   Este paso **no debe ejecutarse sin autorización de activación**. El usuario `ADMIN_SISTEMA` es el configurado durante el aprovisionamiento y su contraseña es la nueva que se generó en el servidor.

### Estado inicial esperado

| Elemento | Cantidad |
| --- | ---: |
| Cuenta `ADMIN_SISTEMA` | 1 |
| Cuentas `MUNICIPIO` | 0 |
| Cuentas `FISCALIZADOR` | 0 |
| Fiscalizadores operativos | 0 |
| Expedientes, visitas, diligencias, evidencias | 0 |
| PDF y adjuntos de prueba | 0 |

Flyway mantiene el esquema y el catálogo necesario de roles/capacidades. La **V27** de producción reconoce y retira exclusivamente la semilla histórica de una instalación nueva y protegida; **V28 y V29** agregan restricciones/relaciones operativas posteriores. Según la validación documentada, se aplicaron **29 migraciones V1–V29** en el ensayo limpio. No copiar la base local ni lanzar V27 por separado sobre datos existentes.

### Operación y respaldos productivos

```bash
# Estado
sudo python3 ops/production.py status --env /etc/san-miguel/production.env

# Parada controlada
sudo python3 ops/production.py stop --env /etc/san-miguel/production.env

# Arranque de núcleo sin abrir todavía el acceso
sudo python3 ops/production.py start --env /etc/san-miguel/production.env
```

Los respaldos y la restauración utilizan herramientas documentadas en [`ops/OPERACION.md`](ops/OPERACION.md). **Nunca** usar `docker compose down -v`, `docker volume prune` ni eliminar volúmenes productivos como modo de reinicio. MySQL y los archivos PDF requieren respaldos conjuntos y un destino externo protegido. La clave maestra de identidad y los secretos productivos deben conservarse entre actualizaciones.

## Compatibilidad con contenedores Windows

La persona responsable de adaptar el despliegue **no debe modificar la lógica municipal** para intentar acomodar contenedores. Debe confirmar primero qué sistema de contenedores ejecuta el servidor:

```bash
docker version
docker compose version
docker info --format '{{.OSType}}/{{.Architecture}}'
```

| Resultado del motor | Consideración |
| --- | --- |
| `linux/amd64` | Arquitectura para la que está preparada la entrega actual |
| `linux/arm64` | Reconstruir y validar imágenes compatibles |
| `windows/amd64` | **No ejecuta directamente** las imágenes Linux actuales |

**Distinciones importantes:**

- Windows de escritorio con Docker Desktop en modo contenedores Linux puede servir para **desarrollo**, sujeto a requisitos de la plataforma.
- Un Windows Server que solo ejecuta **contenedores Windows** no podrá utilizar directamente las imágenes Linux de MySQL, Nginx y Java de este proyecto.
- Si la infraestructura lo permite, una **máquina virtual Linux compatible** administrada en el servidor Windows facilita reutilizar la entrega existente.
- Si la exigencia es **contenedores Windows nativos**, se necesita evaluar imágenes y métodos de alojamiento compatibles para cada servicio, especialmente MySQL y el servidor web, así como persistencia, seguridad y conectividad. **Esta variante todavía no ha sido implementada ni certificada.**

Para trabajar en paralelo sin alterar la versión funcional:

```bash
git switch -c infra/windows-containers
```

Documentar resultados en esa rama. No publicar una nueva imagen Windows ni afirmar su compatibilidad sin validación en el servidor de destino.

## Base de datos, archivos y seguridad

- **MySQL**: las migraciones ordinarias están bajo `backend/src/main/resources/db/migration/`; la migración exclusiva de producción está en `backend/src/main/resources/db/production/`. Hibernate usa validación de esquema, no creación automática.
- **Adjuntos**: los controladores del backend gestionan las descargas autenticadas. En el despliegue productivo se usa el almacenamiento persistente montado en `/app/uploads`; los datos del volumen **no** forman parte de Git.
- **Secretos**: no subir `.env` privados, `.local/`, tokens Factiliza, contraseñas, claves TLS, archivos `.pem`, respaldos ni bases SQL reales.
- **Red**: publicar únicamente el punto de entrada HTTPS autorizado; MySQL y backend no deben quedar expuestos a Internet. HTTPS confiable es importante para permisos de ubicación/GPS desde navegadores.
- **Identidad**: el proveedor DNI se configura desde administración y permanece deshabilitado hasta recibir credenciales legítimas; no existen tokens predeterminados para producción.
- **Persistencia**: mantener nombre de proyecto Compose y volúmenes entre reinicios y actualizaciones. Ver procedimientos de restauración antes de intervenir.

## Diagnóstico y problemas frecuentes

| Síntoma | Revisión inicial |
| --- | --- |
| `java -version` muestra 25 | Ejecutar Maven con JDK **21** y verificar `mvn -version` |
| MySQL no responde al iniciar | Revisar `docker compose ps`, esperar `healthy` y consultar logs |
| Puerto 3307, 8080 o 4200 ocupado | Verificar procesos/contenedores antes de cambiar puertos |
| Frontend carga, pero API falla | Revisar backend y URL del API en modo desarrollo; en producción, proxy Nginx `/api` |
| Falta `IDENTITY_MASTER_KEY` | Cargar la clave privada persistente correcta antes de guardar tokens; no reemplazar una existente |
| Flyway informa migración fallida | Detener el arranque y diagnosticar esquema/historial; **no** ejecutar `repair`, `clean` ni borrar tablas a ciegas |
| No se puede iniciar sesión como admin | Confirmar usuario y configuración bootstrap; un usuario existente no recibe contraseña nueva al reiniciar |
| GPS no disponible | Verificar HTTPS confiable, permisos de ubicación y configuración del dispositivo |
| Consulta DNI indisponible | Comprobar proveedor, token, conectividad y mensajes controlados; no suponer que un 503 es una respuesta de identidad |

Para investigar el estado técnico del proyecto sin alterar datos:

```bash
git status -sb
git branch --show-current
docker compose -f docker-compose.yml ps
```

## Colaboración y documentación

- **Repositorio:** <https://github.com/Will-M7/Muni_Random>
- **Rama con la versión funcional:** `willian/funcional-san-miguel`.
- **Adaptación de infraestructura Windows:** realizarla en una rama independiente, por ejemplo `infra/windows-containers`.
- **Manual completo de operación:** [`ops/OPERACION.md`](ops/OPERACION.md).
- **Informe de la preparación productiva:** [`ops/VALIDACION.md`](ops/VALIDACION.md).
- **Diagnóstico histórico de V20 y recuperación aislada:** [`docs/backend-v20-recuperacion.md`](docs/backend-v20-recuperacion.md).

**Antes de hacer `git push`:** verificar que los cambios locales funcionales y visuales estén incluidos, que los archivos compilados `__pycache__/` no estén preparados y que no haya secretos en el área de *staging*. El paquete productivo de `.local/releases/` se distribuye por separado; no debe convertirse en un archivo Git normal.

---

**Responsabilidad compartida:** quien despliega confirma infraestructura, certificados, seguridad y respaldos; los responsables del sistema realizan las pruebas funcionales y autorizan la activación municipal.
