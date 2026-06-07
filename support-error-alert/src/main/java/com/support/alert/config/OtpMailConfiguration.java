package com.support.alert.config;

import com.support.alert.auth.MailAuthSupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * Ensures a {@link JavaMailSender} exists when {@code spring.mail.host} is set in application.properties.
 */
@Configuration
@ConditionalOnProperty(name = "spring.mail.host")
public class OtpMailConfiguration {

    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    public JavaMailSender javaMailSender(Environment environment) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(environment.getRequiredProperty("spring.mail.host"));
        String port = environment.getProperty("spring.mail.port", "587");
        sender.setPort(Integer.parseInt(port));
        sender.setUsername(environment.getProperty("spring.mail.username"));
        sender.setPassword(MailAuthSupport.resolveMailPassword(environment));
        sender.setDefaultEncoding("UTF-8");

        Properties javaMailProps = new Properties();
        javaMailProps.put("mail.transport.protocol", "smtp");
        javaMailProps.put("mail.smtp.auth", environment.getProperty("spring.mail.properties.mail.smtp.auth", "true"));
        javaMailProps.put(
                "mail.smtp.starttls.enable",
                environment.getProperty("spring.mail.properties.mail.smtp.starttls.enable", "true"));
        javaMailProps.put(
                "mail.smtp.starttls.required",
                environment.getProperty("spring.mail.properties.mail.smtp.starttls.required", "true"));
        String trust = environment.getProperty("spring.mail.properties.mail.smtp.ssl.trust");
        if (trust != null && !trust.isBlank()) {
            javaMailProps.put("mail.smtp.ssl.trust", trust);
        }
        sender.setJavaMailProperties(javaMailProps);
        return sender;
    }
}
