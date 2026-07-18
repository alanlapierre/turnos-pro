package com.turnospro.infrastructure.adapters.out.persistence.exception;

public class InfrastructureDatabaseException extends RuntimeException {
    public InfrastructureDatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
