package cl.duoc.bancoxyz.bff.common.client;
import cl.duoc.bancoxyz.bff.common.exception.ResourceNotFoundException;
import cl.duoc.bancoxyz.bff.common.config.AccountResilience;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AccountServiceClient {
    private final RestClient client;
    private final AccountResilience resilience;
    public AccountServiceClient(@Qualifier("accountRestClientBuilder") RestClient.Builder builder,
            @Value("${services.account.base-url}") String baseUrl, AccountResilience resilience) {
        this.client = builder.baseUrl(baseUrl).build();
        this.resilience = resilience;
    }
    public AccountServiceResponse findAccount(Integer id) {
        return resilience.execute(() -> client.get().uri("/internal/accounts/{id}", id).retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> { throw new ResourceNotFoundException("No existe la cuenta " + id); })
                .body(AccountServiceResponse.class));
    }
    public TransactionServiceResponse findTransaction(Integer id) {
        return resilience.execute(() -> client.get().uri("/internal/transactions/{id}", id).retrieve()
                .onStatus(status -> status.value() == 404, (req, res) -> { throw new ResourceNotFoundException("No existe la transacción " + id); })
                .body(TransactionServiceResponse.class));
    }
}
