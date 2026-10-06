package cl.duoc.bancoxyz.bff.common.client;
import java.math.BigDecimal;
import java.time.LocalDate;
public record AccountMovementResponse(Integer id, LocalDate date, String type, BigDecimal amount,
        String description, Boolean audited) {}
