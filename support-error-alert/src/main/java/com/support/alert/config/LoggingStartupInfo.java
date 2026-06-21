package com.support.alert.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class LoggingStartupInfo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoggingStartupInfo.class);

    @Value("${server.port}")
    private int serverPort;

    @Value("${logging.file.name:logs/jira-support-alert.json}")
    private String logFile;

    @Override
    public void run(ApplicationArguments args) {
        Path logPath = Paths.get(logFile).toAbsolutePath().normalize();
        try {
            Path parent = logPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException ex) {
            log.warn("Could not create log directory for {}: {}", logPath, ex.getMessage());
        }

        log.info(
                "jira-support-alert ready - port={}, IMAP inbox via UI credentials, application logs: {}",
                serverPort,
                logPath);
    }
}
