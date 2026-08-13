package com.support.alert;

import com.support.alert.auth.AuthProperties;
import com.support.alert.email.ImapEmailProperties;
import com.support.alert.health.HealthCheckProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ImapEmailProperties.class, AuthProperties.class, HealthCheckProperties.class})
public class JiraSupportAlertApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiraSupportAlertApplication.class, args);
    }
}
