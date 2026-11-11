package com.shyam0880.OrderService.serviceImplmentation;

import com.shyam0880.OrderService.dto.OrderResponse;
import com.shyam0880.OrderService.entity.Order;
import com.shyam0880.OrderService.exception.OrderNotFoundException;
import com.shyam0880.OrderService.exception.UserNotFoundException;
import com.shyam0880.OrderService.repository.OrderRepository;
import com.shyam0880.OrderService.service.OrderService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImplementation implements OrderService {

    private final OrderRepository orderRepository;
    private final UserServiceClient userServiceClient;

    public OrderServiceImplementation(OrderRepository orderRepository, UserServiceClient userServiceClient) {
        this.orderRepository = orderRepository;
        this.userServiceClient = userServiceClient;
    }

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository
                .findById(id)
                .orElseThrow(()-> new OrderNotFoundException("Order with id " + id + " not found"));
        return mapToResponse(order);
    }

    public OrderResponse createOrder(Order order) {
        if (!userServiceClient.userExists(order.getUserId())) {
            throw new UserNotFoundException("User with id " + order.getUserId() + " does not exist");
        }
        order.setOrderDate(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);
        return mapToResponse(savedOrder);
    }

    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new OrderNotFoundException("Order with id " + id + " not found");
        }
        orderRepository.deleteById(id);
    }

    private OrderResponse mapToResponse(Order order) {
        if (order == null) {
            return null;
        }
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getProductName(),
                order.getQuantity(),
                order.getTotalPrice(),
                order.getOrderDate()
        );
    }
}
