package com.flowcity.core.database;

import java.util.Locale;

/** Supported SQL dialects. Each has its own migration folder under resources/db/&lt;id&gt;/. */
public enum Dialect {
    SQLITE("sqlite"),
    MYSQL("mysql");

    private final String id;

    Dialect(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Dialect parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Database type is missing (expected sqlite or mysql)");
        }
        switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "sqlite":
                return SQLITE;
            case "mysql":
            case "mariadb":
                return MYSQL;
            default:
                throw new IllegalArgumentException("Unknown database type '" + value + "' (expected sqlite or mysql)");
        }
    }
}
