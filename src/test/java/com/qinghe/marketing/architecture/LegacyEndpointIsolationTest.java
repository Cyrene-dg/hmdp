package com.qinghe.marketing.architecture;

import com.hmdp.controller.BlogCommentsController;
import com.hmdp.controller.BlogController;
import com.hmdp.controller.FollowController;
import com.hmdp.controller.ShopController;
import com.hmdp.controller.ShopTypeController;
import com.hmdp.controller.UploadController;
import com.hmdp.controller.UserController;
import com.hmdp.controller.VoucherController;
import com.hmdp.controller.VoucherOrderController;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class LegacyEndpointIsolationTest {

    private static final String SWITCH = "legacy.hmdp.endpoints-enabled";
    private static final List<Class<?>> LEGACY_CONTROLLERS = Arrays.asList(
            BlogCommentsController.class,
            BlogController.class,
            FollowController.class,
            ShopController.class,
            ShopTypeController.class,
            UploadController.class,
            UserController.class,
            VoucherController.class,
            VoucherOrderController.class
    );

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(LegacyControllersConfiguration.class)
            .withBean(IBlogService.class, () -> mock(IBlogService.class))
            .withBean(IFollowService.class, () -> mock(IFollowService.class))
            .withBean(IShopService.class, () -> mock(IShopService.class))
            .withBean(IShopTypeService.class, () -> mock(IShopTypeService.class))
            .withBean(IUserInfoService.class, () -> mock(IUserInfoService.class))
            .withBean(IUserService.class, () -> mock(IUserService.class))
            .withBean(IVoucherService.class, () -> mock(IVoucherService.class))
            .withBean(IVoucherOrderService.class, () -> mock(IVoucherOrderService.class));

    @Test
    void shouldNotRegisterLegacyControllersByDefault() {
        contextRunner.run(context -> LEGACY_CONTROLLERS.forEach(controller ->
                assertFalse(context.getBeansOfType(controller).size() > 0,
                        controller.getSimpleName() + " should be disabled by default")));
    }

    @Test
    void shouldRegisterLegacyControllersOnlyWhenExplicitlyEnabled() {
        contextRunner.withPropertyValues(SWITCH + "=true")
                .run(context -> LEGACY_CONTROLLERS.forEach(controller ->
                        assertEquals(1, context.getBeansOfType(controller).size(),
                                controller.getSimpleName() + " should be enabled explicitly")));
    }

    @Test
    void everyLegacyControllerShouldUseTheSameOptInSwitch() {
        LEGACY_CONTROLLERS.forEach(controller -> {
            ConditionalOnProperty condition = controller.getAnnotation(ConditionalOnProperty.class);
            assertEquals(SWITCH, condition.name()[0], controller.getSimpleName());
            assertEquals("true", condition.havingValue(), controller.getSimpleName());
            assertFalse(condition.matchIfMissing(), controller.getSimpleName());
        });
    }

    @Configuration
    @Import({
            BlogCommentsController.class,
            BlogController.class,
            FollowController.class,
            ShopController.class,
            ShopTypeController.class,
            UploadController.class,
            UserController.class,
            VoucherController.class,
            VoucherOrderController.class
    })
    static class LegacyControllersConfiguration {
    }
}
