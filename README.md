# FLOWCITY ROLEPLAY

Modular roleplay framework for **Minecraft 1.16.5 on Arclight 1.16/1.0.25 (Forge 36.2.39)**.

| Module | Output | Installs to |
|---|---|---|
| `common/` – FlowCityCommon | compiled into both jars below | – |
| `core/` – FlowCityCore | `FlowCityCore-1.16.5-<ver>.jar` (Bukkit plugin) | server `plugins/` |
| `forge/` – FlowCityForge | `FlowCityForge-1.16.5-<ver>.jar` (Forge mod) | server `mods/` **and** every client `mods/` |
| `voice/` – FlowCityVoice | Plasmo Voice 2.1.x addon | Phase 8 |

Docs: [Phase 0 compatibility report](docs/PHASE0-COMPATIBILITY-REPORT.md) · [Phase 1 notes](docs/PHASE1.md) · [Phase 2](docs/PHASE2.md) · [Phase 3](docs/PHASE3.md)

## Building

Builds run on GitHub Actions (`.github/workflows/build.yml`): every push to `main` builds and tests
everything and uploads the jars as a workflow artifact; pushing a tag `v*` also creates a GitHub Release.

Local build (needs JDK 8 and Gradle 7.6.x): `gradle build`

## Current status

Phase 3 (HUD + HUD editor) – see [docs/PHASE3.md](docs/PHASE3.md). Phase 1 (core architecture) – see [docs/PHASE1.md](docs/PHASE1.md) for what is implemented and what is
still unverified on a real Arclight server.
