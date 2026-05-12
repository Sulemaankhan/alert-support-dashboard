package com.support.alert.jira;

import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * True when {@link JiraIntegrationProperties#isRunnable()} — Jira Cloud client can be built.
 */
public final class OnJiraRunnableCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        BindResult<JiraIntegrationProperties> bound =
                Binder.get(context.getEnvironment()).bind("support.jira", JiraIntegrationProperties.class);
        return bound.orElseGet(JiraIntegrationProperties::new).isRunnable();
    }
}
