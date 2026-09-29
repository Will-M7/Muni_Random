package pe.gob.munisanmiguel.service;

import org.springframework.stereotype.Service;
import pe.gob.munisanmiguel.dto.ApiDtos.ReniecResponse;

@Service
public class ReniecService {
    private final IdentityProvider provider;
    public ReniecService(IdentityProvider provider) { this.provider = provider; }

    public ReniecResponse consultar(String dni) {
        if (dni == null || !dni.matches("\\d{8}")) {
            throw new IllegalArgumentException("El DNI debe tener exactamente 8 dígitos.");
        }
        return provider.findByDni(dni);
    }
}
