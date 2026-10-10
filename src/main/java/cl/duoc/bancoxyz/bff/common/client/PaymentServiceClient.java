package cl.duoc.bancoxyz.bff.common.client;
import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.retry.*;
import io.github.resilience4j.bulkhead.*;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
@Component
public class PaymentServiceClient {
 private final RestClient client;
 private final CircuitBreaker circuit=CircuitBreaker.of("payment-service",CircuitBreakerConfig.custom()
  .slidingWindowSize(8).minimumNumberOfCalls(4).failureRateThreshold(50)
  .waitDurationInOpenState(Duration.ofSeconds(10)).permittedNumberOfCallsInHalfOpenState(2)
  .ignoreExceptions(HttpClientErrorException.class).build());
 private final Bulkhead bulkhead=Bulkhead.of("payment-service",BulkheadConfig.custom().maxConcurrentCalls(10).maxWaitDuration(Duration.ZERO).build());
 private final Retry readRetry=Retry.of("payment-service-read",RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ofMillis(200))
  .retryOnException(e->e instanceof ResourceAccessException || e instanceof HttpServerErrorException).build());
 public PaymentServiceClient(@Qualifier("accountRestClientBuilder") RestClient.Builder builder,
  @Value("${services.payment.base-url:http://localhost:8092}") String baseUrl) {
  this.client=builder.clone().baseUrl(baseUrl).build();
 }
 public ResponseEntity<String> create(UUID key,String json,String token) {
  return call(()->client.post().uri("/internal/payments").headers(h->h.setBearerAuth(token))
   .header("Idempotency-Key",key.toString()).contentType(MediaType.APPLICATION_JSON).body(json).retrieve().toEntity(String.class),false);
 }
 public ResponseEntity<String> find(UUID id,String token) {
  return call(()->client.get().uri("/internal/payments/{id}",id).headers(h->h.setBearerAuth(token)).retrieve().toEntity(String.class),true);
 }
 public ResponseEntity<String> list(int page,int size,String token) {
  return call(()->client.get().uri("/internal/payments?page={page}&size={size}",page,size)
   .headers(h->h.setBearerAuth(token)).retrieve().toEntity(String.class),true);
 }
 private ResponseEntity<String> call(Supplier<ResponseEntity<String>> action,boolean retryRead) {
  try {
   var retried=retryRead?Retry.decorateSupplier(readRetry,action):action;
   return Bulkhead.decorateSupplier(bulkhead,CircuitBreaker.decorateSupplier(circuit,retried)).get();
  } catch(HttpClientErrorException ex) {
   return ResponseEntity.status(ex.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(ex.getResponseBodyAsString());
  } catch(CallNotPermittedException|BulkheadFullException|RestClientException ex) {
   throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Servicio de pagos temporalmente no disponible");
  } catch(IllegalStateException ex) {
   if("No instances available for payment-service".equals(ex.getMessage()))
    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Servicio de pagos temporalmente no disponible");
   throw ex;
  }
 }
}
