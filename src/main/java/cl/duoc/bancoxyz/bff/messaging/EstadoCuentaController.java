package cl.duoc.bancoxyz.bff.messaging;
import cl.duoc.bancoxyz.bff.common.client.AccountServiceClient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @Validated
@RequestMapping("/api/web")
@ConditionalOnProperty(name="lab.kafka.enabled", havingValue="true")
public class EstadoCuentaController {
    private final EstadoCuentaPublisher publisher;
    private final AccountServiceClient accounts;
    public EstadoCuentaController(EstadoCuentaPublisher publisher, AccountServiceClient accounts) {
        this.publisher=publisher; this.accounts=accounts;
    }
    public record Solicitud(@NotNull @Min(2000) @Max(2100) Integer anio) {}
    public record Aceptada(UUID solicitudId, String estado, String consulta) {}
    @PostMapping("/cuentas/{cuentaId}/solicitudes-estado")
    public ResponseEntity<Aceptada> solicitar(@PathVariable @Min(1) Integer cuentaId,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody Solicitud body,
            @AuthenticationPrincipal Jwt jwt) {
        accounts.findAccount(cuentaId);
        UUID id = publisher.publish(jwt.getSubject(), key, cuentaId, body.anio());
        String path = "/api/web/solicitudes-estado/" + id;
        return ResponseEntity.accepted().location(URI.create(path)).body(new Aceptada(id,"ACEPTADA",path));
    }
    @GetMapping("/solicitudes-estado/{id}")
    public Object consultar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return accounts.findSolicitud(id, jwt.getSubject());
    }
}
