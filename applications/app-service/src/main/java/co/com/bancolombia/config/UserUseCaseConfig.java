package co.com.bancolombia.config;

import co.com.bancolombia.model.user.gateways.ChangeNameParser;
import co.com.bancolombia.model.user.gateways.DeleteUserParser;
import co.com.bancolombia.usecase.user.BatchOperationReportFormatter;
import co.com.bancolombia.usecase.user.DefaultChangeNameParser;
import co.com.bancolombia.usecase.user.DefaultDeleteUserParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserUseCaseConfig {

    @Bean
    public ChangeNameParser changeNameParser() {
        return new DefaultChangeNameParser();
    }

    @Bean
    public DeleteUserParser deleteUserParser() {
        return new DefaultDeleteUserParser();
    }

    @Bean
    public BatchOperationReportFormatter batchOperationReportFormatter() {
        return new BatchOperationReportFormatter();
    }
}
