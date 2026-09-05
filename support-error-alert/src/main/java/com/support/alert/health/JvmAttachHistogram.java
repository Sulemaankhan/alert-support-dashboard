package com.support.alert.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Live {@code GC.class_histogram} via DiagnosticCommand, jcmd, or attach (same host). */
final class JvmAttachHistogram {

    private static final Logger log = LoggerFactory.getLogger(JvmAttachHistogram.class);
    private static final int HISTOGRAM_TIMEOUT_SEC = 45;

    private JvmAttachHistogram() {
    }

    static Optional<String> classHistogram(long pid) {
        if (pid <= 0) {
            return Optional.empty();
        }
        Optional<String> viaJcmd = jcmdHistogram(pid);
        if (viaJcmd.isPresent()) {
            return viaJcmd;
        }
        return attachHistogram(pid);
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

    private static Optional<String> jcmdHistogram(long pid) {
        Path jcmd = findJcmd();
        if (jcmd == null) {
            return Optional.empty();
        }
        try {
            Process process = new ProcessBuilder(jcmd.toString(), String.valueOf(pid), "GC.class_histogram")
                    .redirectErrorStream(true)
                    .start();
            CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
            boolean finished = process.waitFor(HISTOGRAM_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.debug("jcmd GC.class_histogram timed out for pid {}", pid);
                return Optional.empty();
            }
            String text = output.get(3, TimeUnit.SECONDS);
            if (process.exitValue() != 0 || text == null || text.isBlank() || looksLikeJcmdError(text)) {
                log.debug("jcmd histogram failed for pid {}: {}", pid, text == null ? "" : text.lines().findFirst().orElse(""));
                return Optional.empty();
            }
            return Optional.of(text);
        } catch (Exception ex) {
            log.debug("jcmd GC.class_histogram failed for pid {}: {}", pid, ex.toString());
            return Optional.empty();
        }
    }

    private static Optional<String> attachHistogram(long pid) {
        try {
            Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            Object vm = vmClass.getMethod("attach", String.class).invoke(null, String.valueOf(pid));
            try {
                Class<?> actual = vm.getClass();
                Optional<String> histo = invokeHeapHisto(actual, vm);
                if (histo.isPresent()) {
                    return histo;
                }
                histo = invokeStreamMethod(actual, vm, "executeJCmd", new Class<?>[] { String.class }, "GC.class_histogram");
                if (histo.isPresent()) {
                    return histo;
                }
                return invokeStringMethod(actual, vm, "executeCommand", "GC.class_histogram");
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

    private static Optional<String> invokeHeapHisto(Class<?> actual, Object vm) {
        try {
            Method method = actual.getMethod("heapHisto", Object[].class);
            method.setAccessible(true);
            Object result = method.invoke(vm, (Object) new Object[] {});
            return readPossibleStream(result);
        } catch (NoSuchMethodException ex) {
            return Optional.empty();
        } catch (Exception ex) {
            log.debug("heapHisto attach failed: {}", ex.toString());
            return Optional.empty();
        }
    }

    private static Optional<String> invokeStreamMethod(
            Class<?> actual,
            Object vm,
            String name,
            Class<?>[] types,
            Object... args) {
        try {
            Method method = actual.getMethod(name, types);
            method.setAccessible(true);
            return readPossibleStream(method.invoke(vm, args));
        } catch (NoSuchMethodException ex) {
            return Optional.empty();
        } catch (Exception ex) {
            log.debug("{} attach failed: {}", name, ex.toString());
            return Optional.empty();
        }
    }

    private static Optional<String> invokeStringMethod(Class<?> actual, Object vm, String name, String command) {
        try {
            Method method = actual.getMethod(name, String.class);
            method.setAccessible(true);
            Object result = method.invoke(vm, command);
            if (result instanceof String text && !text.isBlank()) {
                return Optional.of(text);
            }
            return readPossibleStream(result);
        } catch (NoSuchMethodException ex) {
            return Optional.empty();
        } catch (Exception ex) {
            log.debug("{} attach failed: {}", name, ex.toString());
            return Optional.empty();
        }
    }

    private static Optional<String> readPossibleStream(Object result) throws Exception {
        if (result instanceof String text && !text.isBlank()) {
            return Optional.of(text);
        }
        if (result instanceof InputStream stream) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String text = reader.lines().collect(Collectors.joining("\n"));
                return text.isBlank() ? Optional.empty() : Optional.of(text);
            }
        }
        return Optional.empty();
    }

    private static String readAll(InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception ex) {
            return "";
        }
    }

    private static Path findJcmd() {
        String javaHome = System.getProperty("java.home", "");
        String file = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? "jcmd.exe"
                : "jcmd";
        Path[] candidates = new Path[] {
                Path.of(javaHome, "bin", file),
                Path.of(javaHome).getParent() != null ? Path.of(javaHome).getParent().resolve("bin").resolve(file) : null,
                Optional.ofNullable(System.getenv("JAVA_HOME")).map(home -> Path.of(home, "bin", file)).orElse(null)
        };
        for (Path candidate : candidates) {
            if (candidate != null && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean looksLikeJcmdError(String text) {
        String first = text.lines().findFirst().orElse("").toLowerCase(Locale.ROOT);
        return first.contains("exception")
                || first.contains("not found")
                || first.contains("could not")
                || first.contains("error:");
    }
}
