package com.support.alert;

import com.support.alert.auth.AuthProperties;
import com.support.alert.email.ImapEmailProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ImapEmailProperties.class, AuthProperties.class})
public class JiraSupportAlertApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiraSupportAlertApplication.class, args);
    }
}
