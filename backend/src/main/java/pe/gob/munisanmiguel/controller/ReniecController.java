package pe.gob.munisanmiguel.controller;

import org.springframework.web.bind.annotation.*;
import pe.gob.munisanmiguel.dto.ApiDtos.ReniecResponse;
import pe.gob.munisanmiguel.service.ReniecService;

@RestController
@RequestMapping("/api/reniec")
public class ReniecController {
    private final ReniecService service;
    public ReniecController(ReniecService service) { this.service = service; }

    @GetMapping("/{dni}")
    public ReniecResponse consultar(@PathVariable String dni) { return service.consultar(dni); }
}
