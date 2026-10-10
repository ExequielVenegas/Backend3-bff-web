package cl.duoc.bancoxyz.bff.messaging;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
@Service
@ConditionalOnProperty(name="lab.kafka.enabled", havingValue="true")
public class EstadoCuentaPublisher {
    private final KafkaTemplate<String,String> kafka;
    private final ObjectMapper json;
    private final String topic;
    public EstadoCuentaPublisher(KafkaTemplate<String,String> kafka, ObjectMapper json,
            @Value("${lab.kafka.topic:bancoxyz.cuentas.commands.v1}") String topic) {
        this.kafka=kafka; this.json=json; this.topic=topic;
    }
    public UUID publish(String client, UUID key, int cuenta, int anio) {
        var id = UUID.nameUUIDFromBytes((client + ":" + key).getBytes(StandardCharsets.UTF_8));
        var command = new EstadoCuentaCommand(id, 1, "SOLICITAR_ESTADO_CUENTA", client, cuenta, anio);
        try {
            // 202 solamente despues del ack del broker; no equivale a COMMIT de MySQL.
            kafka.send(topic, id.toString(), json.writeValueAsString(command)).get(8, TimeUnit.SECONDS);
            return id;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new CommandUnavailableException(ex);
        } catch (Exception ex) { throw new CommandUnavailableException(ex); }
    }
}
