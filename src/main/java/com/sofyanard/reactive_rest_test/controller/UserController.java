package com.sofyanard.reactive_rest_test.controller;

import com.sofyanard.reactive_rest_test.exceptions.EmailUniquenessException;
import com.sofyanard.reactive_rest_test.model.User;
import com.sofyanard.reactive_rest_test.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/users")
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    @Cacheable( "users")
    public Flux<User> getAllUsers() {
        long start = System.currentTimeMillis();
        return userRepository.findAll()
                .doOnSubscribe(subscription -> log.debug("Subscribed to User stream!"))
                .doOnNext(user -> log.debug("Processed User: {} in {} ms", user.name(), System.currentTimeMillis() - start))
                .doOnComplete(() -> log.info("Finished streaming users for getAllUsers in {} ms", System.currentTimeMillis() - start));
    }

    @GetMapping("/{id}")
    @Cacheable( "users")
    public Mono<User> getUserById(@PathVariable String id) {
        return userRepository.findById(id);
    }

    @PostMapping
    @CacheEvict(value = "users", allEntries = true)
    public Mono<ResponseEntity<User>> createUser(@RequestBody User user) {
        return userRepository.findByEmail(user.email())
                .flatMap(existingUser -> Mono.error(new EmailUniquenessException("Email already exists!")))
                .then(userRepository.save(user)) // Save the new user if the email doesn't exist
                .map(ResponseEntity::ok) // Map the saved user to a ResponseEntity
                .doOnNext(savedUser -> System.out.println("New user created: " + savedUser)) // Logging or further action
                .onErrorResume(e -> { // Handling errors, such as email uniqueness violation
                    System.out.println("An exception has occurred: " + e.getMessage());
                    if (e instanceof EmailUniquenessException) {
                        return Mono.just(ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .build());
                    } else {
                        return Mono.just(ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .build());
                    }
                });
    }

    @DeleteMapping("/{id}")
    @CacheEvict(value = "users", key = "#id")
    public Mono<Void> deleteUser(@PathVariable String id) {
        return userRepository.deleteById(id);
    }

    @GetMapping("/stream")
    @Cacheable( "users")
    public Flux<User> streamUsers() {
        long start = System.currentTimeMillis();
        return userRepository.findAll()
                .onBackpressureBuffer()  // Buffer strategy for back-pressure
                .doOnNext(user -> log.debug("Processed User: {} in {} ms", user.name(), System.currentTimeMillis() - start))
                .doOnError(error -> log.error("Error streaming users", error))
                .doOnComplete(() -> log.info("Finished streaming users for streamUsers in {} ms", System.currentTimeMillis() - start));
    }
}
