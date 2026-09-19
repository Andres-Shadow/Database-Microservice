package co.com.bancolombia.model.user.gateways;

import reactor.core.publisher.Mono;

public interface BatchFileStorage {

    Mono<String> read(String fileName);

    Mono<String> saveResult(String sourceFileName, String content);

    Mono<Void> delete(String fileName);
}
