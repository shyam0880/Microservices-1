package com.shyam0880.OrderService.serviceImplmentation;

import com.shyam0880.OrderService.exception.UserServiceErrorException;
import com.shyam0880.OrderService.exception.UserServiceUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
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
        } catch (TimeoutException | java.net.ConnectException e) {
            throw new UserServiceUnavailableException("User Service is unavailable: " + e.getMessage());
        } catch (WebClientResponseException e) {
            throw new UserServiceErrorException("User Service returned an error: " + e.getMessage());
        } catch (Exception e) {
            // covers reactor timeout and connection refused wrapped in runtime exceptions
            if (e.getCause() instanceof TimeoutException || e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                throw new UserServiceUnavailableException("User Service is unavailable: " + e.getMessage());
            }
            throw new UserServiceErrorException("User Service encountered an error: " + e.getMessage());
        }
    }
}
