package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="user_preference") @Getter @Setter
public class UserPreference {
    @Id @Column(name="user_id") private Long userId;
    @Column(name="mapa_interno",nullable=false) private boolean mapaInterno=true;
    @Column(name="google_externo",nullable=false) private boolean googleExterno=true;
    @Column(name="google_url",nullable=false) private boolean googleUrl=true;
    @Column(name="descargar_jornada_html",nullable=false) private boolean descargarJornadaHtml=true;
    @Column(name="actualizado_por") private String actualizadoPor;
    @Column(name="actualizado_en") private LocalDateTime actualizadoEn;
}
