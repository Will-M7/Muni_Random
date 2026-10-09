package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="global_parameter") @Getter @Setter
public class GlobalParameter {
    @Id @Column(length=80) private String name;
    @Column(name="int_value",nullable=false) private int intValue;
    @Column(name="actualizado_por") private String actualizadoPor;
    @Column(name="actualizado_en") private LocalDateTime actualizadoEn;
}
