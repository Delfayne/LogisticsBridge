package com.tom.logisticsbridge.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// https://github.com/embeddedt/VintageFix/blob/15291a5829ead82cf7dde9b482a2a2cc95ea45b7/src/main/java/org/embeddedt/vintagefix/annotation/LateMixin.java
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface LateMixin {
}