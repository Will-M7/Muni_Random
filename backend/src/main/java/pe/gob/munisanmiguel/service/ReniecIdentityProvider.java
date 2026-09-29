package pe.gob.munisanmiguel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pe.gob.munisanmiguel.dto.ApiDtos.ReniecResponse;
import pe.gob.munisanmiguel.exception.NotFoundException;
import pe.gob.munisanmiguel.exception.ReniecNotConfiguredException;
import pe.gob.munisanmiguel.exception.ReniecUnavailableException;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Component
public class ReniecIdentityProvider implements IdentityProvider {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String mode;
    private final String clientId;
    private final String clientSecret;
    private final String apiKey;

    public ReniecIdentityProvider(
            ObjectMapper mapper,
            @Value("${app.reniec.mode:disabled}") String mode,
            @Value("${app.reniec.base-url:}") String baseUrl,
            @Value("${app.reniec.client-id:}") String clientId,
            @Value("${app.reniec.client-secret:}") String clientSecret,
            @Value("${app.reniec.api-key:}") String apiKey,
            @Value("${app.reniec.connect-timeout-ms:2000}") int connectTimeout,
            @Value("${app.reniec.read-timeout-ms:4000}") int readTimeout) {
        this.mapper = mapper;
        this.mode = mode == null ? "disabled" : mode.trim().toLowerCase();
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/$", "");
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.apiKey = apiKey;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeout));
        factory.setReadTimeout(Duration.ofMillis(readTimeout));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public ReniecResponse findByDni(String dni) {
        if (mode.equals("disabled") || baseUrl.isBlank()) throw new ReniecNotConfiguredException();
        try {
            var request = client.get().uri(baseUrl + "/" + dni);
            if (clientId != null && !clientId.isBlank()) request = request.header("X-Client-Id", clientId);
            if (clientSecret != null && !clientSecret.isBlank()) request = request.header("X-Client-Secret", clientSecret);
            if (apiKey != null && !apiKey.isBlank()) request = request.header("X-API-Key", apiKey);
            String body = request.retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        if (res.getStatusCode().value() == 404) throw new NotFoundException("DNI no encontrado en RENIEC.");
                        throw new ReniecUnavailableException("RENIEC rechazó la consulta.");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> { throw new ReniecUnavailableException("No fue posible consultar RENIEC en este momento."); })
                    .body(String.class);
            return mapResponse(body, dni);
        } catch (NotFoundException | ReniecNotConfiguredException | ReniecUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new ReniecUnavailableException("No fue posible consultar RENIEC en este momento.", e);
        }
    }

    private ReniecResponse mapResponse(String body, String dni) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode data = root.has("data") && root.get("data").isObject() ? root.get("data") : root;
            String nombres = text(data, "nombres", "nombre", "firstName");
            String paterno = text(data, "apellidoPaterno", "apellido_paterno", "lastName");
            String materno = text(data, "apellidoMaterno", "apellido_materno", "secondLastName");
            if (nombres.isBlank() && paterno.isBlank() && materno.isBlank()) throw new NotFoundException("DNI no encontrado en RENIEC.");
            String sexo = text(data, "sexo", "gender");
            String nacimiento = text(data, "fechaNacimiento", "fecha_nacimiento", "birthDate");
            LocalDate date = null;
            if (!nacimiento.isBlank()) try { date = LocalDate.parse(nacimiento); } catch (DateTimeParseException ignored) { }
            return new ReniecResponse(dni, nombres, paterno, materno, sexo.isBlank() ? null : sexo, date);
        } catch (NotFoundException e) { throw e; }
        catch (Exception e) { throw new ReniecUnavailableException("La respuesta de RENIEC no tiene un formato utilizable.", e); }
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) if (node.hasNonNull(name)) return node.get(name).asText("").trim();
        return "";
    }
}
