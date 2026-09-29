package pe.gob.munisanmiguel.service;

import pe.gob.munisanmiguel.dto.ApiDtos.ReniecResponse;

public interface IdentityProvider {
    ReniecResponse findByDni(String dni);
}
