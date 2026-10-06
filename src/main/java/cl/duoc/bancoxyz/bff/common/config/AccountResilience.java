package cl.duoc.bancoxyz.bff.common.config;

import cl.duoc.bancoxyz.bff.common.exception.*;
import io.github.resilience4j.bulkhead.*;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.retry.*;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class AccountResilience implements InfoContributor {
    private static final Logger log = LoggerFactory.getLogger(AccountResilience.class);
    private final CircuitBreaker circuit;
    private final Retry retry;
    private final Bulkhead bulkhead;

    public AccountResilience(
            @Value("${services.account.resilience.attempts:2}") int attempts,
            @Value("${services.account.resilience.retry-wait:200ms}") Duration retryWait,
            @Value("${services.account.resilience.open-wait:10s}") Duration openWait,
            @Value("${services.account.resilience.max-concurrent:10}") int concurrent) {
        circuit = CircuitBreaker.of("account-service", CircuitBreakerConfig.custom()
            .slidingWindowSize(8).minimumNumberOfCalls(4).failureRateThreshold(50)
            .waitDurationInOpenState(openWait).permittedNumberOfCallsInHalfOpenState(2)
            .ignoreExceptions(ResourceNotFoundException.class, HttpClientErrorException.class).build());
        retry = Retry.of("account-service", RetryConfig.custom().maxAttempts(attempts)
            .waitDuration(retryWait).retryOnException(AccountResilience::isTransient).build());
        bulkhead = Bulkhead.of("account-service", BulkheadConfig.custom()
            .maxConcurrentCalls(concurrent).maxWaitDuration(Duration.ZERO).build());
        circuit.getEventPublisher().onStateTransition(event ->
            log.info("Account circuit breaker: {}", event.getStateTransition()));
        retry.getEventPublisher().onRetry(event ->
            log.warn("Reintento Account: {}", event.getNumberOfRetryAttempts()));
    }

    private static boolean noInstance(Throwable error) {
        return error instanceof IllegalStateException &&
            "No instances available for account-service".equals(error.getMessage());
    }

    private static boolean isTransient(Throwable error) {
        return error instanceof ResourceAccessException || error instanceof HttpServerErrorException || noInstance(error);
    }

    public <T> T execute(Supplier<T> call) {
        // Una solicitud cuenta una vez para el circuito, incluso si realiza dos intentos.
        // El cupo se mantiene durante los reintentos; rechazos locales no cuentan como fallos del backend.
        Supplier<T> retried = Retry.decorateSupplier(retry, call);
        Supplier<T> guarded = CircuitBreaker.decorateSupplier(circuit, retried);
        try {
            return Bulkhead.decorateSupplier(bulkhead, guarded).get();
        } catch (CallNotPermittedException | BulkheadFullException | RestClientException error) {
            throw new BackendUnavailableException(error);
        } catch (IllegalStateException error) {
            if (noInstance(error)) { throw new BackendUnavailableException(error); }
            throw error;
        }
    }

    public CircuitBreaker circuit() { return circuit; }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("resilience", Map.of("accountService", Map.of(
            "state", circuit.getState().name(),
            "failedCalls", circuit.getMetrics().getNumberOfFailedCalls(),
            "bufferedCalls", circuit.getMetrics().getNumberOfBufferedCalls(),
            "availableConcurrentCalls", bulkhead.getMetrics().getAvailableConcurrentCalls())));
    }
}
