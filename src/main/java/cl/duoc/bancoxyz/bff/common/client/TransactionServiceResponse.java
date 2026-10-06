package cl.duoc.bancoxyz.bff.common.client;
import java.math.BigDecimal;
import java.time.LocalDate;
public record TransactionServiceResponse(Integer id, LocalDate date, BigDecimal amount, String type, String status) {}
