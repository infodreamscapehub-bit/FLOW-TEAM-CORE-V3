# Phase 3 – HUD, HUD editor, money state

## What a player sees

| Element | Content (all from the server) |
|---|---|
| Logo (top right) | FlowCity logo on a dark panel + "ROLEPLAY" |
| ID box | permanent RP ID |
| Name box | character name (account name until the character system exists) |
| Rank box | LuckPerms rank, in the rank's colour |
| Playtime box | e.g. `2h 5m`, advances locally between server updates |
| Cash box | Vault balance of the player |
| Bank box | bank balance – **always 0 until the Bank module (Phase 4)** |

Top-left corner is empty: **no clock, no date**. Without data from the server the HUD is not drawn
(no example data anywhere).

Default layout, top right: logo, below it `[ID][Name][Rank][Playtime]`, below that `[Bank][Cash]`.

## HUD editor (key **H**, rebindable in Options › Controls › FlowCity Roleplay)

* Drag an element with the mouse; mouse wheel or **Size ±** to resize.
* **Show/Hide**, **Color** (palette), **Opacity ±** for the selected element.
* **Font** (default / uniform), **HUD ±** (scale of the whole HUD).
* **Preset 1–3**: switch slot. **Reset**: back to the FlowCity default (preview only).
* **Save**: sends the layout to the server; confirmed with "Saved to the server".
* Position is stored relative to the nearest screen corner in GUI pixels, so it follows resolution / GUI scale.

Per-player storage: tables `fc_hud_presets` + `fc_hud_active` (migration V3). Permission
`flowcity.hud.edit` (default: everyone) is checked on the server for every save.

## Configuration

* `plugins/FlowCityCore/hud.yml` – money refresh interval, save cooldown, currency symbol and decimals.
* `config/flowcity-client.toml` (client) – `hud.enabled`, `theme.accent`, `theme.accent2`, `theme.panel`.
  The HUD reads these; the older Phase 2 screens still use fixed colours (migration planned).

## Security

The client only sends a layout string. The server parses it strictly (unknown elements, bad numbers,
over-long text are rejected), clamps all ranges, stores the *canonical* re-encoding, rate-limits saves
and cleans the preset name. Balances are sent as integer cents and only when they change.

## Protocol

Version bumped to **3**. New packets: `CB_MONEY_STATE (0x06)`, `CB_HUD_CONFIG (0x07)`, `SB_HUD_SAVE (0x12)`.
Client and server jars must both be updated.

## Verified vs not verified

Verified in CI (GitHub Actions, JDK 8): everything compiles (common, core, Forge mod) and unit tests pass –
layout codec round trip / clamping / rejection of garbage, packets, SQLite repository for presets,
migration to schema 3.

**Not verified – needs a real client + Arclight 1.16.5 server:**
1. Visual result of the HUD and the editor (layout, text fitting, logo blending, fade-in, count-up of money).
2. `Style.withFont` "uniform" font option and `blit` of the logo on the real 1.16.5 renderer.
3. Reading the balance from Vault/EssentialsX through reflection (`Economy#getBalance(OfflinePlayer)`).
4. Saving presets over the real `flowcity:main` channel, MySQL path of migration V3 (only SQLite is tested).

## Known limitations

* Bank is 0 until Phase 4. Cash is the Vault balance.
* No custom TTF font yet: needs a font file added to the resource pack; the editor offers vanilla fonts only.
* Individual element font size equals element scale; there is no separate font-size control.
* "Reset" only changes the preview; press Save to store it.
