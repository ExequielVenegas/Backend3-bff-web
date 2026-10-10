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
public class ManagedAccountClient {
 private final RestClient client;
 private final CircuitBreaker circuit=CircuitBreaker.of("managed-account-service",CircuitBreakerConfig.custom()
  .slidingWindowSize(8).minimumNumberOfCalls(4).failureRateThreshold(50)
  .waitDurationInOpenState(Duration.ofSeconds(10)).permittedNumberOfCallsInHalfOpenState(2)
  .ignoreExceptions(HttpClientErrorException.class).build());
 private final Bulkhead bulkhead=Bulkhead.of("managed-account-service",BulkheadConfig.custom().maxConcurrentCalls(10).maxWaitDuration(Duration.ZERO).build());
 private final Retry readRetry=Retry.of("managed-account-service-read",RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ofMillis(200))
  .retryOnException(e->e instanceof ResourceAccessException || e instanceof HttpServerErrorException).build());
 public ManagedAccountClient(@Qualifier("accountRestClientBuilder") RestClient.Builder builder,
  @Value("${services.managed-account.base-url:http://localhost:8090}") String baseUrl) {
  this.client=builder.clone().baseUrl(baseUrl).build();
 }
 public ResponseEntity<String> create(UUID key,String json,String token) {
  return call(()->client.post().uri("/internal/managed-accounts").headers(h->h.setBearerAuth(token))
   .header("Idempotency-Key",key.toString()).contentType(MediaType.APPLICATION_JSON).body(json).retrieve().toEntity(String.class),false);
 }
 public ResponseEntity<String> find(UUID id,String token) {
  return call(()->client.get().uri("/internal/managed-accounts/{id}",id).headers(h->h.setBearerAuth(token)).retrieve().toEntity(String.class),true);
 }
 public ResponseEntity<String> importLegacy(String body,String token) {
  return call(()->client.post().uri("/internal/managed-accounts/importaciones").headers(h->h.setBearerAuth(token))
   .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().toEntity(String.class),false);
 }
 public ResponseEntity<String> legacyAssociation(int id,String token) {
  return call(()->client.get().uri("/internal/managed-accounts/importaciones/{id}",id).headers(h->h.setBearerAuth(token))
   .retrieve().toEntity(String.class),true);
 }
 public ResponseEntity<String> list(int page,int size,String token) {
  return call(()->client.get().uri("/internal/managed-accounts?page={page}&size={size}",page,size)
   .headers(h->h.setBearerAuth(token)).retrieve().toEntity(String.class),true);
 }
 public ResponseEntity<String> update(UUID id,String json,String token) {
  return call(()->client.put().uri("/internal/managed-accounts/{id}",id).headers(h->h.setBearerAuth(token))
   .contentType(MediaType.APPLICATION_JSON).body(json).retrieve().toEntity(String.class),false);
 }
 private ResponseEntity<String> call(Supplier<ResponseEntity<String>> action,boolean retryRead) {
 
  try {
   var retried=retryRead?Retry.decorateSupplier(readRetry,action):action;
   return Bulkhead.decorateSupplier(bulkhead,CircuitBreaker.decorateSupplier(circuit,retried)).get();
  } catch(HttpClientErrorException ex) {
   return ResponseEntity.status(ex.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(ex.getResponseBodyAsString());
  } catch(CallNotPermittedException|BulkheadFullException|RestClientException ex) {
   throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Servicio de cuentas temporalmente no disponible");
  } catch(IllegalStateException ex) {
   if("No instances available for account-service".equals(ex.getMessage()))
    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Servicio de cuentas temporalmente no disponible");
   throw ex;
  }
 }
 public ResponseEntity<String> close(UUID id,String json,String token) {
  return call(()->client.post().uri("/internal/managed-accounts/{id}/cierre",id).headers(h->h.setBearerAuth(token))
   .contentType(MediaType.APPLICATION_JSON).body(json).retrieve().toEntity(String.class),false);
 }
}
