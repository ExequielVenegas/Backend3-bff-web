package cl.duoc.bancoxyz.bff.web.controller;

import cl.duoc.bancoxyz.bff.web.dto.WebCuentaDetalle;
import cl.duoc.bancoxyz.bff.web.dto.WebTransaccionDetalle;
import cl.duoc.bancoxyz.bff.web.service.WebCuentaService;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/web")
public class WebCuentaController {

    private final WebCuentaService service;

    public WebCuentaController(WebCuentaService service) {
        this.service = service;
    }

    @GetMapping("/cuentas/{cuentaId}")
    public WebCuentaDetalle obtenerCuenta(@PathVariable @Min(1) Integer cuentaId) {
        return service.obtenerCuenta(cuentaId);
    }

    @GetMapping("/transacciones/{id}")
    public WebTransaccionDetalle obtenerTransaccion(@PathVariable @Min(1) Integer id) {
        return service.obtenerTransaccion(id);
    }
}
