package com.support.alert.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds a same-host JVM PID so HealthCheck can take a class histogram. */
final class SameHostJvmLocator {

    private static final Logger log = LoggerFactory.getLogger(SameHostJvmLocator.class);
    private static final Pattern NETSTAT_LISTEN = Pattern.compile(
            "^\\s*TCP\\S*\\s+\\S+:(\\d+)\\s+\\S+\\s+LISTENING\\s+(\\d+)\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SS_LISTEN = Pattern.compile(
            "LISTEN\\s+.+?:(\\d+)\\s+.+pid=(\\d+)",
            Pattern.CASE_INSENSITIVE);

    private SameHostJvmLocator() {
    }

    static long resolve(HealthTarget target, long metricPid) {
        long self = ProcessHandle.current().pid();
        if (metricPid > 0 && metricPid != self) {
            return metricPid;
        }
        if (!isSameHost(target.normalizedActuatorBaseUrl())) {
            return 0L;
        }
        int port = portOf(target.normalizedActuatorBaseUrl());
        if (port > 0) {
            long fromPort = listeningPid(port);
            if (fromPort > 0 && fromPort != self) {
                return fromPort;
            }
        }
        return matchAttachableVm(target, self);
    }

    static boolean isSameHost(String actuatorUrl) {
        if (actuatorUrl == null || actuatorUrl.isBlank()) {
            return true;
        }
        try {
            URI uri = URI.create(actuatorUrl);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return true;
            }
            if ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host)) {
                return true;
            }
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
                return true;
            }
            return address.equals(InetAddress.getLocalHost());
        } catch (Exception ex) {
            log.debug("Could not decide if {} is same-host: {}", actuatorUrl, ex.toString());
            return false;
        }
    }

    static int portOf(String actuatorUrl) {
        if (actuatorUrl == null || actuatorUrl.isBlank()) {
            return -1;
        }
        try {
            URI uri = URI.create(actuatorUrl);
            int port = uri.getPort();
            if (port > 0) {
                return port;
            }
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return 443;
            }
            if ("http".equalsIgnoreCase(uri.getScheme())) {
                return 80;
            }
        } catch (Exception ex) {
            log.debug("Could not parse port from {}: {}", actuatorUrl, ex.toString());
        }
        return -1;
    }

    private static long listeningPid(int port) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> command = windows
                ? List.of("netstat", "-ano", "-p", "TCP")
                : List.of("ss", "-lptn");
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            long pid = 0;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    long found = windows ? parseNetstat(line, port) : parseSs(line, port);
                    if (found > 0) {
                        pid = found;
                        break;
                    }
                }
            }
            process.destroy();
            return pid;
        } catch (Exception ex) {
            log.debug("Listening PID lookup for port {} failed: {}", port, ex.toString());
            return 0L;
        }
    }

    private static long parseNetstat(String line, int port) {
        Matcher matcher = NETSTAT_LISTEN.matcher(line);
        if (!matcher.matches()) {
            // Windows netstat can look like: TCP    0.0.0.0:8082    0.0.0.0:0    LISTENING    1234
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 5 || !parts[0].toUpperCase(Locale.ROOT).startsWith("TCP")) {
                return 0L;
            }
            if (!parts[3].equalsIgnoreCase("LISTENING")) {
                return 0L;
            }
            if (!addressHasPort(parts[1], port)) {
                return 0L;
            }
            try {
                return Long.parseLong(parts[parts.length - 1]);
            } catch (NumberFormatException ex) {
                return 0L;
            }
        }
        if (Integer.parseInt(matcher.group(1)) != port) {
            return 0L;
        }
        return Long.parseLong(matcher.group(2));
    }

    private static boolean addressHasPort(String address, int port) {
        int colon = address.lastIndexOf(':');
        if (colon < 0) {
            return false;
        }
        try {
            return Integer.parseInt(address.substring(colon + 1)) == port;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static long parseSs(String line, int port) {
        Matcher matcher = SS_LISTEN.matcher(line);
        if (matcher.find() && Integer.parseInt(matcher.group(1)) == port) {
            return Long.parseLong(matcher.group(2));
        }
        return 0L;
    }

    private static long matchAttachableVm(HealthTarget target, long selfPid) {
        List<String> needles = new ArrayList<>();
        addNeedle(needles, target.applicationId());
        addNeedle(needles, target.serviceName());
        addNeedle(needles, target.applicationName());
        if (needles.isEmpty()) {
            return 0L;
        }
        try {
            Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            Class<?> descClass = Class.forName("com.sun.tools.attach.VirtualMachineDescriptor");
            @SuppressWarnings("unchecked")
            List<Object> vms = (List<Object>) vmClass.getMethod("list").invoke(null);
            List<Long> matches = new ArrayList<>();
            for (Object desc : vms) {
                String id = String.valueOf(descClass.getMethod("id").invoke(desc));
                String name = String.valueOf(descClass.getMethod("displayName").invoke(desc)).toLowerCase(Locale.ROOT);
                long pid;
                try {
                    pid = Long.parseLong(id);
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (pid == selfPid) {
                    continue;
                }
                for (String needle : needles) {
                    if (name.contains(needle)) {
                        matches.add(pid);
                        break;
                    }
                }
            }
            if (matches.size() == 1) {
                return matches.get(0);
            }
        } catch (Exception ex) {
            log.debug("VirtualMachine.list match failed: {}", ex.toString());
        }
        return 0L;
    }

    private static void addNeedle(List<String> needles, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String needle = value.trim().toLowerCase(Locale.ROOT);
        if (needle.length() < 3 || needles.contains(needle)) {
            return;
        }
        needles.add(needle);
    }
}
