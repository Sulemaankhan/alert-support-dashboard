package com.support.alert.jira;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

/**
 * Registers Jira integration properties and a {@link RestClient} for Jira Cloud when fully configured
 * ({@code support.jira.*} and {@link JiraIntegrationProperties#isRunnable()}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JiraIntegrationProperties.class)
public class JiraConfiguration {

    @Bean
    @Conditional(OnJiraRunnableCondition.class)
    public RestClient jiraRestClient(JiraIntegrationProperties props) {
        String base = props.getSiteUrl().trim().replaceAll("/+$", "");
        return RestClient.builder()
                .baseUrl(base)
                .defaultHeaders(h -> h.setBasicAuth(
                        props.getEmail().trim(), props.getApiToken().trim(), StandardCharsets.UTF_8))
                .build();
    }
}
