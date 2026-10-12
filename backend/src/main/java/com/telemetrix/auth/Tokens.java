package com.telemetrix.auth;

/** An access token and the refresh token that buys the next pair; `expiresIn`: the access token's seconds. */
public record Tokens(String accessToken, String refreshToken, long expiresIn) {
}
