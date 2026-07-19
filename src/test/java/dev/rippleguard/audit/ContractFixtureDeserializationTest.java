package dev.rippleguard.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rippleguard.audit.interfaces.rest.CaseTimelineResponse;
import org.junit.jupiter.api.Test;

class ContractFixtureDeserializationTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void readsPhaseOneCaseTimelineContractExample() throws Exception {
        CaseTimelineResponse response = objectMapper.readValue(
                getClass().getResourceAsStream("/contracts/case-timeline.json"),
                CaseTimelineResponse.class
        );

        assertThat(response.schemaVersion()).isEqualTo("1.0.0");
        assertThat(response.events()).isNotEmpty();
    }
}
