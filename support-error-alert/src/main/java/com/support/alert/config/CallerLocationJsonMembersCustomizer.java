package com.support.alert.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.util.StringUtils;

import org.springframework.core.env.Environment;

import java.util.function.Function;

/**
 * Adds dashboard-friendly fields to ECS JSON logs (logger, serviceName, functionPath, exception, etc.).
 */
public class CallerLocationJsonMembersCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    private final String serviceName;

    public CallerLocationJsonMembersCustomizer(Environment environment) {
        this.serviceName = environment.getProperty("spring.application.name", "jira-support-alert");
    }

    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        members.add("logger", event -> safeString(ILoggingEvent::getLoggerName, event)).whenNotNull();
        members.add("serviceName", event -> serviceName).whenNotNull();
        members.add("functionName", event -> safeCaller(event, StackTraceElement::getMethodName)).whenNotNull();
        members.add("functionPath", event -> safeFunctionPath(event)).whenNotNull();
        members.add("fileName", event -> safeCaller(event, StackTraceElement::getFileName)).whenNotNull();
        members.add("lineNumber", event -> safeCallerLine(event)).whenNotNull();
        members.add("filePath", event -> safeCaller(event, StackTraceElement::getClassName)).whenNotNull();
        members.add("exception", event -> safeString(this::exceptionSummary, event)).whenNotNull();
    }

    private String safeFunctionPath(ILoggingEvent event) {
        try {
            String functionName = caller(event, StackTraceElement::getMethodName);
            if (functionName == null || functionName.isBlank()) {
                return null;
            }
            String className = caller(event, StackTraceElement::getClassName);
            String fileName = caller(event, StackTraceElement::getFileName);
            Integer line = callerLine(event);
            String location = "";
            if (fileName != null && line != null) {
                location = "(" + fileName + ":" + line + ")";
            } else if (fileName != null) {
                location = "(" + fileName + ")";
            }
            if (className != null && !className.isBlank()) {
                return className + "." + functionName + location;
            }
            return functionName + location;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String safeCaller(ILoggingEvent event, Function<StackTraceElement, String> extractor) {
        try {
            return caller(event, extractor);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Integer safeCallerLine(ILoggingEvent event) {
        try {
            return callerLine(event);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static <T> String safeString(Function<ILoggingEvent, T> extractor, ILoggingEvent event) {
        try {
            T value = extractor.apply(event);
            return value != null ? String.valueOf(value) : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String caller(ILoggingEvent event, Function<StackTraceElement, String> extractor) {
        StackTraceElement element = firstCaller(event);
        return element != null ? extractor.apply(element) : null;
    }

    private Integer callerLine(ILoggingEvent event) {
        StackTraceElement element = firstCaller(event);
        return element != null ? element.getLineNumber() : null;
    }

    private static StackTraceElement firstCaller(ILoggingEvent event) {
        StackTraceElement[] data = event.getCallerData();
        return data != null && data.length > 0 ? data[0] : null;
    }

    private String exceptionSummary(ILoggingEvent event) {
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable == null) {
            return null;
        }
        String type = throwable.getClassName();
        String message = throwable.getMessage();
        if (!StringUtils.hasText(message)) {
            return type;
        }
        return type + ": " + message;
    }
}
