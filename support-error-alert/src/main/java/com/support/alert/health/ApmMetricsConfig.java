package com.support.alert.health;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApmMetricsConfig {

    /** Keep HealthCheck polling/SSE from inflating app request and error-rate charts. */
    @Bean
    MeterFilter denyApmSelfTraffic() {
        return new MeterFilter() {
            @Override
            public MeterFilterReply accept(Meter.Id id) {
                if (!"http.server.requests".equals(id.getName())) {
                    return MeterFilterReply.NEUTRAL;
                }
                String uri = id.getTag("uri");
                if (uri != null && ApmUriFilters.isNoiseUri(uri)) {
                    return MeterFilterReply.DENY;
                }
                return MeterFilterReply.NEUTRAL;
            }
        };
    }
}
