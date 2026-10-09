package pe.gob.munisanmiguel.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class GoogleMapsCoordinatesTest {
    @Test void soloAceptaPuntosExplicitos(){
        assertArrayEquals(new BigDecimal[]{new BigDecimal("-15.4"),new BigDecimal("-70.1")},GoogleMapsCoordinates.extract("https://www.google.com/maps/place/predio/data=!3d-15.4!4d-70.1"));
        assertNull(GoogleMapsCoordinates.extract("https://www.google.com/maps/@-15.4,-70.1,17z"));
        assertNull(GoogleMapsCoordinates.extract("https://google.com.evil.test/?q=-15.4,-70.1"));
    }
}
