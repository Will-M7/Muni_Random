package pe.gob.munisanmiguel.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="identity_integration") @Getter @Setter
public class IdentityIntegration {
    @Id private Byte id=1;
    @Column(nullable=false,length=24) private String provider="DISABLED";
    @Column(length=500) private String endpoint;
    @Column(name="encrypted_api_key",columnDefinition="TEXT") private String encryptedApiKey;
    @Column(name="encrypted_client_id",columnDefinition="TEXT") private String encryptedClientId;
    @Column(name="encrypted_client_secret",columnDefinition="TEXT") private String encryptedClientSecret;
    @Column(name="actualizado_por") private String actualizadoPor;
    @Column(name="actualizado_en") private LocalDateTime actualizadoEn;
}
