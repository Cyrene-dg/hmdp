package com.qinghe.marketing.architecture;

import com.qinghe.marketing.shared.clock.BusinessClock;
import com.qinghe.marketing.shared.id.BusinessIdGenerator;
import com.qinghe.marketing.shared.trace.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;

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

    @Configuration
    @ComponentScan(basePackages = "com.qinghe.marketing")
    static class QingheOnlyConfiguration {
    }
}
