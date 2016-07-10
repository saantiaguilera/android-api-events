package com.santiago.event.anotation;

import android.support.annotation.NonNull;

import com.santiago.event.Event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotation for letting an event know which method invoke from a class when dispatching itself
 *
 * <strong> Since from Android N Repeatable anotation will be supported, I cant use repeatable anotations </strong
 * >
 * Created by saantiaguilera on 16/03/16.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventMethod {

    @NonNull Class<? extends Event> value() default Event.class;

}
