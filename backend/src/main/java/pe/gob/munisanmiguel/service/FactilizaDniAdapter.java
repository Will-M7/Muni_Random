package pe.gob.munisanmiguel.service;

import org.springframework.web.client.RestClient;
import pe.gob.munisanmiguel.entity.IdentityIntegration;
import java.util.function.Function;

final class FactilizaDniAdapter implements DniProviderAdapter {
    public String provider(){return "FACTILIZA";}
    public String url(IdentityIntegration config,String dni){return "https://api.factiliza.com/v1/dni/info/"+dni;}
    public void authorize(RestClient.RequestHeadersSpec<?> request,IdentityIntegration config,Function<String,String> decrypt){
        request.headers(headers->headers.setBearerAuth(decrypt.apply(config.getEncryptedFactilizaToken())));
    }
}
