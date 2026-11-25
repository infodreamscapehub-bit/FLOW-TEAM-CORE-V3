package com.flowcity.common.protocol;

/** Thrown when a payload is malformed, too large, or from an incompatible protocol version. */
public class ProtocolException extends Exception {

    private static final long serialVersionUID = 1L;

    public ProtocolException(String message) {
        super(message);
    }
}
