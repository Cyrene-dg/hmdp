package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokenDTO {
    private String tokenType;
    private String accessToken;
    private Long accessTokenTtlMinutes;
    private String refreshToken;
    private Long refreshTokenTtlMinutes;
}
