package com.garage.garageapi.admin.stock.dto;

import com.garage.garageapi.user.entity.User;

public record AdminStockPerformedByResponse(Long userId, String name, String email) {
    public static AdminStockPerformedByResponse from(User user) {
        return user == null ? null : new AdminStockPerformedByResponse(
                user.getId(), user.getName(), user.getEmail());
    }
}
