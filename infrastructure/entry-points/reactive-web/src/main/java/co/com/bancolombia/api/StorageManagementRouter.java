package co.com.bancolombia.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.DELETE;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class StorageManagementRouter {

    @Bean
    public RouterFunction<ServerResponse> storageManagementRoutes(StorageManagementHandler handler) {
        return route(GET("/api/v1/manage/s3/files"), handler::listFiles)
                .andRoute(GET("/api/v1/manage/s3/files/detail"), handler::getFileDetail)
                .andRoute(DELETE("/api/v1/manage/s3/files"), handler::deleteFile);
    }
}