# Phase 1 – Core architecture

## Implemented

| Area | Where | Notes |
|---|---|---|
| Multi-project Gradle build | `settings.gradle`, `*/build.gradle` | ForgeGradle 5.1 (forge), Shadow 7.1.2 (core), Java 8 bytecode |
| CI build + release | `.github/workflows/build.yml` | JDK 8, Gradle 7.6.4; jars uploaded per commit, GitHub Release on `v*` tags |
| FlowCityCommon protocol | `common/.../protocol` | versioned header, VarInt, bounds-checked reader, size limits, HELLO / HELLO_ACK packets |
| Protocol unit tests | `common/src/test` | round-trip, wrong version, truncated, trailing bytes, oversize, huge string length |
| Database layer | `core/.../database` | HikariCP (relocated), SQLite (1 connection, WAL) or MySQL (relocated Connector/J 8.0.33), dedicated `FlowCity-DB-*` threads, `query` / `transaction` futures |
| Migrations | `core/src/main/resources/db` | `fc_schema_version`, per-dialect SQL, refuses a DB newer than the plugin, SQLite `VACUUM INTO` backup before migrating an existing DB |
| Schema V1 | `db/*/V1__core.sql` | `fc_players`, `fc_counters` (never-reused RP IDs), `fc_rp_id_history`, `fc_admin_log` |
| Database tests | `core/src/test` | fresh migrate, restart keeps data, rollback on failure, downgrade refusal |
| Config | `config.yml`, `database.yml` | created on first run; jar defaults applied for missing keys |
| Client handshake | `ClientConnectionService` (core) + `ClientNetwork` (forge, client only) | `flowcity:main` plugin-messaging channel, random nonce per session, rate limit, states CONFIRMED / NO_MOD / REJECTED |
| Forge bridge probe | `ForgeBridgeProbe` (core) + `ServerBridge` (forge) | checks whether the plugin can call mod classes on Arclight |
| Status command | `/flowcity status` (`flowcity.admin.status`) | version, DB, pool, bridge probe, per-player client state |

## Verified on GitHub Actions (run #4, commit 1e7a671)

* Whole project compiles (JDK 8, Gradle 7.6.4, ForgeGradle 5.1, Forge 36.2.39 official mappings).
* All unit tests pass: 8 protocol tests (common), 5 database tests (core, real SQLite).
* `FlowCityForge-1.16.5-0.1.0.jar`: 14 KB, 10 classes, `mods.toml` present, reobfuscated.
* `FlowCityCore-1.16.5-0.1.0.jar`: 2.7 MB (HikariCP + MySQL Connector/J relocated), `plugin.yml` present.
* Fixed on the way: HikariCP needs slf4j on the test classpath (Arclight provides it at runtime).

## Not verified yet (needs a real Arclight server + client)

1. Server boots with FlowCityCore + FlowCityForge on Arclight 1.0.25 (Java 11 and 17).
2. **Spike B** – a client with FlowCityForge gets `CONFIRMED` in `/flowcity status`.
   A vanilla client gets `NO_MOD` and can still play.
3. **Spike A** – log line `Forge bridge probe: reachable via …` or `not reachable (…)`.
4. A FlowCityForge client can still join a server **without** FlowCity (channel accepts absent).

The result of 2 and 3 decides how the plugin and the mod talk for the rest of the project
(see the compatibility report, §4.1).

## Test procedure

1. Arclight 1.0.25 server, fresh folder. `mods/`: `FlowCityForge-1.16.5-0.1.0.jar`. `plugins/`: `FlowCityCore-1.16.5-0.1.0.jar`.
2. Start; check the log for `Database ready: sqlite, schema version 1` and `Forge bridge probe: …`.
3. Join with a Forge 36.2.39 client that has `FlowCityForge-1.16.5-0.1.0.jar`; after ~2 s run `/flowcity status`.
4. Join with a client without the mod (if the server allows it) and run `/flowcity status` again.
5. Stop the server, start it again: schema stays v1, no errors.
