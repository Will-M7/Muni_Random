package pe.gob.munisanmiguel.service;

import org.springframework.web.client.RestClient;
import pe.gob.munisanmiguel.entity.IdentityIntegration;
import java.util.function.Function;

final class PeruApiComDniAdapter implements DniProviderAdapter {
    public String provider(){return "PERUAPI_COM";}
    public String url(IdentityIntegration config,String dni){return "https://peruapi.com/api/dni/"+dni;}
    public void authorize(RestClient.RequestHeadersSpec<?> request,IdentityIntegration config,Function<String,String> decrypt){
        request.header("X-API-KEY",decrypt.apply(config.getEncryptedApiKey()));
    }
}
