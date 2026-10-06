package cl.duoc.bancoxyz.bff.common.config;

import cl.duoc.bancoxyz.bff.common.client.AccountServiceClient;
import cl.duoc.bancoxyz.bff.common.exception.*;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import static org.junit.jupiter.api.Assertions.*;

class AccountResilienceTests {
    private AccountResilience policy(int slots) {
        return new AccountResilience(2, Duration.ofMillis(1), Duration.ofMillis(100), slots);
    }
    private RuntimeException failure() {
        return new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test void retriesTransientFailureAndReturnsRealResult() {
        var policy = policy(10);
        var calls = new AtomicInteger();
        String result = policy.execute(() -> {
            if (calls.incrementAndGet() == 1) throw failure();
            return "real";
        });
        assertEquals("real", result);
        assertEquals(2, calls.get());
        assertEquals(1, policy.circuit().getMetrics().getNumberOfSuccessfulCalls());
    }

    @Test void missingResourceIsNotRetriedOrCountedAsBackendFailure() {
        var policy = policy(10);
        var calls = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            assertThrows(ResourceNotFoundException.class, () -> policy.execute(() -> {
                calls.incrementAndGet(); throw new ResourceNotFoundException("ausente");
            }));
        }
        assertEquals(10, calls.get());
        assertEquals(0, policy.circuit().getMetrics().getNumberOfBufferedCalls());
        assertEquals(CircuitBreaker.State.CLOSED, policy.circuit().getState());
    }

    @Test void opensAfterFailuresRejectsWithoutCallingAndRecovers() throws Exception {
        var policy = policy(10);
        var calls = new AtomicInteger();
        for (int i = 0; i < 4; i++) {
            assertThrows(BackendUnavailableException.class, () -> policy.execute(() -> {
                calls.incrementAndGet(); throw failure();
            }));
        }
        assertEquals(8, calls.get());
        assertEquals(CircuitBreaker.State.OPEN, policy.circuit().getState());
        assertThrows(BackendUnavailableException.class, () -> policy.execute(() -> calls.incrementAndGet()));
        assertEquals(8, calls.get());
        Thread.sleep(150);
        assertEquals("ok", policy.execute(() -> "ok"));
        assertEquals(CircuitBreaker.State.HALF_OPEN, policy.circuit().getState());
        assertEquals("ok", policy.execute(() -> "ok"));
        assertEquals(CircuitBreaker.State.CLOSED, policy.circuit().getState());
    }

    @Test void rejectsConcurrencyExcessAndReleasesPermit() throws Exception {
        var policy = policy(1);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> policy.execute(() -> {
                entered.countDown();
                try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new RuntimeException(ex); }
                return "first";
            }));
            try {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                assertThrows(BackendUnavailableException.class, () -> policy.execute(() -> "excess"));
                assertEquals(0, policy.circuit().getMetrics().getNumberOfFailedCalls());
            } finally { release.countDown(); }
            assertEquals("first", first.get(2, TimeUnit.SECONDS));
            assertEquals("available", policy.execute(() -> "available"));
        }
    }

    @Test void unavailableDiscoveryIsRetriedAndTranslated() {
        var policy = policy(10);
        var calls = new AtomicInteger();
        assertThrows(BackendUnavailableException.class, () -> policy.execute(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("No instances available for account-service");
        }));
        assertEquals(2, calls.get());
    }

    @Test void realHttpTimeoutIsBoundedAndRetried() throws Exception {
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(pool);
            server.createContext("/internal/accounts/1", exchange -> {
                calls.incrementAndGet();
                try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                finally { exchange.close(); }
            });
            server.start();
            try {
                var builder = new RestClientConfig().localAccountRestClientBuilder(Duration.ofMillis(200), Duration.ofMillis(150));
                var client = new AccountServiceClient(builder, "http://127.0.0.1:" + server.getAddress().getPort(), policy(10));
                assertTimeout(Duration.ofSeconds(2), () ->
                    assertThrows(BackendUnavailableException.class, () -> client.findAccount(1)));
                assertEquals(2, calls.get());
            } finally { release.countDown(); server.stop(0); }
        }
    }
}
