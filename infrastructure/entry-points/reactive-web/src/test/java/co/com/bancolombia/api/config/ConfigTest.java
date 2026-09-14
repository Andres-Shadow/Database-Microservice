package co.com.bancolombia.api.config;

import co.com.bancolombia.api.SqlExecutionHandler;
import co.com.bancolombia.api.SqlExecutionRouter;
import co.com.bancolombia.model.sql.ExecutionStatus;
import co.com.bancolombia.usecase.sql.ExecuteSqlScriptUseCase;
import co.com.bancolombia.usecase.sql.SqlExecutionOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@WebFluxTest
@ContextConfiguration(classes = {
        SqlExecutionRouter.class,
        SqlExecutionHandler.class,
        CorsConfig.class,
        SecurityHeadersConfig.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost:4200,http://localhost:8080")
class ConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ExecuteSqlScriptUseCase useCase;

    @Test
    void securityHeadersShouldBePresent() {
        given(useCase.execute(anyString())).willReturn(Mono.just(new SqlExecutionOutcome(
                "script.sql", ExecutionStatus.SUCCESS, 1, 1, 0, 10L, "results/script-result.txt")));

        webTestClient.post()
                .uri("/api/v1/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fileName\":\"script.sql\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Content-Security-Policy",
                        "default-src 'self'; frame-ancestors 'self'; form-action 'self'")
                .expectHeader().valueEquals("Strict-Transport-Security",
                        "max-age=31536000; includeSubDomains; preload")
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().doesNotExist("Server")
                .expectHeader().valueEquals("Cache-Control", "no-store")
                .expectHeader().valueEquals("Pragma", "no-cache")
                .expectHeader().valueEquals("Referrer-Policy", "strict-origin-when-cross-origin");
    }
}
