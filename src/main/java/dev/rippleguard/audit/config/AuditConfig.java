package dev.rippleguard.audit.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        AuditKafkaProperties.class,
        Phase2PendingCausationProperties.class
})
public class AuditConfig {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
