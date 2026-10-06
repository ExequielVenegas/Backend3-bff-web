package cl.duoc.bancoxyz.bff.common.client;
import java.math.BigDecimal;
import java.util.List;
public record AccountServiceResponse(Integer accountId, String customerName, String accountType,
        BigDecimal originalBalance, BigDecimal interestAmount, BigDecimal availableBalance,
        List<AccountMovementResponse> movements) {}
