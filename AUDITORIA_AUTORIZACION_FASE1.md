# Auditoría previa: usuarios y autorización

- `AppUser` almacena un único `Rol` enum (`MUNICIPIO` o `FISCALIZADOR`) en `app_user.role`; `ADMIN_SISTEMA` aún no existe. Los usuarios tienen contraseña BCrypt, estado activo y vínculo opcional a `Fiscalizador`.
- El login valida usuario, contraseña y opcionalmente el rol solicitado. El JWT almacena `role` y `fiscalizadorId`; el filtro crea solo la autoridad `ROLE_<rol>` desde el token.
- `SecurityConfig` combina reglas URL por rol y anotaciones `@PreAuthorize`. Solicitudes de lectura y RENIEC se restringen a MUNICIPIO, la API fiscalizador a FISCALIZADOR y `/api/usuarios/**` a MUNICIPIO. El resto de rutas autenticadas no tiene política de capacidad específica.
- `PATCH /api/solicitudes/{codigo}/estado` autorizaba MUNICIPIO y FISCALIZADOR tanto en la regla HTTP como en `@PreAuthorize`; no verificaba ownership. Esto permitía a un fiscalizador cambiar un estado administrativo.
- Angular guarda el rol de la sesión y usa `roleGuard`, con comparaciones por rol en rutas y menú. `MUNICIPIO` ve `/usuarios`; `FISCALIZADOR` usa su portal; ADMIN no está modelado y la ruta por defecto lleva a login.
- Flyway llega a V10. Esta fase agregará el catálogo de roles/capacidades y sus asignaciones en V11, copiando el rol actual de cada usuario antes de empezar a usar las relaciones nuevas. No se modifica V1–V10.

## Diseño aplicado

`app_user` conservará sus datos y columna histórica `role` para compatibilidad. Las relaciones efectivas vivirán en `user_role`, `app_role` y `role_capability`; las peticiones JWT resolverán las autoridades actuales desde esas relaciones. El acceso se expresará mediante capacidades Spring Security y no por el rol histórico.

El esquema original no contenía un usuario ADMIN_SISTEMA. Para provisionarlo sin introducir contraseñas predeterminadas, el arranque crea uno solo si se configuran `ADMIN_BOOTSTRAP_USERNAME` y `ADMIN_BOOTSTRAP_PASSWORD` (mínimo 12 caracteres) y ese username aún no existe. La contraseña se guarda con BCrypt. Si no se configuran esas variables, los usuarios existentes no se modifican.

## Corrección posterior a Fase 1

V12 revoca `USUARIOS_VER` y `USUARIOS_CREAR` de MUNICIPIO y le asigna `FISCALIZADORES_DISPONIBLES_VER`. El directorio está en `/api/catalogos/fiscalizadores` y entrega `id`, `nombre`, `zona` y `activo`; ya no comparte rutas de administración de usuarios. Los permisos administrativos continúan solo en ADMIN_SISTEMA.

`app_user.role` queda solo como columna histórica escrita para compatibilidad. No se emite en JWT ni se usa para generar autoridades, guards, navegación o redirecciones; estas decisiones consultan capacidades efectivas de `user_role` y `role_capability`.
