package cl.duoc.bancoxyz.bff.web.service;

import cl.duoc.bancoxyz.bff.common.client.AccountServiceClient;
import cl.duoc.bancoxyz.bff.common.client.AccountMovementResponse;
import cl.duoc.bancoxyz.bff.web.dto.WebCuentaDetalle;
import cl.duoc.bancoxyz.bff.web.dto.WebMovimientoDetalle;
import cl.duoc.bancoxyz.bff.web.dto.WebTransaccionDetalle;
import org.springframework.stereotype.Service;

@Service
public class WebCuentaService {

    private final AccountServiceClient client;
    public WebCuentaService(AccountServiceClient client) { this.client = client; }

    public WebCuentaDetalle obtenerCuenta(Integer cuentaId) {
        var cuenta = client.findAccount(cuentaId);
        var movimientos = cuenta.movements().stream()
                .map(this::toMovimiento)
                .toList();
        return new WebCuentaDetalle(cuenta.accountId(), cuenta.customerName(), cuenta.accountType(), cuenta.originalBalance(),
                cuenta.interestAmount(), cuenta.availableBalance(), movimientos);
    }

    public WebTransaccionDetalle obtenerTransaccion(Integer id) {
        var transaccion = client.findTransaction(id);
        return new WebTransaccionDetalle(transaccion.id(), transaccion.date(), transaccion.amount(),
                transaccion.type(), transaccion.status());
    }

    private WebMovimientoDetalle toMovimiento(AccountMovementResponse movimiento) {
        return new WebMovimientoDetalle(movimiento.id(), movimiento.date(), movimiento.type(),
                movimiento.amount(), movimiento.description(), movimiento.audited());
    }
}
