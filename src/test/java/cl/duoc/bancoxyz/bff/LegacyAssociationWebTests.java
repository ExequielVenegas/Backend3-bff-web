package cl.duoc.bancoxyz.bff;
import cl.duoc.bancoxyz.bff.common.client.ManagedAccountClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
class LegacyAssociationWebTests {
 @Autowired MockMvc mvc;
 @MockitoBean ManagedAccountClient client;
 @Test void ordinaryAccountPermissionsDoNotGrantImports() throws Exception {
  mvc.perform(post("/api/web/cuentas-modernas/importaciones").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.accounts.write")))
   .contentType("application/json").content("{}")).andExpect(status().isForbidden());
  verifyNoInteractions(client);
 }
 @Test void forwardsDecisionAndRewritesLocation() throws Exception {
  String id="f0000000-0000-0000-0000-000000000001";
  when(client.importLegacy("{}","token")).thenReturn(ResponseEntity.status(201).body("{\"cuentaId\":\""+id+"\"}"));
  mvc.perform(post("/api/web/cuentas-modernas/importaciones").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.accounts.import")))
   .contentType("application/json").content("{}")).andExpect(status().isCreated()).andExpect(header().string("Location","/api/web/cuentas-modernas/"+id));
  verify(client).importLegacy("{}","token");
 }
 @Test void returnsConflictWithoutRetryOrChangingPayload() throws Exception {
  when(client.importLegacy("{}","token")).thenReturn(ResponseEntity.status(409).body("{}"));
  mvc.perform(post("/api/web/cuentas-modernas/importaciones").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.accounts.import")))
   .contentType("application/json").content("{}")).andExpect(status().isConflict());
  verify(client,times(1)).importLegacy("{}","token");
 }
}
