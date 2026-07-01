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
@Table(schema = "sim", value = "password_reset_token")
public class PasswordResetToken {

    @Id
    private Long id;
    private Long usuarioId;
    private String token;
    private OffsetDateTime expiresAt;
    private boolean used;
    private OffsetDateTime createdAt;

    public PasswordResetToken(Long usuarioId, String token, OffsetDateTime expiresAt) {
        this.usuarioId = usuarioId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.used = false;
        this.createdAt = OffsetDateTime.now();
    }
}
