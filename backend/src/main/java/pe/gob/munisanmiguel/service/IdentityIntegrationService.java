package pe.gob.munisanmiguel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.text.Normalizer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service @Primary
public class IdentityIntegrationService implements IdentityProvider {
    public record Configuration(String provider,String endpoint,boolean credentialConfigured,String actualizadoPor,LocalDateTime actualizadoEn) {}
    public record Update(String provider,String endpoint,String apiKey,String clientId,String clientSecret) {}
    public record DniTestResult(String dni,String nombres,String apellidoPaterno,String apellidoMaterno,String provider,String message) {}
    public record DniQuotaStatus(boolean applies,String provider,LocalDate date,int used,int limit,int remaining,boolean exhausted,OffsetDateTime resetsAt,String zone) {}
    private final IdentityIntegrationRepository repository;
    private final AdminSettingsService admin;
    private final ObjectMapper mapper;
    private final RestClient client;
    private final String masterKey;
    private final DniDailyQuotaService quota;
    private final Set<String> reniecHosts;
    private final Map<String,java.time.Instant> lastTestByAdmin=new ConcurrentHashMap<>();
    private final Map<String,DniProviderAdapter> adapters=Map.of("RENIEC",new ReniecContractDniAdapter(),"PERUAPI_COM",new PeruApiComDniAdapter(),"FACTILIZA",new FactilizaDniAdapter());
    private static final SecureRandom RANDOM=new SecureRandom();
    @Autowired public IdentityIntegrationService(IdentityIntegrationRepository repository,AdminSettingsService admin,ObjectMapper mapper,
            DniDailyQuotaService quota,@Value("${app.identity.master-key:}") String masterKey,@Value("${app.identity.reniec-allowed-hosts:}") String hosts){
        this(repository,admin,mapper,quota,masterKey,hosts,buildClient());
    }
    IdentityIntegrationService(IdentityIntegrationRepository repository,AdminSettingsService admin,ObjectMapper mapper,DniDailyQuotaService quota,String masterKey,String hosts,RestClient client){
        this.repository=repository;this.admin=admin;this.mapper=mapper;this.quota=quota;this.masterKey=masterKey;
        this.reniecHosts=new HashSet<>(Arrays.stream(hosts.split(",")).map(String::trim).map(String::toLowerCase).filter(s->!s.isBlank()).toList());
        this.client=client;
    }
    private static RestClient buildClient(){
        var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory=new JdkClientHttpRequestFactory(http);factory.setReadTimeout(Duration.ofSeconds(4));
        return RestClient.builder().requestFactory(factory).build();
    }
    @Transactional(readOnly=true) public Configuration configuration(String actor){admin.requireAdmin(actor);return view(current());}
    @Transactional(readOnly=true) public DniQuotaStatus quotaStatus(){var today=LocalDate.now(ZoneId.of("America/Lima"));var configuration=current();boolean applies="PERUAPI_COM".equals(configuration.getProvider());int used=applies?quota.used(today):0;int limit=applies?DniDailyQuotaService.LIMIT:0;return new DniQuotaStatus(applies,configuration.getProvider(),today,used,limit,applies?Math.max(0,limit-used):0,applies&&used>=limit,today.plusDays(1).atStartOfDay(ZoneId.of("America/Lima")).toOffsetDateTime(),"America/Lima");}
    @Transactional(readOnly=true) public boolean available(){var c=current();if("DISABLED".equals(c.getProvider())||credential(c)==null)return false;var encrypted=new ArrayList<String>();encrypted.add(credential(c));if("RENIEC".equals(c.getProvider())&&c.getEncryptedClientId()!=null)encrypted.add(c.getEncryptedClientId());if("RENIEC".equals(c.getProvider())&&c.getEncryptedClientSecret()!=null)encrypted.add(c.getEncryptedClientSecret());try{encrypted.forEach(this::decrypt);return true;}catch(ConflictException e){return false;}}
    @Transactional public Configuration update(Update request,String actor){admin.requireAdmin(actor);
        if(request==null||request.provider()==null)throw new IllegalArgumentException("Proveedor DNI requerido.");
        var provider=request.provider().trim().toUpperCase(Locale.ROOT);
        if(!Set.of("DISABLED","RENIEC","PERUAPI_COM","FACTILIZA").contains(provider))throw new IllegalArgumentException("Proveedor DNI inválido.");
        var c=current();
        if("RENIEC".equals(provider))c.setEndpoint(validateReniecEndpoint(request.endpoint()!=null?request.endpoint():c.getEndpoint()));
        if("PERUAPI_COM".equals(provider)&&request.apiKey()!=null&&!request.apiKey().isBlank()&&!request.apiKey().trim().matches("[\\x21-\\x7E]{32,128}"))
            throw new IllegalArgumentException("La clave de PerúAPI.com debe tener entre 32 y 128 caracteres visibles.");
        if("FACTILIZA".equals(provider)&&request.apiKey()!=null&&!request.apiKey().isBlank()&&!request.apiKey().trim().matches("[\\x21-\\x7E]{1,512}"))
            throw new IllegalArgumentException("Token Factiliza inválido.");
        if(request.apiKey()!=null&&!request.apiKey().isBlank()){
            var encrypted=encrypt(request.apiKey().trim());
            if("PERUAPI_COM".equals(provider))c.setEncryptedPeruapiKey(encrypted);
            if("FACTILIZA".equals(provider))c.setEncryptedFactilizaToken(encrypted);
            if("RENIEC".equals(provider))c.setEncryptedApiKey(encrypted);
        }
        if("RENIEC".equals(provider)&&request.clientId()!=null&&!request.clientId().isBlank())c.setEncryptedClientId(encrypt(request.clientId().trim()));
        if("RENIEC".equals(provider)&&request.clientSecret()!=null&&!request.clientSecret().isBlank())c.setEncryptedClientSecret(encrypt(request.clientSecret().trim()));
        c.setProvider(provider);c.setActualizadoPor(actor);c.setActualizadoEn(LocalDateTime.now(ZoneId.of("America/Lima")));
        return view(repository.save(c));
    }
    @Transactional(readOnly=true) public String testConnection(String actor){admin.requireAdmin(actor);var c=current();if(!available())throw new ReniecNotConfiguredException();
        if("PERUAPI_COM".equals(c.getProvider()))return "PerúAPI.com no ofrece una prueba sin consultar un DNI; guarda la configuración y realiza una consulta autorizada.";
        return "Usa un DNI de prueba autorizado por la entidad para verificar el proveedor seleccionado.";
    }
    @Transactional(readOnly=true) public DniTestResult testDni(String dni,String actor){
        admin.requireAdmin(actor);
        if(dni==null||!dni.matches("\\d{8}"))throw new IllegalArgumentException("El DNI debe tener exactamente 8 dígitos.");
        var now=java.time.Instant.now();var limited=new AtomicBoolean();
        lastTestByAdmin.compute(actor,(key,last)->{if(last!=null&&last.plusSeconds(2).isAfter(now)){limited.set(true);return last;}return now;});
        if(limited.get())throw new pe.gob.munisanmiguel.exception.TooManyRequestsException("Espera unos segundos antes de volver a probar la consulta DNI.");
        var configuration=current();var provider=configuration.getProvider();
        if("DISABLED".equals(provider))throw new ReniecNotConfiguredException("El proveedor DNI está desactivado.");
        var encrypted=credential(configuration);if(encrypted==null)throw new ReniecNotConfiguredException("No hay una credencial guardada para el proveedor DNI seleccionado.");
        try{decrypt(encrypted);}catch(ConflictException e){throw new ConflictException(e.getMessage());}
        var result=findByDni(dni);
        return new DniTestResult(result.dni(),result.nombres(),result.apellidoPaterno(),result.apellidoMaterno(),provider,"Consulta realizada correctamente.");
    }
    @Override @Transactional(readOnly=true) public ReniecResponse findByDni(String dni){if(dni==null||!dni.matches("\\d{8}"))throw new IllegalArgumentException("El DNI debe tener ocho dígitos.");
        var c=current();if("DISABLED".equals(c.getProvider())||credential(c)==null)throw new ReniecNotConfiguredException();
        var adapter=adapters.get(c.getProvider());if(adapter==null)throw new ReniecNotConfiguredException();
        try{var request=client.get().uri(adapter.url(c,dni));adapter.authorize(request,c,this::decrypt);
            if("PERUAPI_COM".equals(c.getProvider())&&!quota.reserve(LocalDate.now(ZoneId.of("America/Lima"))))throw new pe.gob.munisanmiguel.exception.TooManyRequestsException("Se alcanzó el límite diario de 45 consultas DNI. Puedes ingresar los datos manualmente. La cuota se renovará mañana.");
            String body=request.retrieve().onStatus(HttpStatusCode::is4xxClientError,(req,res)->{
                int status=res.getStatusCode().value();String detail=providerErrorDetail(res);
                if(status==404)throw new NotFoundException("DNI no encontrado.");
                if(status==401||status==403){if("PERUAPI_COM".equals(c.getProvider())){if(isPeruApiIpNotAuthorized(detail))throw new ConflictException("La IP pública de salida del servidor no está autorizada en PerúAPI. Registra la IP pública de salida del backend en la lista de IP permitidas del panel de PerúAPI. La credencial guardada se conserva; reintenta después de autorizarla.");throw new ConflictException("La clave de PerúAPI fue rechazada, venció o el plan está inactivo.");}if("FACTILIZA".equals(c.getProvider()))throw new ConflictException("Factiliza rechazó el token o el acceso del plan contratado.");throw new ConflictException("RENIEC rechazó las credenciales o el acceso configurado para el servicio.");}
                if(status==429){String normalized=Normalizer.normalize(detail,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);if("RENIEC".equals(c.getProvider()))throw new pe.gob.munisanmiguel.exception.TooManyRequestsException("RENIEC indicó que se alcanzó su límite de consultas; espera según las condiciones del servicio.");if("FACTILIZA".equals(c.getProvider()))throw new pe.gob.munisanmiguel.exception.TooManyRequestsException("Factiliza indicó que se alcanzó la cuota o frecuencia del plan contratado.");throw new pe.gob.munisanmiguel.exception.TooManyRequestsException(normalized.contains("cuota")?"La cuota de consultas DNI de PerúAPI está agotada.":"PerúAPI recibió demasiadas solicitudes; espera antes de reintentar.");}
                if(status==503)throw new ReniecUnavailableException("El servicio DNI del proveedor está temporalmente no disponible.");
                throw new ReniecUnavailableException("El proveedor DNI rechazó la consulta por su configuración o parámetros.");
            }).onStatus(HttpStatusCode::is5xxServerError,(req,res)->{throw new ReniecUnavailableException("El servicio DNI del proveedor está temporalmente no disponible.");}).body(String.class);
            var root=mapper.readTree(body);
            if("FACTILIZA".equals(c.getProvider()))return mapFactilizaResponse(root,dni);
            return mapResponse(root,dni);
        }catch(NotFoundException|ReniecUnavailableException|ConflictException|pe.gob.munisanmiguel.exception.TooManyRequestsException e){throw e;}
        catch(Exception e){throw new ReniecUnavailableException("No se pudo conectar temporalmente con el proveedor DNI.");}
    }
    ReniecResponse mapResponse(JsonNode root,String dni){JsonNode data=root.has("data")&&root.get("data").isObject()?root.get("data"):root;
        String names=field(data,"nombres","nombre","firstName");String paternal=field(data,"apellido_paterno","apellidoPaterno","lastName");String maternal=field(data,"apellido_materno","apellidoMaterno","secondLastName");
        if(names.isBlank()||paternal.isBlank())throw new NotFoundException("DNI sin datos utilizables.");
        return new ReniecResponse(dni,names,paternal,maternal,null,null);
    }
    ReniecResponse mapFactilizaResponse(JsonNode root,String dni){
        if(!root.path("success").asBoolean(false)){
            int status=root.path("status").asInt(0);
            String message=Normalizer.normalize(root.path("message").asText(""),Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);
            if(status==401||status==403)throw new ConflictException("Factiliza rechazó el token o el acceso del plan contratado.");
            if(status==429||message.contains("cuota")||message.contains("limit")||message.contains("creditos"))throw new pe.gob.munisanmiguel.exception.TooManyRequestsException("Factiliza indicó que se alcanzó la cuota o frecuencia del plan contratado.");
            if(status==404||message.contains("no encontrado")||message.contains("no existe"))throw new NotFoundException("DNI no encontrado.");
            throw new ReniecUnavailableException("Factiliza no pudo completar la consulta DNI.");
        }
        var data=root.path("data");
        if(!data.isObject()||!dni.equals(field(data,"numero"))||field(data,"nombres").isBlank()||field(data,"apellido_paterno").isBlank()||field(data,"apellido_materno").isBlank())
            throw new ReniecUnavailableException("Factiliza devolvió datos DNI incompletos.");
        return new ReniecResponse(dni,field(data,"nombres"),field(data,"apellido_paterno"),field(data,"apellido_materno"),null,null);
    }
    private String field(JsonNode data,String... keys){for(String key:keys)if(data.hasNonNull(key))return data.get(key).asText("").trim();return "";}
    static boolean isPeruApiIpNotAuthorized(String detail){
        if(detail==null||detail.isBlank())return false;
        String value=Normalizer.normalize(detail,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();
        boolean ip=value.matches(".*\\bip\\b.*")||value.contains("ip address")||value.contains("direccion ip");
        boolean denied=value.contains("not authorized")||value.contains("unauthorized")||value.contains("not allowed")||value.contains("not allowlisted")||value.contains("not whitelisted")||value.contains("not on allowlist")||value.contains("not in allow list")||value.contains("no autorizad")||value.contains("no permitid")||value.contains("no esta autorizad")||value.contains("no esta permitid")||value.contains("no esta en lista")||value.contains("no se encuentra autorizad")||value.contains("blocked by ip");
        return ip&&denied;
    }
    private IdentityIntegration current(){return repository.findById((byte)1).orElseGet(()->{var c=new IdentityIntegration();c.setId((byte)1);return c;});}
    private String credential(IdentityIntegration c){return switch(c.getProvider()){
        case "RENIEC" -> c.getEncryptedApiKey()!=null?c.getEncryptedApiKey():c.getEncryptedClientSecret();
        case "PERUAPI_COM" -> c.getEncryptedPeruapiKey();
        case "FACTILIZA" -> c.getEncryptedFactilizaToken();
        default -> null;
    };}
    private String providerErrorDetail(org.springframework.http.client.ClientHttpResponse response){try{return new String(response.getBody().readNBytes(4096),java.nio.charset.StandardCharsets.UTF_8);}catch(Exception e){return "";}}
    private Configuration view(IdentityIntegration c){return new Configuration(c.getProvider(),"RENIEC".equals(c.getProvider())?c.getEndpoint():null,credential(c)!=null,c.getActualizadoPor(),c.getActualizadoEn());}
    private String validateReniecEndpoint(String value){try{String raw=value==null?"":value.trim();if(raw.indexOf('{')>=0&&!raw.contains("{dni}"))throw new IllegalArgumentException();var uri=URI.create(raw.replace("{dni}","00000000"));var host=uri.getHost();
        if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||!reniecHosts.contains(host.toLowerCase(Locale.ROOT))||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getQuery()!=null||uri.getFragment()!=null)
            throw new IllegalArgumentException("El endpoint RENIEC debe ser HTTPS y usar un dominio autorizado en RENIEC_ALLOWED_HOSTS.");
        return raw.replaceAll("/$","");
    }catch(IllegalArgumentException e){throw new IllegalArgumentException("Endpoint RENIEC inválido o no autorizado.");}}
    private SecretKeySpec key(){if(masterKey==null||masterKey.isBlank())throw new ConflictException("Falta IDENTITY_MASTER_KEY. Configura la clave maestra local antes de guardar credenciales.");try{byte[] raw=Base64.getDecoder().decode(masterKey);if(raw.length!=32)throw new IllegalArgumentException();return new SecretKeySpec(raw,"AES");}
        catch(IllegalArgumentException e){throw new ConflictException("IDENTITY_MASTER_KEY debe contener 32 bytes codificados en Base64.");}}
    private String encrypt(String value){try{var secretKey=key();byte[] iv=new byte[12];RANDOM.nextBytes(iv);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,secretKey,new GCMParameterSpec(128,iv));byte[] bytes=cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));byte[] all=new byte[iv.length+bytes.length];System.arraycopy(iv,0,all,0,iv.length);System.arraycopy(bytes,0,all,iv.length,bytes.length);return Base64.getEncoder().encodeToString(all);}catch(ConflictException e){throw e;}catch(Exception e){throw new IllegalStateException("Fallo técnico al cifrar la credencial DNI.",e);}}
    private String decrypt(String value){try{var secretKey=key();byte[] all;try{all=Base64.getDecoder().decode(value);}catch(IllegalArgumentException e){throw new ConflictException("La credencial cifrada almacenada no es válida; actualízala desde Configuración.");}if(all.length<29)throw new ConflictException("La credencial cifrada no es válida; actualízala desde Configuración.");byte[] iv=Arrays.copyOfRange(all,0,12);var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,secretKey,new GCMParameterSpec(128,iv));return new String(cipher.doFinal(all,12,all.length-12),java.nio.charset.StandardCharsets.UTF_8);}catch(ConflictException e){throw e;}catch(javax.crypto.AEADBadTagException e){throw new ConflictException("La credencial cifrada no coincide con IDENTITY_MASTER_KEY; consérvala y restaura la clave original.");}catch(Exception e){throw new IllegalStateException("Fallo técnico al descifrar la credencial DNI.",e);}}
}
