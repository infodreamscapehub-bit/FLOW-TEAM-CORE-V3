package com.flowcity.core.database;

import org.bukkit.configuration.ConfigurationSection;

import java.io.File;

/** Reads database.yml into {@link DatabaseSettings}. */
public final class DatabaseSettingsLoader {

    private DatabaseSettingsLoader() {
    }

    public static DatabaseSettings load(ConfigurationSection yml, File dataFolder) {
        Dialect dialect = Dialect.parse(yml.getString("type"));
        DatabaseSettings.Builder b = DatabaseSettings.builder(dialect)
                .sqliteFile(new File(dataFolder, yml.getString("sqlite.file", "flowcity.db")))
                .host(yml.getString("mysql.host", "localhost"))
                .port(yml.getInt("mysql.port", 3306))
                .database(yml.getString("mysql.database", "flowcity"))
                .username(yml.getString("mysql.username", "flowcity"))
                .password(yml.getString("mysql.password", ""))
                .useSsl(yml.getBoolean("mysql.use-ssl", false))
                .maximumPoolSize(yml.getInt("pool.maximum-pool-size", 8))
                .minimumIdle(yml.getInt("pool.minimum-idle", 2))
                .connectionTimeoutMs(yml.getLong("pool.connection-timeout-ms", 10_000))
                .maxLifetimeMs(yml.getLong("pool.max-lifetime-ms", 1_800_000))
                .backupBeforeMigrations(yml.getBoolean("backup.before-migrations", true))
                .backupKeep(Math.max(1, yml.getInt("backup.keep", 10)))
                .backupFolder(new File(dataFolder, "backups"));

        ConfigurationSection props = yml.getConfigurationSection("mysql.properties");
        if (props != null) {
            for (String key : props.getKeys(false)) {
                b.property(key, String.valueOf(props.get(key)));
            }
        }
        return b.build();
    }
}
