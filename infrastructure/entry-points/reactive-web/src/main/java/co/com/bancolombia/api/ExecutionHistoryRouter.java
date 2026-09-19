package co.com.bancolombia.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class ExecutionHistoryRouter {

    @Bean
    public RouterFunction<ServerResponse> executionHistoryRoute(ExecutionHistoryHandler handler) {
        return route(GET("/api/v1/manage/history"), handler::listHistory);
    }
}