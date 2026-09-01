package com.support.alert.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Optional;

/** Invokes HotSpot {@code gcClassHistogram} on a JVM process id (same host). */
final class JvmAttachHistogram {

    private static final Logger log = LoggerFactory.getLogger(JvmAttachHistogram.class);

    private JvmAttachHistogram() {
    }

    static Optional<String> classHistogram(long pid) {
        if (pid <= 0) {
            return Optional.empty();
        }
        try {
            Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            Object vm = vmClass.getMethod("attach", String.class).invoke(null, String.valueOf(pid));
            try {
                Object result = vmClass.getMethod("executeCommand", String.class)
                        .invoke(vm, "GC.class_histogram");
                if (result instanceof String text && !text.isBlank()) {
                    return Optional.of(text);
                }
            } finally {
                vmClass.getMethod("detach").invoke(vm);
            }
        } catch (ClassNotFoundException ex) {
            log.debug("Attach API not available (tools.jar / jdk.attach): {}", ex.toString());
        } catch (Exception ex) {
            log.debug("GC.class_histogram attach failed for pid {}: {}", pid, ex.toString());
        }
        return Optional.empty();
    }

    static Optional<String> localDiagnosticHistogram() {
        try {
            var mbs = ManagementFactory.getPlatformMBeanServer();
            var name = new javax.management.ObjectName("com.sun.management:type=DiagnosticCommand");
            String[] args = new String[] {};
            String[] signature = new String[] { String[].class.getName() };
            Object result = mbs.invoke(name, "gcClassHistogram", new Object[] { args }, signature);
            if (result instanceof String text && !text.isBlank()) {
                return Optional.of(text);
            }
        } catch (Exception ex) {
            log.debug("Local gcClassHistogram failed: {}", ex.toString());
        }
        return Optional.empty();
    }
}
