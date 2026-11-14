package com.shyam0880.ApiGateway.controller;

import com.shyam0880.ApiGateway.dto.ErrorResponse;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    // @RequestMapping (not @GetMapping) so POST/PUT/DELETE forwarded here also work
    @RequestMapping("/user-service")
    public ResponseEntity<ErrorResponse> userServiceFallback(ServerWebExchange exchange) {
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "User Service is currently unavailable. Please try again later.",
                exchange);
    }

    @RequestMapping("/order-service")
    public ResponseEntity<ErrorResponse> orderServiceFallback(ServerWebExchange exchange) {
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "Order Service is currently unavailable. Please try again later.",
                exchange);
    }

    @RequestMapping("/service-not-found")
    public ResponseEntity<ErrorResponse> serviceNotFoundFallback(ServerWebExchange exchange) {
        return build(HttpStatus.NOT_FOUND,
                "No service is available for this request path.",
                exchange);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, ServerWebExchange exchange) {
        ErrorResponse body = new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                originalPath(exchange),
                LocalDateTime.now());
        return ResponseEntity.status(status).body(body);
    }

    // After forward:, the request path is /fallback/...; this gets the path the client called
    private String originalPath(ServerWebExchange exchange) {
        Set<URI> uris = exchange.getAttributeOrDefault(
                ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR,
                Collections.emptySet());
        return uris.stream()
                .findFirst()
                .map(URI::getPath)
                .orElse(exchange.getRequest().getURI().getPath());
    }
}