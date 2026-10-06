package cl.duoc.bancoxyz.bff;
import cl.duoc.bancoxyz.bff.common.client.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
@SpringBootTest @AutoConfigureMockMvc
class BffWebApplicationTests {
 @Autowired MockMvc mvc; @MockitoBean AccountServiceClient client;
 @Test void contextLoads() {}
 @Test void unavailableBackendReturns503WithoutInventedData() throws Exception {
  when(client.findAccount(1)).thenThrow(new cl.duoc.bancoxyz.bff.common.exception.BackendUnavailableException(new RuntimeException("internal")));
  mvc.perform(get("/api/web/cuentas/1").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.read"))))
   .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value(503))
   .andExpect(jsonPath("$.saldoFinal").doesNotExist());
 }
 @Test void returnsCompleteWebRepresentation() throws Exception {
  when(client.findAccount(1)).thenReturn(new AccountServiceResponse(1,"Ana","AHORRO",bd("1000"),bd("50"),bd("1050"),List.of(new AccountMovementResponse(7,LocalDate.of(2026,1,1),"DEPOSITO",bd("100"),"Abono",true))));
  mvc.perform(get("/api/web/cuentas/1").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.read")))).andExpect(status().isOk()).andExpect(jsonPath("$.saldoOriginal").value(1000)).andExpect(jsonPath("$.interesAplicado").value(50)).andExpect(jsonPath("$.movimientos[0].descripcion").value("Abono"));
 }
 private BigDecimal bd(String v){return new BigDecimal(v);}
}
