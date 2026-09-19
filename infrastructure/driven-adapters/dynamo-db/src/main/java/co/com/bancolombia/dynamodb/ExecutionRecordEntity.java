package co.com.bancolombia.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
public class ExecutionRecordEntity {

    private String executionId;
    private String operationType;
    private String sourceFile;
    private String status;
    private Integer totalEntries;
    private Integer successfulEntries;
    private Integer failedEntries;
    private Long executionTimeMs;
    private String executedAt;

    public ExecutionRecordEntity() {
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("execution_id")
    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    @DynamoDbAttribute("operation_type")
    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    @DynamoDbAttribute("source_file")
    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    @DynamoDbAttribute("status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @DynamoDbAttribute("total_entries")
    public Integer getTotalEntries() {
        return totalEntries;
    }

    public void setTotalEntries(Integer totalEntries) {
        this.totalEntries = totalEntries;
    }

    @DynamoDbAttribute("successful_entries")
    public Integer getSuccessfulEntries() {
        return successfulEntries;
    }

    public void setSuccessfulEntries(Integer successfulEntries) {
        this.successfulEntries = successfulEntries;
    }

    @DynamoDbAttribute("failed_entries")
    public Integer getFailedEntries() {
        return failedEntries;
    }

    public void setFailedEntries(Integer failedEntries) {
        this.failedEntries = failedEntries;
    }

    @DynamoDbAttribute("execution_time_ms")
    public Long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(Long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    @DynamoDbAttribute("executed_at")
    public String getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(String executedAt) {
        this.executedAt = executedAt;
    }
}