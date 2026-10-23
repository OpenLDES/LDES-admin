package org.openldes.ldesadmin.application.service;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class of the application ring that must be instantiated by the bootstrap module.
 *
 * <p>The application ring has no framework dependencies; this plain annotation lets the bootstrap
 * module pick up the services by classpath scanning instead of wiring each one by hand.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApplicationService {

}
