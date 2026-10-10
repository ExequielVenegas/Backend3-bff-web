package cl.duoc.bancoxyz.bff.web.controller;
import cl.duoc.bancoxyz.bff.common.client.ManagedAccountClient;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
@RestController @Validated @RequestMapping("/api/web/cuentas-modernas")
public class WebManagedAccountController {
 private final ManagedAccountClient client;
 private final ObjectMapper json;
 public WebManagedAccountController(ManagedAccountClient client,ObjectMapper json) { this.client=client; this.json=json; }
 @PostMapping public ResponseEntity<String> create(@RequestHeader("Idempotency-Key") UUID key,
  @RequestBody String body,@AuthenticationPrincipal Jwt jwt) {
  var result=client.create(key,body,jwt.getTokenValue());
  var response=ResponseEntity.status(result.getStatusCode()).contentType(MediaType.APPLICATION_JSON);
  if(result.getStatusCode().is2xxSuccessful()) {
   UUID id=UUID.fromString(json.readTree(result.getBody()).get("cuentaId").asText());
   response.location(URI.create("/api/web/cuentas-modernas/"+id));
  }
  return response.body(result.getBody());
 }
 @GetMapping("/{id}") public ResponseEntity<String> find(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) {
  return publicResponse(client.find(id,jwt.getTokenValue()));
 }
 @GetMapping public ResponseEntity<String> list(@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,
  @RequestParam(defaultValue="20") @Min(1) @Max(100) int size,@AuthenticationPrincipal Jwt jwt) {
  return publicResponse(client.list(page,size,jwt.getTokenValue()));
 }
 @PutMapping("/{id}") public ResponseEntity<String> update(@PathVariable UUID id,@RequestBody String body,@AuthenticationPrincipal Jwt jwt) {
  return publicResponse(client.update(id,body,jwt.getTokenValue()));
 }
 private ResponseEntity<String> publicResponse(ResponseEntity<String> r) {
  return ResponseEntity.status(r.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(r.getBody());
 }
 @PostMapping("/importaciones") public ResponseEntity<String> importLegacy(@RequestBody String body,@AuthenticationPrincipal Jwt jwt) {
  var result=client.importLegacy(body,jwt.getTokenValue());
  var response=ResponseEntity.status(result.getStatusCode()).contentType(MediaType.APPLICATION_JSON);
  if(result.getStatusCode().is2xxSuccessful()) {
   UUID id=UUID.fromString(json.readTree(result.getBody()).get("cuentaId").asText());
   response.location(URI.create("/api/web/cuentas-modernas/"+id));
  }
  return response.body(result.getBody());
 }
 @GetMapping("/importaciones/{id}") public ResponseEntity<String> association(@PathVariable @Min(1) int id,@AuthenticationPrincipal Jwt jwt) {
  return publicResponse(client.legacyAssociation(id,jwt.getTokenValue()));
 }
 @PostMapping("/{id}/cierre") public ResponseEntity<String> close(@PathVariable UUID id,@RequestBody String body,@AuthenticationPrincipal Jwt jwt) {
  return publicResponse(client.close(id,body,jwt.getTokenValue()));
 }
}
