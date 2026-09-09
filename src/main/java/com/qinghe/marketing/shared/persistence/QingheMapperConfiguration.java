package com.qinghe.marketing.shared.persistence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = "com.qinghe.marketing", annotationClass = QinghePersistenceMapper.class)
public class QingheMapperConfiguration {
}
