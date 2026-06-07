package com.support.alert.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Loads {@code application-local.properties} from common locations (IDE working directory,
 * module root). Google OAuth client ID is configured via startup args or {@code GOOGLE_CLIENT_ID}.
 */
public class LocalSecretsPropertiesLoader implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(LocalSecretsPropertiesLoader.class);

    private static final String SOURCE_NAME = "localSecretsProperties";
    private static final List<String> LOCAL_FILES = List.of("application-local.properties");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> merged = new LinkedHashMap<>();
        List<String> loadedFrom = new ArrayList<>();

        for (String fileName : LOCAL_FILES) {
            for (Path path : candidatePaths(fileName)) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                if (loadInto(merged, path)) {
                    loadedFrom.add(path.toAbsolutePath().normalize().toString());
                    break;
                }
            }
        }

        if (merged.isEmpty()) {
            return;
        }

        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, merged));
        log.info("Loaded local properties from: {}", String.join(", ", loadedFrom));
    }

    private static List<Path> candidatePaths(String fileName) {
        String userDir = System.getProperty("user.dir", ".");
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of(userDir, fileName));
        candidates.add(Path.of(userDir, "support-error-alert", fileName));
        try {
            URL codeUrl = LocalSecretsPropertiesLoader.class.getProtectionDomain().getCodeSource().getLocation();
            Path classes = Path.of(codeUrl.toURI());
            if ("classes".equals(String.valueOf(classes.getFileName()))) {
                Path target = classes.getParent();
                if (target != null && "target".equals(String.valueOf(target.getFileName()))) {
                    candidates.add(target.getParent().resolve(fileName));
                }
            }
        } catch (Exception ex) {
            log.debug("Could not resolve module root: {}", ex.getMessage());
        }
        return candidates;
    }

    private static boolean loadInto(Map<String, Object> merged, Path path) {
        try {
            Properties props = new Properties();
            try (InputStreamReader reader =
                    new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            for (String name : props.stringPropertyNames()) {
                String value = props.getProperty(name);
                if (value != null && !value.isBlank()) {
                    merged.put(name, value.trim());
                }
            }
            return true;
        } catch (Exception ex) {
            log.warn("Could not load {}: {}", path, ex.getMessage());
            return false;
        }
    }
}
