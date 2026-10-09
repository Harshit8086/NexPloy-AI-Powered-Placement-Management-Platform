package com.nexploy.campuslink.auth.dto;

public record LoginResponse(String accessToken, String refreshToken, String tokenType) {
}
