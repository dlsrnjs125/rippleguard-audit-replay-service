package dev.rippleguard.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rippleguard.audit.interfaces.rest.CaseTimelineResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ContractFixtureDeserializationTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void readsPhaseOneCaseTimelineContractExample() throws Exception {
        String json = Files.readString(Path.of("../rippleguard-contracts/examples/valid/rest/case-timeline.json"));

        CaseTimelineResponse response = objectMapper.readValue(json, CaseTimelineResponse.class);

        assertThat(response.schemaVersion()).isEqualTo("1.0.0");
        assertThat(response.events()).isNotEmpty();
    }
}
