package cl.duoc.bancoxyz.bff.common.exception;

public class BackendUnavailableException extends RuntimeException {
    public BackendUnavailableException(Throwable cause) {
        super("El servicio de cuentas no esta disponible temporalmente. Intenta nuevamente.", cause);
    }
}
