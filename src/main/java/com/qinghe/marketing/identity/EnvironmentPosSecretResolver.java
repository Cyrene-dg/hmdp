package com.qinghe.marketing.identity;

import com.qinghe.marketing.shared.error.QingheBusinessException;
import com.qinghe.marketing.shared.error.QingheErrorCode;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** Local/integration secret adapter. Vault and KMS references require their deployment-specific adapter. */
@Component
public class EnvironmentPosSecretResolver implements PosSecretResolver {

    private static final String PREFIX = "secret://env/";
    private static final Pattern ENVIRONMENT_NAME = Pattern.compile("[A-Z][A-Z0-9_]{2,127}");
    private final Environment environment;

    public EnvironmentPosSecretResolver(Environment environment) {
        this.environment = environment;
    }

    @Override
    public char[] resolve(String secretReference) {
        if (secretReference == null || !secretReference.startsWith(PREFIX)) {
            throw new QingheBusinessException(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                    "POS secret provider is not configured for this reference");
        }
        String environmentName = secretReference.substring(PREFIX.length());
        if (!ENVIRONMENT_NAME.matcher(environmentName).matches()) {
            throw new QingheBusinessException(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                    "POS secret reference is invalid");
        }
        String secret = environment.getProperty(environmentName);
        if (secret == null || secret.length() < 16) {
            throw new QingheBusinessException(QingheErrorCode.TEMPORARY_UNAVAILABLE,
                    "POS secret is unavailable");
        }
        return secret.toCharArray();
    }
}
