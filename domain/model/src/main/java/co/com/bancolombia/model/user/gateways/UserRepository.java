package co.com.bancolombia.model.user.gateways;

import co.com.bancolombia.model.user.UserSnapshot;
import reactor.core.publisher.Mono;

public interface UserRepository {

    Mono<UserSnapshot> findUser(int id, String name, String email);

    Mono<Long> updateUser(int id, String newName);

    Mono<Long> deleteUser(int id, String name, String email);

    Mono<Void> insertUser(UserSnapshot user);
}
