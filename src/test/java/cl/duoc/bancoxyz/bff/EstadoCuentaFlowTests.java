package cl.duoc.bancoxyz.bff;
import cl.duoc.bancoxyz.bff.common.client.AccountServiceClient;
import cl.duoc.bancoxyz.bff.common.exception.ResourceNotFoundException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties="lab.kafka.enabled=true") @AutoConfigureMockMvc
class EstadoCuentaFlowTests {
    @Autowired MockMvc mvc;
    @MockitoBean AccountServiceClient accounts;
    @MockitoBean KafkaTemplate<String,String> kafka;
    static final String URL="/api/web/cuentas/137/solicitudes-estado";
    static final String KEY="b16f973f-a9bf-49d4-98c8-d6962962325c";
    @BeforeEach void setup() {
        clearInvocations(kafka,accounts); // Omitir llamadas de ciclo de vida del contexto Spring.
        when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.completedFuture(null));
    }
    @Test void missingTokenCannotPublish() throws Exception {
        mvc.perform(post(URL).contentType("application/json").content("{\"anio\":2026}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(kafka,accounts);
    }
    @Test void readScopeCannotPublish() throws Exception {
        mvc.perform(post(URL).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.read"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(kafka,accounts);
    }
    @Test void acceptsOnlyAfterAckAndReusesSameEventForRetries() throws Exception {
        for (int n=0;n<2;n++) mvc.perform(post(URL).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.write")))
                .header("Idempotency-Key",KEY).contentType("application/json").content("{\"anio\":2026}"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.estado").value("ACEPTADA"))
                .andExpect(header().exists("Location"));
        var values=ArgumentCaptor.forClass(String.class);
        verify(kafka,times(2)).send(eq("bancoxyz.cuentas.commands.v1"),anyString(),values.capture());
        assertEquals(values.getAllValues().get(0),values.getAllValues().get(1));
    }
    @Test void kafkaFailureDoesNotReportSuccess() throws Exception {
        when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));
        mvc.perform(post(URL).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.write")))
                .header("Idempotency-Key",KEY).contentType("application/json").content("{\"anio\":2026}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("misma Idempotency-Key")));
    }
    @Test void missingKeyAndInvalidPayloadAre400() throws Exception {
        mvc.perform(post(URL).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.write")))
                .contentType("application/json").content("{\"anio\":2026}")).andExpect(status().isBadRequest());
        mvc.perform(post(URL).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.write")))
                .header("Idempotency-Key",KEY).contentType("application/json").content("{\"anio\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.write")))
                .header("Idempotency-Key",KEY).contentType("application/json").content("bad-json"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(kafka,accounts);
    }
    @Test void nonexistentAccountIsNotPublished() throws Exception {
        when(accounts.findAccount(137)).thenThrow(new ResourceNotFoundException("No existe"));
        mvc.perform(post(URL).with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.write")))
                .header("Idempotency-Key",KEY).contentType("application/json").content("{\"anio\":2026}"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(kafka);
    }
    @Test void queryUsesAuthenticatedClientNotRequestInput() throws Exception {
        UUID id=UUID.randomUUID();
        when(accounts.findSolicitud(id,"user")).thenReturn(Map.of("estado","REGISTRADA"));
        mvc.perform(get("/api/web/solicitudes-estado/"+id).param("cliente","otro")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_web.read"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("REGISTRADA"));
        verify(accounts).findSolicitud(id,"user");
    }
}
