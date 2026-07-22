package dev.rippleguard.audit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ContractSchemaValidator {
    private final JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private final ConcurrentHashMap<String, JsonSchema> schemas = new ConcurrentHashMap<>();

    public void validate(String schemaPath, JsonNode node) {
        JsonSchema schema = schemas.computeIfAbsent(schemaPath, this::load);
        Set<ValidationMessage> errors = schema.validate(node);
        if (!errors.isEmpty()) {
            throw new ContractValidationException(errors.iterator().next().getMessage());
        }
    }

    private JsonSchema load(String schemaPath) {
        try {
            ClassPathResource resource = new ClassPathResource("contracts/" + schemaPath);
            return factory.getSchema(resource.getURI());
        } catch (Exception exception) {
            throw new ContractValidationException("Contract schema unavailable: " + schemaPath, exception);
        }
    }
}
