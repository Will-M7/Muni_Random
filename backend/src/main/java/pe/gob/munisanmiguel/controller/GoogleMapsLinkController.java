package pe.gob.munisanmiguel.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

@RestController @RequestMapping("/api/ubicaciones/google")
public class GoogleMapsLinkController {
    private static final Set<String> HOSTS=Set.of("maps.app.goo.gl","goo.gl","google.com","www.google.com","maps.google.com","google.com.pe","www.google.com.pe");
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    public record LinkRequest(String url){}
    @PostMapping("/resolver") @PreAuthorize("hasAuthority('EXPEDIENTE_CREAR')")
    public Map<String,String> resolve(@RequestBody LinkRequest request){
        try{URI uri=validated(URI.create(request.url()));
            for(int i=0;i<4;i++){
                if(!Set.of("maps.app.goo.gl","goo.gl").contains(uri.getHost().toLowerCase()))return Map.of("url",uri.toString());
                var response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(4)).method("HEAD",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding());
                if(response.statusCode()<300||response.statusCode()>399)break;
                var location=response.headers().firstValue("Location").orElseThrow();uri=validated(uri.resolve(location));
            }
        }catch(IllegalArgumentException e){throw e;}
        catch(Exception e){throw new IllegalArgumentException("No se pudo verificar el enlace corto de Google Maps.");}
        throw new IllegalArgumentException("El enlace no contiene un punto verificable.");
    }
    private URI validated(URI uri){String host=uri.getHost();if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||!HOSTS.contains(host.toLowerCase())||uri.getUserInfo()!=null||uri.getPort()!=-1)
        throw new IllegalArgumentException("Usa un enlace HTTPS válido de Google Maps.");return uri;}
}
