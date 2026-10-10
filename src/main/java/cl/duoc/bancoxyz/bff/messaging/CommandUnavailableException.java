package cl.duoc.bancoxyz.bff.messaging;
public class CommandUnavailableException extends RuntimeException {
    public CommandUnavailableException(Throwable cause) {
        super("No se pudo confirmar Kafka. Reintenta con la misma Idempotency-Key y el mismo contenido.", cause);
    }
}
