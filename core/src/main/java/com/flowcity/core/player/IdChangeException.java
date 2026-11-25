package com.flowcity.core.player;

/**
 * A rejected RP ID change; {@link #reason()} maps to a message in messages.yml.
 * Unchecked so it can leave a DatabaseManager transaction, which then rolls back.
 */
public final class IdChangeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        NOT_FOUND("id.error.player-not-found"),
        OUT_OF_RANGE("id.error.out-of-range"),
        TAKEN("id.error.taken"),
        RETIRED("id.error.retired"),
        SAME("id.error.same"),
        EXHAUSTED("id.error.exhausted");

        public final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private final Reason reason;

    public IdChangeException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
