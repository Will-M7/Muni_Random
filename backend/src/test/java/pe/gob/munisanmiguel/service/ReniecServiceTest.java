package pe.gob.munisanmiguel.service;

import org.junit.jupiter.api.Test;
import pe.gob.munisanmiguel.dto.ApiDtos.ReniecResponse;
import pe.gob.munisanmiguel.exception.NotFoundException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ReniecServiceTest {
    @Test void rechazaDniInvalido() {
        var service = new ReniecService(dni -> fail("No debe llamar al proveedor"));
        assertThrows(IllegalArgumentException.class, () -> service.consultar("1234567"));
        assertThrows(IllegalArgumentException.class, () -> service.consultar("1234567A"));
        assertThrows(IllegalArgumentException.class, () -> service.consultar("123456789"));
    }

    @Test void devuelveDatosDelProveedorSinTransformarlosEnController() {
        var expected = new ReniecResponse("12345678", "Ana", "Pérez", "Lima", "F", LocalDate.of(1990, 1, 2));
        var service = new ReniecService(dni -> expected);
        assertEquals(expected, service.consultar("12345678"));
    }

    @Test void propagaDniNoEncontradoDelProveedor() {
        var service = new ReniecService(dni -> { throw new NotFoundException("DNI no encontrado en RENIEC."); });
        assertThrows(NotFoundException.class, () -> service.consultar("12345678"));
    }
}
