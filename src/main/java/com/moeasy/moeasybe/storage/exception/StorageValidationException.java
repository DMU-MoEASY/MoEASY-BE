package com.moeasy.moeasybe.storage.exception;

/** Indicates that an S3 storage operation received invalid input. */
public class StorageValidationException extends RuntimeException {

    /**
     * Creates an exception for invalid storage input.
     *
     * @param message description of the invalid input
     */
    public StorageValidationException(String message) {
        super(message);
    }
}
