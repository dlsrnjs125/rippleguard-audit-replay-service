package dev.rippleguard.audit.application;

public class ContractValidationException extends RuntimeException {
    public ContractValidationException(String message) {
        super(message);
    }

    public ContractValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
