package co.com.bancolombia.api;

import co.com.bancolombia.model.sql.ExecutionStatus;
import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import co.com.bancolombia.model.sql.exceptions.SqlScriptNotFoundException;
import co.com.bancolombia.usecase.sql.ExecuteSqlScriptUseCase;
import co.com.bancolombia.usecase.sql.SqlExecutionOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@WebFluxTest
@ContextConfiguration(classes = {SqlExecutionRouter.class, SqlExecutionHandler.class})
class SqlExecutionRouterTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ExecuteSqlScriptUseCase useCase;

    @Test
    void shouldExecuteSqlScriptSuccessfully() {
        SqlExecutionOutcome outcome = new SqlExecutionOutcome(
                "approved/update-customers.sql", ExecutionStatus.SUCCESS, 3, 3, 0, 42L,
                "results/update-customers-result.txt");
        given(useCase.execute(anyString())).willReturn(Mono.just(outcome));

        webTestClient.post()
                .uri("/api/v1/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fileName\":\"approved/update-customers.sql\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sourceFile").isEqualTo("approved/update-customers.sql")
                .jsonPath("$.status").isEqualTo("SUCCESS")
                .jsonPath("$.totalStatements").isEqualTo(3)
                .jsonPath("$.successfulStatements").isEqualTo(3)
                .jsonPath("$.failedStatements").isEqualTo(0)
                .jsonPath("$.resultFile").isEqualTo("results/update-customers-result.txt");
    }

    @Test
    void shouldRejectBlankFileName() {
        webTestClient.post()
                .uri("/api/v1/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fileName\":\"\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldRejectWrongExtension() {
        webTestClient.post()
                .uri("/api/v1/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fileName\":\"script.txt\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldRejectPathTraversal() {
        webTestClient.post()
                .uri("/api/v1/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fileName\":\"../secret.sql\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldReturnNotFoundWhenScriptIsMissing() {
        given(useCase.execute(anyString()))
                .willReturn(Mono.error(new SqlScriptNotFoundException("SQL script not found in S3: missing.sql")));

        webTestClient.post()
                .uri("/api/v1/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fileName\":\"missing.sql\"}")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldReturnServerErrorOnInfrastructureFailure() {
        given(useCase.execute(anyString()))
                .willReturn(Mono.error(new SqlExecutionInfrastructureException("db down", new RuntimeException("boom"))));

        webTestClient.post()
                .uri("/api/v1/sql/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"fileName\":\"script.sql\"}")
                .exchange()
                .expectStatus().is5xxServerError();
    }
}
