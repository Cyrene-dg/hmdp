package com.qinghe.marketing.shared.persistence;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks MyBatis mappers owned by the Qinghe module. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface QinghePersistenceMapper {
}
