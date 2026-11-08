package com.logitrack.shipment.exception;

/**
 * Indica que la autoridad de identidad no pudo comprobar un destinatario.
 * @author dominox45
 */
public class IdentityIntegrationException extends RuntimeException {
    /** @param message descripción pública sin datos sensibles. */
    public IdentityIntegrationException(String message) { super(message); }
}
