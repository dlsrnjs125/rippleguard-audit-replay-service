package dev.rippleguard.audit.config;

import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rippleguard.phase2.pending-causation")
public record Phase2PendingCausationProperties(
        @DefaultValue("PT10M") @DurationMin(seconds = 1) Duration ttl,
        @DefaultValue("PT30S") @DurationMin(seconds = 1) Duration retryDelay,
        @DefaultValue("5") @Min(1) int maxAttempts,
        @DefaultValue("100") @Min(1) int batchSize,
        @DefaultValue("30000") @Min(1) long reconcileDelayMs
) {
}
