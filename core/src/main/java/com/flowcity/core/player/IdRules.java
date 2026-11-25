package com.flowcity.core.player;

/** RP ID allocation rules from ids.yml. */
public final class IdRules {

    public final int startId;
    public final int maxId;
    /** false (default): an ID that was ever assigned is never handed out again. */
    public final boolean allowReuse;

    public IdRules(int startId, int maxId, boolean allowReuse) {
        if (startId < 1) {
            throw new IllegalArgumentException("ids.start-id must be >= 1");
        }
        if (maxId < startId) {
            throw new IllegalArgumentException("ids.max-id must be >= start-id");
        }
        this.startId = startId;
        this.maxId = maxId;
        this.allowReuse = allowReuse;
    }

    public boolean inRange(int id) {
        return id >= 1 && id <= maxId;
    }
}
