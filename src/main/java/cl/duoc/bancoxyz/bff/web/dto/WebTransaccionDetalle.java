package cl.duoc.bancoxyz.bff.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WebTransaccionDetalle(
        Integer id,
        LocalDate fecha,
        BigDecimal monto,
        String tipo,
        String estado
) {
}
