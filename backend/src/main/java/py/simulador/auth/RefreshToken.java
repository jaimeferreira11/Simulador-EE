package py.simulador.auth;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@Table(schema = "sim", value = "refresh_token")
public class RefreshToken {

    @Id
    private Long id;
    private Long usuarioId;
    private String tokenHash;
    private OffsetDateTime expiresAt;
    private boolean revocado;
    private OffsetDateTime createdAt;

    public RefreshToken(Long usuarioId, String tokenHash, OffsetDateTime expiresAt) {
        this.usuarioId = usuarioId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revocado = false;
        this.createdAt = OffsetDateTime.now();
    }

    public void revocar() { this.revocado = true; }
}
