package pe.gob.munisanmiguel.service;

import org.springframework.web.client.RestClient;
import pe.gob.munisanmiguel.entity.IdentityIntegration;
import java.util.function.Function;

final class ReniecContractDniAdapter implements DniProviderAdapter {
    public String provider(){return "RENIEC";}
    public String url(IdentityIntegration config,String dni){return config.getEndpoint().contains("{dni}")?config.getEndpoint().replace("{dni}",dni):config.getEndpoint()+"/"+dni;}
    public void authorize(RestClient.RequestHeadersSpec<?> request,IdentityIntegration config,Function<String,String> decrypt){
        if(config.getEncryptedApiKey()!=null)request.header("X-API-KEY",decrypt.apply(config.getEncryptedApiKey()));
        if(config.getEncryptedClientId()!=null)request.header("X-Client-Id",decrypt.apply(config.getEncryptedClientId()));
        if(config.getEncryptedClientSecret()!=null)request.header("X-Client-Secret",decrypt.apply(config.getEncryptedClientSecret()));
    }
}
