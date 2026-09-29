package pe.gob.munisanmiguel.controller;
import jakarta.persistence.*; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.web.bind.annotation.*; import org.springframework.security.access.prepost.PreAuthorize; import pe.gob.munisanmiguel.repository.PredioRepository; import java.math.BigDecimal;
@RestController @RequestMapping("/api/predios") public class PredioController {
  private final PredioRepository repo; public PredioController(PredioRepository repo){this.repo=repo;}
  public record Request(@NotBlank @Size(max=250) String direccion,@Size(max=250) String referencia,@NotBlank @Size(max=40) String tipoVivienda,@NotBlank @Size(max=80) String codigoPredial,@DecimalMin("-90") @DecimalMax("90") BigDecimal latitud,@DecimalMin("-180") @DecimalMax("180") BigDecimal longitud){}
  public record Response(Long id,String direccion,String referencia,String tipoVivienda,String codigoPredial,BigDecimal latitud,BigDecimal longitud){}
  @PostMapping @PreAuthorize("hasRole('MUNICIPIO')") @ResponseStatus(org.springframework.http.HttpStatus.CREATED) public Response crear(@Valid @RequestBody Request r){var p=new Predio();p.direccion=r.direccion().trim();p.referencia=r.referencia();p.tipoVivienda=r.tipoVivienda().trim();p.codigoPredial=r.codigoPredial().trim();p.latitud=r.latitud();p.longitud=r.longitud();repo.save(p);return to(p);}
  private Response to(Predio p){return new Response(p.id,p.direccion,p.referencia,p.tipoVivienda,p.codigoPredial,p.latitud,p.longitud);}
  @Entity @Table(name="predio") public static class Predio { @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; String direccion; String referencia; @Column(name="tipo_vivienda") String tipoVivienda; @Column(name="codigo_predial") String codigoPredial; BigDecimal latitud; BigDecimal longitud; }
}
