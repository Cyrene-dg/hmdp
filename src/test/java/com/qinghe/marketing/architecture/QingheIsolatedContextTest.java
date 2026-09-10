package com.qinghe.marketing.architecture;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.clock.SystemBusinessClock;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.trace.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Arrays;

import com.qinghe.marketing.QingheMarketingApplication;
import com.qinghe.marketing.configuration.LegacyHmdpRuntimeConfiguration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class QingheIsolatedContextTest {

    @Test
    void shouldStartQingheFoundationWithoutLegacyApplicationOrMessageConsumers() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(QingheOnlyConfiguration.class);
            context.refresh();

            assertNotNull(context.getBean(BusinessClock.class));
            assertNotNull(context.getBean(BusinessIdGenerator.class));
            assertNotNull(context.getBean(TraceIdFilter.class));
            assertFalse(Arrays.stream(context.getBeanDefinitionNames())
                    .anyMatch(name -> name.contains("voucherOrderService")
                            || name.contains("rabbitMq")
                            || name.contains("hmDianPingApplication")),
                    "isolated Qinghe context must not start legacy services or RabbitMQ consumers");
        }
    }

    @Test
    void applicationShouldScanQingheOnlyAndKeepLegacyRuntimeOptIn() {
        SpringBootApplication application = QingheMarketingApplication.class
                .getAnnotation(SpringBootApplication.class);
        assertArrayEquals(new String[0], application.scanBasePackages());

        ConditionalOnProperty legacySwitch = LegacyHmdpRuntimeConfiguration.class
                .getAnnotation(ConditionalOnProperty.class);
        assertEquals("legacy.hmdp.endpoints-enabled", legacySwitch.name()[0]);
        assertEquals("true", legacySwitch.havingValue());
        assertFalse(legacySwitch.matchIfMissing());
    }

    @Configuration
    @Import({SystemBusinessClock.class, BusinessIdGenerator.class, TraceIdFilter.class})
    static class QingheOnlyConfiguration {
    }
}
