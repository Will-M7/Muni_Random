package pe.gob.munisanmiguel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import pe.gob.munisanmiguel.dto.ApiDtos.ReniecResponse;
import pe.gob.munisanmiguel.entity.IdentityIntegration;
import pe.gob.munisanmiguel.exception.*;
import pe.gob.munisanmiguel.repository.IdentityIntegrationRepository;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service @Primary
public class IdentityIntegrationService implements IdentityProvider {
    public record Configuration(String provider,String endpoint,boolean credentialConfigured,String actualizadoPor,LocalDateTime actualizadoEn) {}
    public record Update(String provider,String endpoint,String apiKey,String clientId,String clientSecret) {}
    private final IdentityIntegrationRepository repository;
    private final AdminSettingsService admin;
    private final ObjectMapper mapper;
    private final RestClient client;
    private final String masterKey;
    private final Set<String> reniecHosts;
    private final Map<String,DniProviderAdapter> adapters=Map.of("RENIEC",new ReniecContractDniAdapter(),"PERUAPI_COM",new PeruApiComDniAdapter());
    private static final SecureRandom RANDOM=new SecureRandom();
    public IdentityIntegrationService(IdentityIntegrationRepository repository,AdminSettingsService admin,ObjectMapper mapper,
            @Value("${app.identity.master-key:}") String masterKey,@Value("${app.identity.reniec-allowed-hosts:}") String hosts){
        this.repository=repository;this.admin=admin;this.mapper=mapper;this.masterKey=masterKey;
        this.reniecHosts=new HashSet<>(Arrays.stream(hosts.split(",")).map(String::trim).map(String::toLowerCase).filter(s->!s.isBlank()).toList());
        var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory=new JdkClientHttpRequestFactory(http);factory.setReadTimeout(Duration.ofSeconds(4));
        this.client=RestClient.builder().requestFactory(factory).build();
    }
    @Transactional(readOnly=true) public Configuration configuration(String actor){admin.requireAdmin(actor);return view(current());}
    @Transactional(readOnly=true) public boolean available(){var c=current();return !"DISABLED".equals(c.getProvider())&&(c.getEncryptedApiKey()!=null||("RENIEC".equals(c.getProvider())&&c.getEncryptedClientSecret()!=null));}
    @Transactional public Configuration update(Update request,String actor){admin.requireAdmin(actor);
        if(request==null||request.provider()==null)throw new IllegalArgumentException("Proveedor DNI requerido.");
        var provider=request.provider().trim().toUpperCase(Locale.ROOT);
        if(!Set.of("DISABLED","RENIEC","PERUAPI_COM").contains(provider))throw new IllegalArgumentException("Proveedor DNI inválido.");
        var c=current();if(!provider.equals(c.getProvider())){c.setEncryptedApiKey(null);c.setEncryptedClientId(null);c.setEncryptedClientSecret(null);}
        if("DISABLED".equals(provider)){c.setEncryptedApiKey(null);c.setEncryptedClientId(null);c.setEncryptedClientSecret(null);}
        if("RENIEC".equals(provider))c.setEndpoint(validateReniecEndpoint(request.endpoint()));else c.setEndpoint(null);
        if(!"DISABLED".equals(provider)&&request.apiKey()!=null&&!request.apiKey().isBlank())c.setEncryptedApiKey(encrypt(request.apiKey().trim()));
        if("RENIEC".equals(provider)&&request.clientId()!=null&&!request.clientId().isBlank())c.setEncryptedClientId(encrypt(request.clientId().trim()));
        if("RENIEC".equals(provider)&&request.clientSecret()!=null&&!request.clientSecret().isBlank())c.setEncryptedClientSecret(encrypt(request.clientSecret().trim()));
        c.setProvider(provider);c.setActualizadoPor(actor);c.setActualizadoEn(LocalDateTime.now(ZoneId.of("America/Lima")));
        return view(repository.save(c));
    }
    @Transactional(readOnly=true) public String testConnection(String actor){admin.requireAdmin(actor);var c=current();if(!available())throw new ReniecNotConfiguredException();
        if("PERUAPI_COM".equals(c.getProvider()))return "PerúAPI.com no ofrece una prueba sin consultar un DNI; guarda la configuración y realiza una consulta autorizada.";
        return "RENIEC: usa un DNI de prueba autorizado por la entidad para verificar su endpoint contratado.";
    }
    @Override @Transactional(readOnly=true) public ReniecResponse findByDni(String dni){if(dni==null||!dni.matches("\\d{8}"))throw new IllegalArgumentException("El DNI debe tener ocho dígitos.");
        var c=current();if("DISABLED".equals(c.getProvider())||(c.getEncryptedApiKey()==null&&c.getEncryptedClientSecret()==null))throw new ReniecNotConfiguredException();
        var adapter=adapters.get(c.getProvider());if(adapter==null)throw new ReniecNotConfiguredException();
        try{var request=client.get().uri(adapter.url(c,dni));adapter.authorize(request,c,this::decrypt);
            String body=request.retrieve().onStatus(HttpStatusCode::is4xxClientError,(req,res)->{
                if(res.getStatusCode().value()==404)throw new NotFoundException("DNI no encontrado.");
                throw new ReniecUnavailableException("El proveedor DNI rechazó la consulta.");
            }).onStatus(HttpStatusCode::is5xxServerError,(req,res)->{throw new ReniecUnavailableException("El proveedor DNI no está disponible.");}).body(String.class);
            return mapResponse(mapper.readTree(body),dni);
        }catch(NotFoundException|ReniecUnavailableException e){throw e;}
        catch(Exception e){throw new ReniecUnavailableException("No se pudo consultar el proveedor DNI.",e);}
    }
    ReniecResponse mapResponse(JsonNode root,String dni){JsonNode data=root.has("data")&&root.get("data").isObject()?root.get("data"):root;
        String names=field(data,"nombres","nombre","firstName");String paternal=field(data,"apellido_paterno","apellidoPaterno","lastName");String maternal=field(data,"apellido_materno","apellidoMaterno","secondLastName");
        if(names.isBlank()||paternal.isBlank())throw new NotFoundException("DNI sin datos utilizables.");
        return new ReniecResponse(dni,names,paternal,maternal,null,null);
    }
    private String field(JsonNode data,String... keys){for(String key:keys)if(data.hasNonNull(key))return data.get(key).asText("").trim();return "";}
    private IdentityIntegration current(){return repository.findById((byte)1).orElseGet(()->{var c=new IdentityIntegration();c.setId((byte)1);return c;});}
    private Configuration view(IdentityIntegration c){return new Configuration(c.getProvider(),c.getEndpoint(),c.getEncryptedApiKey()!=null||c.getEncryptedClientSecret()!=null,c.getActualizadoPor(),c.getActualizadoEn());}
    private String validateReniecEndpoint(String value){try{String raw=value==null?"":value.trim();if(raw.indexOf('{')>=0&&!raw.contains("{dni}"))throw new IllegalArgumentException();var uri=URI.create(raw.replace("{dni}","00000000"));var host=uri.getHost();
        if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||!reniecHosts.contains(host.toLowerCase(Locale.ROOT))||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getQuery()!=null||uri.getFragment()!=null)
            throw new IllegalArgumentException("El endpoint RENIEC debe ser HTTPS y usar un dominio autorizado en RENIEC_ALLOWED_HOSTS.");
        return raw.replaceAll("/$","");
    }catch(IllegalArgumentException e){throw new IllegalArgumentException("Endpoint RENIEC inválido o no autorizado.");}}
    private SecretKeySpec key(){try{byte[] raw=Base64.getDecoder().decode(masterKey);if(raw.length!=32)throw new IllegalStateException();return new SecretKeySpec(raw,"AES");}
        catch(Exception e){throw new ConflictException("Configura IDENTITY_MASTER_KEY con 32 bytes en Base64 antes de guardar credenciales.");}}
    private String encrypt(String value){try{byte[] iv=new byte[12];RANDOM.nextBytes(iv);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,iv));byte[] bytes=cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));byte[] all=new byte[iv.length+bytes.length];System.arraycopy(iv,0,all,0,iv.length);System.arraycopy(bytes,0,all,iv.length,bytes.length);return Base64.getEncoder().encodeToString(all);}catch(Exception e){throw new IllegalStateException("No se pudo cifrar la credencial.",e);}}
    private String decrypt(String value){try{byte[] all=Base64.getDecoder().decode(value);byte[] iv=Arrays.copyOfRange(all,0,12);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));return new String(cipher.doFinal(all,12,all.length-12),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException("No se pudo leer la credencial cifrada.",e);}}
}
