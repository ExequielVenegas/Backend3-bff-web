package cl.duoc.bancoxyz.bff.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
            // API exclusivamente Bearer: no acepta cookies de sesion ni Basic.
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/web/cuentas-modernas/importaciones","/api/web/cuentas-modernas/importaciones/**").hasAuthority("SCOPE_web.accounts.import")
                .requestMatchers(HttpMethod.GET, "/api/web/pagos", "/api/web/pagos/**").hasAuthority("SCOPE_web.payments.read")
                .requestMatchers(HttpMethod.POST, "/api/web/pagos").hasAuthority("SCOPE_web.payments.write")
                .requestMatchers(HttpMethod.GET, "/api/web/cuentas-modernas", "/api/web/cuentas-modernas/**").hasAuthority("SCOPE_web.accounts.read")
                .requestMatchers(HttpMethod.POST, "/api/web/cuentas-modernas", "/api/web/cuentas-modernas/*/cierre").hasAuthority("SCOPE_web.accounts.write")
                .requestMatchers(HttpMethod.PUT, "/api/web/cuentas-modernas/*").hasAuthority("SCOPE_web.accounts.write")
                .requestMatchers(HttpMethod.GET, "/api/web/clientes", "/api/web/clientes/**").hasAuthority("SCOPE_web.customers.read")
                .requestMatchers(HttpMethod.POST, "/api/web/clientes").hasAuthority("SCOPE_web.customers.write")
                .requestMatchers(HttpMethod.PUT, "/api/web/clientes/*").hasAuthority("SCOPE_web.customers.write")
                .requestMatchers(HttpMethod.POST, "/api/web/cuentas/*/solicitudes-estado").hasAuthority("SCOPE_web.write")
                .requestMatchers(HttpMethod.GET, "/api/web/**").hasAuthority("SCOPE_web.read")
                .requestMatchers(HttpMethod.GET, "/actuator/info").hasAuthority("SCOPE_web.info")
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }
}

