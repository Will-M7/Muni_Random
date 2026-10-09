package pe.gob.munisanmiguel.service;

import org.springframework.web.client.RestClient;
import pe.gob.munisanmiguel.entity.IdentityIntegration;
import java.util.function.Function;

interface DniProviderAdapter {
    String provider();
    String url(IdentityIntegration config,String dni);
    void authorize(RestClient.RequestHeadersSpec<?> request,IdentityIntegration config,Function<String,String> decrypt);
}
