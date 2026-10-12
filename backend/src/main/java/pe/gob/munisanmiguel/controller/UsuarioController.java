package pe.gob.munisanmiguel.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.gob.munisanmiguel.dto.ApiDtos.UsuarioRequest;
import pe.gob.munisanmiguel.dto.ApiDtos.UsuarioResponse;
import pe.gob.munisanmiguel.dto.ApiDtos.FiscalizadorDisponibleResponse;
import pe.gob.munisanmiguel.service.UsuarioService;
import java.util.List;

@RestController @RequestMapping("/api/usuarios")
public class UsuarioController {
    private final UsuarioService service;
    private final pe.gob.munisanmiguel.service.AdminSettingsService settings;
    public UsuarioController(UsuarioService service,pe.gob.munisanmiguel.service.AdminSettingsService settings) { this.service = service;this.settings=settings; }
    @GetMapping @PreAuthorize("hasAuthority('USUARIOS_VER')") public List<UsuarioResponse> listar(@RequestParam(defaultValue="activos") String filtro,org.springframework.security.core.Authentication auth) { settings.requireAdmin(auth.getName());return service.listar(filtro); }
    @GetMapping("/fiscalizadores-sin-cuenta") @PreAuthorize("hasAuthority('USUARIOS_CREAR')") public List<FiscalizadorDisponibleResponse> fiscalizadoresSinCuenta(org.springframework.security.core.Authentication auth) { settings.requireAdmin(auth.getName());return service.fiscalizadoresSinCuenta(); }
    @PostMapping @PreAuthorize("hasAuthority('USUARIOS_CREAR')") @ResponseStatus(HttpStatus.CREATED) public UsuarioResponse crear(@Valid @RequestBody UsuarioRequest request,org.springframework.security.core.Authentication auth) { settings.requireAdmin(auth.getName());return service.crear(request); }
    @PatchMapping("/{username}/estado") @PreAuthorize("hasAuthority('USUARIOS_ESTADO')") public UsuarioResponse estado(@PathVariable String username, @Valid @RequestBody pe.gob.munisanmiguel.dto.ApiDtos.UserActiveRequest request,org.springframework.security.core.Authentication auth) { settings.requireAdmin(auth.getName());return service.cambiarEstado(username, request.activo()); }
    @PutMapping("/{username}/perfil") @PreAuthorize("hasAuthority('USUARIOS_EDITAR')") public UsuarioResponse editar(@PathVariable String username,@Valid @RequestBody pe.gob.munisanmiguel.dto.ApiDtos.UsuarioEdicionRequest request,org.springframework.security.core.Authentication auth){settings.requireAdmin(auth.getName());return service.editar(username,request);}
    @GetMapping("/{username}/eliminacion") @PreAuthorize("hasAuthority('USUARIOS_EDITAR')") public pe.gob.munisanmiguel.dto.ApiDtos.UsuarioEliminacionPreview previsualizar(@PathVariable String username,org.springframework.security.core.Authentication auth){settings.requireAdmin(auth.getName());return service.previsualizarEliminacion(username);}
    @DeleteMapping("/cuentas/{username}") @PreAuthorize("hasAuthority('USUARIOS_EDITAR')") public pe.gob.munisanmiguel.dto.ApiDtos.UsuarioEliminacionResponse eliminar(@PathVariable String username,org.springframework.security.core.Authentication auth){settings.requireAdmin(auth.getName());return service.eliminarOArchivar(username,auth.getName());}
    @PatchMapping("/{username}/archivo") @PreAuthorize("hasAuthority('USUARIOS_EDITAR')") public UsuarioResponse archivar(@PathVariable String username,@Valid @RequestBody pe.gob.munisanmiguel.dto.ApiDtos.UsuarioArchivadoRequest request,org.springframework.security.core.Authentication auth){settings.requireAdmin(auth.getName());if(request.archivado())throw new pe.gob.munisanmiguel.exception.ConflictException("El archivo se gestiona mediante la operación protegida de eliminación.");return service.restaurar(username,false);}
    @PostMapping("/eliminacion-lote") @PreAuthorize("hasAuthority('USUARIOS_EDITAR')") public pe.gob.munisanmiguel.dto.ApiDtos.UsuarioEliminacionLoteResponse eliminarLote(@Valid @RequestBody pe.gob.munisanmiguel.dto.ApiDtos.UsuarioEliminacionLoteRequest request,org.springframework.security.core.Authentication auth){settings.requireAdmin(auth.getName());var resultados=request.usernames().stream().distinct().map(username->{try{return service.eliminarOArchivar(username,auth.getName());}catch(RuntimeException e){return new pe.gob.munisanmiguel.dto.ApiDtos.UsuarioEliminacionResponse(username,"ERROR",List.of(),e.getMessage()==null?"No se pudo procesar la cuenta.":e.getMessage());}}).toList();return new pe.gob.munisanmiguel.dto.ApiDtos.UsuarioEliminacionLoteResponse(resultados);}
    @PutMapping("/{username}/roles") @PreAuthorize("hasAuthority('USUARIOS_EDITAR') or hasAuthority('ROLES_GESTIONAR')") public UsuarioResponse roles(@PathVariable String username, @Valid @RequestBody pe.gob.munisanmiguel.dto.ApiDtos.UserRolesRequest request,org.springframework.security.core.Authentication auth) { settings.requireAdmin(auth.getName());return service.asignarRoles(username, request.roles(),request.fiscalizadorId()); }
}
