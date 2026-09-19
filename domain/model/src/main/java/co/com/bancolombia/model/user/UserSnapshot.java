package co.com.bancolombia.model.user;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserSnapshot(
        String nombre,
        String email,
        Integer edad,
        Boolean activo,
        BigDecimal salario,
        LocalDateTime fechaRegistro) {
}
