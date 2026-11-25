# FLOWCITY ROLEPLAY — Phase 0: Technical Compatibility Report

Date: 2026-11-25 · Status: **inspection complete, no code written yet**
Decision from owner: **start from zero** (existing jars/zips are reference only), **builds should run on GitHub**.

Legend: ✅ verified on this machine/sources · ⚠️ works with conditions / must be tested · ❌ blocker or not supported as specified

---

## 1. What was found on this machine

| Item | Location | Finding |
|---|---|---|
| JDK | `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot` | ✅ Temurin 17.0.20.1, the only JDK. `JAVA_HOME` set. |
| Git / Gradle / Maven | — | ❌ **None installed.** Git is required for GitHub. Gradle is not needed locally (Gradle Wrapper will be committed). |
| Arclight server jar | `Downloads\arclight-forge-1.16.5-1.0.25-7cf92be3.jar` | ✅ `arclight-1.16.5-1.0.25`, built 2023-10-22, installer pins **Forge 36.2.39**, CraftBukkit `v1_16_R3`. Not yet installed anywhere. |
| Existing test server | `Downloads\server` | ⚠️ This is **Mohist 1.16.5 (754b578e) / Forge 36.2.42**, not Arclight. Ran on Java 17. 8 mods, 0 plugins. World was saved with ~15 mods that are now missing (world/registry warnings). **Will not be used as the target.** |
| Client modpack | `Desktop\FLOWCITY` | 37 client mods (see §5 — several overlap with FlowCity features). |
| Old FlowCity builds | `Documents\FlowCityCore-1.16.5-1.0.0.jar`, `FlowCityBridge-1.0.0.jar`, many `FlowCity-*.zip` | Reference only, per "start from zero". Not inspected further. |

## 2. Target version matrix (to be pinned in the build)

| Component | Version | Side | Status |
|---|---|---|---|
| Minecraft | 1.16.5 | both | ✅ |
| Arclight | 1.16/1.0.25 (**final** 1.16.5 release) | server | ✅ release exists; no further updates will come |
| Forge | **36.2.39** (server, from Arclight installer) — clients should use the same 36.2.39 | both | ✅ |
| Java (runtime, server) | **Java 11 Temurin recommended**; Java 17 to be tested | server | ⚠️ Arclight's manifest uses `Add-Opens` (built for Java 9+). Mohist 36.2.42 ran on 17 here, but Arclight on 17 is **unverified**. Java 8 is not recommended. |
| Java (bytecode target) | Java 8 (`release 8`) for mod + plugin | build | ✅ standard for Forge 1.16.5 mods |
| Build toolchain | ForgeGradle 5.1.x + Gradle 7.x wrapper, JDK 8/11 on GitHub Actions | build | ✅ standard 1.16.5 MDK setup |
| MrCrayfish Vehicle Mod | **0.45.2** (file named `-1.16.3`, runs on 1.16.5; last official version) | both | ✅ already in both modpacks |
| Obfuscate | **0.6.3-1.16.5** | both | ✅ required by Vehicle Mod |
| LuckPerms (Bukkit) | 5.4.x | server plugin | ⚠️ commonly used on Arclight; must be load-tested |
| Vault | 1.7.3 | server plugin | ⚠️ load-test |
| EssentialsX (+Economy) | 2.20.x (supports 1.8.8–1.20.x) | server plugin | ⚠️ load-test; Essentials touches many vanilla events |
| PlaceholderAPI | 2.11.x | server plugin | ⚠️ load-test |
| Plasmo Voice (**chosen** over Simple Voice Chat) | **Forge 1.16.5 – 2.1.x** (replaces the 1.2.19 in the client pack) | both (mod) | ⚠️ see §4.6 — 2.1.0 for 1.16.5 is marked **alpha**; must be load-tested on Arclight |
| Database | SQLite (bundled with Arclight: `sqlite-jdbc 3.34.0`) / MySQL via HikariCP + MariaDB/MySQL driver shaded | server | ✅ Arclight already ships `mysql-connector 5.1.49`; we will shade our own relocated driver to avoid conflicts |

## 3. Proposed architecture (adjusted to what Arclight actually allows)

```
FlowCityRP/                      (one GitHub repo, multi-project Gradle)
├─ common/      FlowCityCommon   pure Java 8: packet IDs, DTOs, codec, protocol version, validation
├─ forge/       FlowCityForge    Forge 36.2.39 mod — CLIENT + SERVER jar
│    client: HUD, HUD editor, ID rendering, nametag hiding, keybinds, screens, notifications
│    server: item registry, creative tab, vehicle tracking hooks, network channel
├─ core/        FlowCityCore     Bukkit plugin (Spigot 1.16.5 API only, no Paper API)
│    IDs, profiles, LuckPerms, Vault, bank, jobs, admin, MDT, CNN, police, EMS, garage, DB
├─ voice/       FlowCityVoice    separate Forge mod = Plasmo Voice 2.1.x addon (optional, isolated)
└─ resourcepack/                 only for plugin-managed items (CustomModelData); Forge item assets live inside the mod jar
```

GitHub Actions: on every push → build all four jars → upload as workflow artifacts; on tag `v*` → GitHub Release with jars + resource pack zip.

## 4. Key compatibility decisions and risks

### 4.1 Plugin ↔ Forge mod communication (highest technical risk) ⚠️
On Arclight the Bukkit plugin and the Forge mod live in the same JVM but in different class loaders.
* **Option A (preferred): Forge `SimpleChannel` owned by the server half of FlowCityForge**, and FlowCityCore calls into it through a tiny `FlowCityBridge` interface published via a static service registry in FlowCityCommon. Requires a **spike test** that a Bukkit plugin on Arclight 1.0.25 can see classes from a Forge mod jar.
* **Option B (fallback): Bukkit plugin-messaging channel `flowcity:main`** with our own binary codec (FlowCityCommon), received on the client by a Forge `EventNetworkChannel` marked as optional. Works without cross-classloader access but bypasses Forge's handshake.
* The first Phase 1 deliverable is a 1-packet round-trip test of both options on the real server; the winner is fixed for the whole project.

### 4.2 Hiding vanilla nametags ✅ / ⚠️
* Primary: client-side `RenderNameplateEvent` → `Result.DENY` for players in FlowCityForge (client mod is mandatory anyway). Tab list unaffected.
* Server fallback for clients without the mod: scoreboard team with `NameTagVisibility.NEVER`. ⚠️ Conflicts with the TAB plugin (an old `TAB-3.3.0-FlowCity` build exists) because TAB also manages teams — if TAB is used, its nametag feature must be disabled or configured to `never`.

### 4.3 Vehicles / mileage ⚠️
* Vehicle Mod 0.45.2 already implements **fuel** (powered vehicles have fuel capacity/consumption) and its own key/lock items; we build on it, no second physics system.
* Mileage: sampled server-side every N ticks from the vehicle entity position, only while a driver is seated, with per-sample max-distance check (teleport/respawn reject), dimension-change reject. Stored against a **persistent FlowCity vehicle UUID** written into the entity's persistent NBT (`getPersistentData()` on the Forge side) — Bukkit PDC on modded entities under Arclight is unverified, so the Forge side owns the tag.
* Must inspect the 0.45.2 source (GitHub `MrCrayfish/MrCrayfishVehicleMod`, branch `1.16.X`) for: `VehicleEntity`/`PoweredVehicleEntity` class names, fuel getters/setters, speed field, despawn behaviour. **No API is assumed before that inspection (Phase 7).**
* Vehicle Mod is unmaintained (final version). Any bug in it cannot be fixed upstream.

### 4.4 Items, creative tab, resource pack ✅
* Real Forge items (phone, tablets, cards, keys, gifts…) registered by FlowCityForge; textures/models in the mod jar → no server resource pack needed for them.
* Server-side authority: right-click on a Forge item is intercepted on the server and the action is validated by FlowCityCore permissions before any GUI opens.
* Creative tab is visible to anyone in creative mode by vanilla design; "admin-only" is enforced by **not giving creative mode** and by server-side checks on use, not by hiding the tab.

### 4.5 Economy ✅ / ⚠️
* Vault is an abstraction; EssentialsX holds the money. "Cash" = Vault balance; "Bank" = FlowCity bank table (our own, with transactions, idempotency keys and row locks). Christmas Coins = separate FlowCity currency, never touching Vault.
* ⚠️ Vault calls from async threads: EssentialsX is not guaranteed thread-safe → all Vault mutations are executed on the main thread, DB work async.

### 4.6 Voice ⚠️ — **decided: Plasmo Voice** (owner decision 2026-11-25; Simple Voice Chat dropped)
* The client pack has **Plasmo Voice 1.2.19**. PV 1.x has practically no server API for radio/channels, so FlowCityVoice targets **Plasmo Voice 2.1.x**, which has a backport for Forge 1.16.5 and an addon API (activations + source lines = radio, job channels, whisper). PV 2.0.x and 2.1.x share one protocol.
* Consequence: **every client and the server must upgrade 1.2.19 → 2.1.x** (1.x and 2.x are not protocol-compatible).
* Risk: the 1.16.5 build of 2.1.0 is marked **alpha**, and the 2.1 API had breaking changes vs 2.0 → pin one exact version and compile against it.
* On Arclight use the **Forge** PV jar on the server (not the Spigot build). FlowCityVoice = separate Forge mod registered as a PV addon; if PV fails or is absent, FlowCityVoice disables itself and FlowCityCore keeps running.
* Radio permission checks: FlowCityVoice asks FlowCityCore (via the bridge from §4.1) whether a player may join a frequency (LuckPerms + job rank); if the bridge spike fails, it falls back to plain LuckPerms permission nodes.
* PV opens its own UDP port → hosting must allow it.
* Fallback if PV 2.1 alpha is unstable on Arclight: keep PV for proximity voice only, and do radio as a FlowCity-side feature later — decided after the Phase 8 test, not before.

### 4.7 LuckPerms ✅
Ranks are read from LuckPerms (`UserManager`, primary group, cached meta); rank changes from EAdmin go through LuckPerms `NodeBuilder`s (incl. temporary nodes). No separate rank storage — only a display mapping (`ranks.yml`) and an audit log.

### 4.8 Things in the spec that need a different approach ❌→ alternative
| Spec item | Problem | Alternative |
|---|---|---|
| "Christmas Music" | Real songs are copyrighted (e.g. the Michael Bublé PDF on your desktop) | Royalty-free / self-made `.ogg` tracks |
| "Medical NPCs", "Christmas NPCs" | No NPC library in the stack | Use Citizens (test on Arclight) or simple FlowCity NPC entities from the Forge mod — decide in Phase 6 |
| HUD = "no vanilla elements" | Hiding vanilla HUD fully breaks health/hunger for RP | Configurable per element (`RenderGameOverlayEvent.Pre` cancel per type) |
| "Test entire system on real Arclight with multiple players" | I can run the server headless here; real multi-client testing needs you (or HeadlessMc clients) | I run server-side tests; client/visual checks are a checklist for you |

## 5. Client modpack conflicts (`Desktop\FLOWCITY`)
| Mod | Overlaps with FlowCity | Recommendation |
|---|---|---|
| `smartphone-1.16.4-1.2.0` | Smartphone system | remove when FlowCity phone ships |
| `economyinc-1.6.9.4` | Bank/ATM/economy | remove (second economy) |
| `Roleplay+_1.16.5_1.0.5`, `LicensingMod 1.0.2` | Documents/licenses/ID | remove when FlowCity documents ship |
| `plasmovoice-forge-1.16-1.2.19` | Voice | keep Plasmo Voice, but **upgrade to 2.1.x** (§4.6) |
| `OptiFine HD U G8` | Custom HUD/rendering | keep, but HUD must be tested with OptiFine on |
| `cfm 7.0.0pre22-1.16.3`, `smartphone-1.16.4` | built for older 1.16.x | test on 36.2.39 |
| server also needs: `vehicle-mod 0.45.2`, `obfuscate 0.6.3` | — | ✅ already present |

## 6. Phase 1 plan (next)
1. Install Git; create GitHub repo `FlowCityRP` (private).
2. Gradle multi-project (common / forge / core / voice) + GitHub Actions workflow.
3. Fresh Arclight 1.0.25 test server in a separate folder (Java 11 + 17 runs), with LuckPerms, Vault, EssentialsX, PlaceholderAPI, Vehicle Mod, Obfuscate → load report.
4. Database layer (HikariCP, SQLite/MySQL, migrations V1) + config loader.
5. Protocol spike (§4.1 A vs B) — 1 packet both ways, logged.
Exit criterion: green GitHub build + server boots with all jars + spike result documented.

## Sources
* [Arclight release 1.16/1.0.25 (final 1.16.5, Forge 36.2.39)](https://github.com/IzzelAliz/Arclight/releases/tag/1.16/1.0.25)
* [Arclight 1.16 branch](https://github.com/IzzelAliz/Arclight/tree/1.16)
* [Plasmo Voice 2.1.0 (Forge 1.16.5 backport, 2.0/2.1 protocol-compatible)](https://modrinth.com/plugin/plasmo-voice/version/neoforge-2.1.0)
* [Plasmo Voice Spigot 2.1.0 (API breaking changes note)](https://modrinth.com/plugin/plasmo-voice/version/spigot-2.1.0)
* [Plasmo Voice on GitHub](https://github.com/plasmoapp/plasmo-voice/)
* [MrCrayfish Vehicle Mod 1.16.X build.gradle (0.45.2-1.16.5, Obfuscate dep)](https://www.github.com/Dragons-Craft/MrCrayfishVehicleMod/blob/1.16.X/build.gradle)
* [MrCrayfish's Vehicle Mod on CurseForge](https://curseforge.com/minecraft/mc-mods/mrcrayfishs-vehicle-mod)
* [Forge: Java 16 and you](https://forums.minecraftforge.net/announcement/15-java-16-and-you-how-to-avoid-crashes/)
