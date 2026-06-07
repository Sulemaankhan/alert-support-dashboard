package com.support.alert.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class LoggingStartupInfo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoggingStartupInfo.class);

    @Value("${server.port}")
    private int serverPort;

    @Value("${logging.file.path:logs}")
    private String logPath;

    @Override
    public void run(ApplicationArguments args) {
        log.info(
                "jira-support-alert ready - port={}, IMAP inbox via UI credentials, application logs: {}/jira-support-alert.log",
                serverPort,
                logPath);
    }
}
