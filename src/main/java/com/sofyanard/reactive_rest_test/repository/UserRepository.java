package com.sofyanard.reactive_rest_test.repository;

import com.sofyanard.reactive_rest_test.model.User;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

public interface UserRepository extends R2dbcRepository<User, String> {
    Mono<User> findByEmail(String email);
}
