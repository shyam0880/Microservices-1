package com.shyam0880.OrderService.service;

import com.shyam0880.OrderService.dto.OrderResponse;
import com.shyam0880.OrderService.entity.Order;

import java.util.List;

public interface OrderService {

    List<OrderResponse> getAllOrders();

    OrderResponse getOrderById(Long id);

    OrderResponse createOrder(Order order);

    void deleteOrder(Long id);
}
