package co.com.bancolombia.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.DELETE;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class DeleteUserRouter {

    @Bean
    public RouterFunction<ServerResponse> deleteUserRoute(DeleteUserHandler handler) {
        return route(DELETE("/api/v1/users/delete"), handler::execute);
    }
}
