package co.com.bancolombia.r2dbc;

import co.com.bancolombia.model.sql.exceptions.SqlExecutionInfrastructureException;
import co.com.bancolombia.model.user.UserSnapshot;
import co.com.bancolombia.model.user.gateways.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostgresUserRepository implements UserRepository {

    private final DatabaseClient databaseClient;

    @Override
    public Mono<UserSnapshot> findUser(int id, String name, String email) {
        return databaseClient.sql(
                        "SELECT nombre, email, edad, activo, salario, fecha_registro " +
                                "FROM usuarios WHERE id = :id AND nombre = :nombre AND email = :email")
                .bind("id", (long) id)
                .bind("nombre", name)
                .bind("email", email)
                .fetch()
                .all()
                .map(row -> new UserSnapshot(
                        (String) row.get("nombre"),
                        (String) row.get("email"),
                        row.get("edad") != null ? ((Number) row.get("edad")).intValue() : null,
                        (Boolean) row.get("activo"),
                        row.get("salario") != null ? new BigDecimal(row.get("salario").toString()) : null,
                        row.get("fecha_registro") != null
                                ? toLocalDateTime(row.get("fecha_registro"))
                                : null))
                .singleOrEmpty()
                .onErrorMap(this::isInfrastructureError, error -> new SqlExecutionInfrastructureException(
                        "Unable to query user: " + error.getMessage(), error));
    }

    @Override
    public Mono<Long> updateUser(int id, String newName) {
        return databaseClient.sql("UPDATE usuarios SET nombre = :nombre WHERE id = :id")
                .bind("nombre", newName)
                .bind("id", (long) id)
                .fetch()
                .rowsUpdated()
                .doOnSuccess(rows -> log.info("Updated user id={}, rows affected={}", id, rows))
                .onErrorMap(this::isInfrastructureError, error -> new SqlExecutionInfrastructureException(
                        "Unable to update user: " + error.getMessage(), error));
    }

    @Override
    public Mono<Long> deleteUser(int id, String name, String email) {
        return databaseClient.sql(
                        "DELETE FROM usuarios WHERE id = :id AND nombre = :nombre AND email = :email")
                .bind("id", (long) id)
                .bind("nombre", name)
                .bind("email", email)
                .fetch()
                .rowsUpdated()
                .doOnSuccess(rows -> log.info("Deleted user id={}, rows affected={}", id, rows))
                .onErrorMap(this::isInfrastructureError, error -> new SqlExecutionInfrastructureException(
                        "Unable to delete user: " + error.getMessage(), error));
    }

    @Override
    public Mono<Void> insertUser(UserSnapshot user) {
        return databaseClient.sql(
                        "INSERT INTO usuarios (nombre, email, edad, activo, salario, fecha_registro) " +
                                "VALUES (:nombre, :email, :edad, :activo, :salario, :fechaRegistro)")
                .bind("nombre", user.nombre())
                .bind("email", user.email())
                .bind("edad", user.edad())
                .bind("activo", user.activo())
                .bind("salario", user.salario())
                .bind("fechaRegistro", user.fechaRegistro())
                .fetch()
                .rowsUpdated()
                .then()
                .doOnSuccess(v -> log.info("Inserted user from backup: email={}", user.email()))
                .onErrorMap(this::isInfrastructureError, error -> new SqlExecutionInfrastructureException(
                        "Unable to insert user: " + error.getMessage(), error));
    }

    private boolean isInfrastructureError(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof org.springframework.dao.DataAccessResourceFailureException
                    || current instanceof java.net.ConnectException
                    || current instanceof java.net.SocketException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (value instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return LocalDateTime.parse(value.toString());
    }
}
