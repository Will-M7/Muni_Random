package pe.gob.munisanmiguel.controller;
import pe.gob.munisanmiguel.dto.ApiDtos.*; import pe.gob.munisanmiguel.service.SolicitudService; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api/catalogos") public class CatalogoController { private final SolicitudService service; public CatalogoController(SolicitudService s){service=s;} @GetMapping("/fiscalizadores") public List<FiscalizadorResponse> fiscalizadores(){return service.fiscalizadores();} }
