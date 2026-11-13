package com.shyam0880.OrderService.serviceImplmentation;

import com.shyam0880.OrderService.exception.UserServiceErrorException;
import com.shyam0880.OrderService.exception.UserServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Component
public class UserServiceClient {

    private final WebClient webClient;

    public UserServiceClient(WebClient.Builder webClientBuilder,
                             @Value("${user-service.base-url}") String userServiceBaseUrl) {
        this.webClient = webClientBuilder.baseUrl(userServiceBaseUrl).build();
    }

    public boolean userExists(Long userId) {
        try {
            Boolean exists = webClient.get()
                    .uri("/api/users/{id}/exists", userId)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return Boolean.TRUE.equals(exists);
        } catch (WebClientResponseException e) {
            // User Service replied with 4xx/5xx
            throw new UserServiceErrorException("User Service returned an error: " + e.getMessage());
        } catch (WebClientRequestException e) {
            // connection refused, host unreachable, etc.
            throw new UserServiceUnavailableException("User Service is unavailable: " + e.getMessage());
        } catch (Exception e) {
            // reactor timeout is wrapped by block() with TimeoutException as the cause
            if (e.getCause() instanceof TimeoutException) {
                throw new UserServiceUnavailableException("User Service timed out: " + e.getMessage());
            }
            throw new UserServiceErrorException("User Service encountered an error: " + e.getMessage());
        }
    }
}