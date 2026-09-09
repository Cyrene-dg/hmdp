package com.qinghe.marketing.identity;

public interface PosSecretResolver {

    char[] resolve(String secretReference);
}
