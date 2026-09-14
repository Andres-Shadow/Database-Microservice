package co.com.bancolombia.config;

import co.com.bancolombia.model.sql.gateways.SqlStatementParser;
import co.com.bancolombia.usecase.sql.DefaultSqlStatementParser;
import co.com.bancolombia.usecase.sql.SqlExecutionReportFormatter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SqlUseCaseConfig {

    @Bean
    public SqlStatementParser sqlStatementParser() {
        return new DefaultSqlStatementParser();
    }

    @Bean
    public SqlExecutionReportFormatter sqlExecutionReportFormatter() {
        return new SqlExecutionReportFormatter();
    }
}
