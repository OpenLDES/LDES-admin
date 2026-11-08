package org.openldes.ldesadmin.adapter.out.sandbox;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.function.Consumer;
import org.slf4j.LoggerFactory;

/** Bridges logged-only upstream failures into sandbox errors without changing LDIO code. */
final class LdioFailureListener extends AppenderBase<ILoggingEvent> implements AutoCloseable {
  private final Logger logger;
  private final Consumer<String> failure;
  private final Thread owner;

  LdioFailureListener(Class<?> component, boolean currentThreadOnly, Consumer<String> failure) {
    this(component.getName(), currentThreadOnly, failure);
  }

  LdioFailureListener(String loggerName, boolean currentThreadOnly, Consumer<String> failure) {
    this.logger = (Logger) LoggerFactory.getLogger(loggerName);
    this.failure = failure;
    this.owner = currentThreadOnly ? Thread.currentThread() : null;
    setContext(logger.getLoggerContext());
    start();
    logger.addAppender(this);
  }

  @Override
  protected void append(ILoggingEvent event) {
    if (event.getLevel().isGreaterOrEqual(Level.ERROR)
        && (owner == null || owner == Thread.currentThread())) {
      failure.accept(event.getFormattedMessage());
    }
  }

  @Override
  public void close() {
    logger.detachAppender(this);
    stop();
  }
}
