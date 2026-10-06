package cl.duoc.bancoxyz.bff.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WebMovimientoDetalle(
        Integer id,
        LocalDate fecha,
        String tipoTransaccion,
        BigDecimal monto,
        String descripcion,
        Boolean auditado
) {
}
