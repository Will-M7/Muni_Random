package pe.gob.munisanmiguel.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EstadoSolicitudTest {
    @Test
    void aceptaSoloLosTresEstadosNuevos() {
        assertEquals(EstadoSolicitud.EN_ESPERA, EstadoSolicitud.fromFrontend("En espera"));
        assertEquals(EstadoSolicitud.VERIFICADO, EstadoSolicitud.fromFrontend("Verificado"));
        assertEquals("Observada", EstadoSolicitud.OBSERVADA.toFrontend());
    }
}
