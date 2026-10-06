package cl.duoc.bancoxyz.bff.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class RestClientConfig {

    // Eureka necesita HTTP directo para acceder al registro antes de descubrir servicios.
    @Bean
    @Primary
    @Scope("prototype")
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean("accountRestClientBuilder")
    @Profile("!cloud")
    RestClient.Builder localAccountRestClientBuilder(
            @Value("${services.account.connect-timeout:1s}") Duration connect,
            @Value("${services.account.read-timeout:2s}") Duration read) {
        return accountBuilder(connect, read);
    }

    @Bean("accountRestClientBuilder")
    @Profile("cloud")
    @LoadBalanced
    RestClient.Builder discoveryRestClientBuilder(
            @Value("${services.account.connect-timeout:1s}") Duration connect,
            @Value("${services.account.read-timeout:2s}") Duration read) {
        return accountBuilder(connect, read);
    }

    private RestClient.Builder accountBuilder(Duration connect, Duration read) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(connect).build());
        factory.setReadTimeout(read);
        return RestClient.builder().requestFactory(factory);
    }
}
