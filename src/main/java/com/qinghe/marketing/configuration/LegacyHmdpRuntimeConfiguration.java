package com.qinghe.marketing.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 历史点评模块的兼容入口。青禾默认运行时不会扫描旧业务 Bean；只有显式开启开关时，
 * 才恢复旧控制器、服务、拦截器、Mapper 和消息监听器，避免两套业务边界相互污染。
 */
@Configuration
@ConditionalOnProperty(name = "legacy.hmdp.endpoints-enabled", havingValue = "true")
@ComponentScan(basePackages = "com.hmdp")
@MapperScan("com.hmdp.mapper")
public class LegacyHmdpRuntimeConfiguration {
}
