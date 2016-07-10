package com.santiago.event.anotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Simple anotation for knowing if the method to invoke should be done Async
 *
 * Created by saantiaguilera on 20/04/16.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventAsync {
}
