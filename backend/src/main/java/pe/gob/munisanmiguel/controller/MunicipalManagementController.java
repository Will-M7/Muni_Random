package pe.gob.munisanmiguel.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import pe.gob.munisanmiguel.service.MunicipalManagementService;
import java.util.List;
@RestController @RequestMapping("/api/admin/fiscalizadores-gestion")
public class MunicipalManagementController {
    private final MunicipalManagementService service;public MunicipalManagementController(MunicipalManagementService service){this.service=service;}
    public record Request(String municipioUsername){}
    @GetMapping @PreAuthorize("hasAuthority('USUARIOS_VER')") public List<MunicipalManagementService.Assignment> list(Authentication auth){return service.list(auth.getName());}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('USUARIOS_EDITAR')") public MunicipalManagementService.Assignment assign(@PathVariable String id,@RequestBody Request request,Authentication auth){return service.assign(id,request.municipioUsername(),auth.getName());}
}
