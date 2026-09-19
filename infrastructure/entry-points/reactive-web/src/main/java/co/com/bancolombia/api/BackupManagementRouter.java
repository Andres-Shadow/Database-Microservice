package co.com.bancolombia.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.DELETE;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

@Configuration
public class BackupManagementRouter {

    @Bean
    public RouterFunction<ServerResponse> backupManagementRoutes(BackupManagementHandler handler) {
        return route(GET("/api/v1/manage/dynamo/backups"), handler::listBackups)
                .andRoute(GET("/api/v1/manage/dynamo/backups/{backupId}"), handler::getBackup)
                .andRoute(DELETE("/api/v1/manage/dynamo/backups/{backupId}"), handler::deleteBackup);
    }
}