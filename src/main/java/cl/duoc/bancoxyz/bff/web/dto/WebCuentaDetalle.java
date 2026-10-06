package cl.duoc.bancoxyz.bff.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record WebCuentaDetalle(
        Integer cuentaId,
        String nombre,
        String tipo,
        BigDecimal saldoOriginal,
        BigDecimal interesAplicado,
        BigDecimal saldoFinal,
        List<WebMovimientoDetalle> movimientos
) {
}
