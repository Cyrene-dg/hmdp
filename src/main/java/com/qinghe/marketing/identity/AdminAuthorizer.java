package com.qinghe.marketing.identity;

public interface AdminAuthorizer {
    AdminPrincipal require(String authorizationHeader, String permission);
}
