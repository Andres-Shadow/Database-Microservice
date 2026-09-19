package co.com.bancolombia.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class ChangeNameRouter {

    @Bean
    public RouterFunction<ServerResponse> changeNameRoute(ChangeNameHandler handler) {
        return route(POST("/api/v1/users/change-name"), handler::execute);
    }
}
