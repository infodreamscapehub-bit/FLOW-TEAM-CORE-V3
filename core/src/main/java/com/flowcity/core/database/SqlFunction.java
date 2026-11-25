package com.flowcity.core.database;

import java.sql.Connection;
import java.sql.SQLException;

/** Database work unit that receives a pooled connection. */
@FunctionalInterface
public interface SqlFunction<T> {
    T apply(Connection connection) throws SQLException;
}
