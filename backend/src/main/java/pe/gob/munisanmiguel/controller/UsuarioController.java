package pe.gob.munisanmiguel.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.gob.munisanmiguel.dto.ApiDtos.UsuarioRequest;
import pe.gob.munisanmiguel.dto.ApiDtos.UsuarioResponse;
import pe.gob.munisanmiguel.service.UsuarioService;
import java.util.List;

@RestController @RequestMapping("/api/usuarios") @PreAuthorize("hasRole('MUNICIPIO')")
public class UsuarioController {
    private final UsuarioService service;
    public UsuarioController(UsuarioService service) { this.service = service; }
    @GetMapping public List<UsuarioResponse> listar() { return service.listar(); }
    @GetMapping("/fiscalizadores") public List<pe.gob.munisanmiguel.dto.ApiDtos.FiscalizadorResponse> fiscalizadores() { return service.fiscalizadores(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public UsuarioResponse crear(@Valid @RequestBody UsuarioRequest request) { return service.crear(request); }
}
