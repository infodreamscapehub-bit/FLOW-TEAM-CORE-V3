# Phase 2 – Player system (RP IDs, nametags, LuckPerms, HUD data)

## What a player sees

| Action | Result |
|---|---|
| First join | gets a permanent RP ID (1, 2, 3 … shown as `001` – format in `ids.yml`) |
| Reconnect / restart | same ID (stored by UUID in the database) |
| Above other players | **no Minecraft username** (vanilla name tags hidden) |
| Hold **LEFT ALT** | RP IDs fade in above nearby players (server decides who is included) |
| **F6** | ID search screen → profile of that ID |
| **F7** | own profile (ID, rank, playtime, status) |
| `/id` | own ID in chat |

Keys can be changed in Options › Controls › *FlowCity Roleplay*. Client settings: `config/flowcity-client.toml`.

## Commands and permissions

| Command | Permission | Default |
|---|---|---|
| `/id` | `flowcity.id` | everyone |
| `/id <player>` | `flowcity.id.lookup` | op |
| `/listid` (menu with heads, click = profile) | `flowcity.id.list` | op |
| `/viewid <id>` (profile) · `/viewid` (always show nearby IDs) | `flowcity.id.view` | everyone |
| `/setid <player> <id> [reason]` | `flowcity.id.admin` | op |
| `/resetid <player> [reason]` | `flowcity.id.admin` | op |
| `/idreload` | `flowcity.id.admin` | op |
| staff colour for your ID | `flowcity.id.staffcolor` | nobody |

`flowcity.id.admin` also shows account name + UUID in profiles. Every `/setid` and `/resetid`
is written to `fc_admin_log` (admin, target, old, new, reason, time) and `fc_rp_id_history`.

## Rules for IDs

* Allocated from a counter inside a database transaction (row-locked on MySQL) → no duplicates,
  also guaranteed by a UNIQUE index.
* An ID that was ever used is never handed out again (`ids.allow-reuse: false`), also not via `/setid`.
* If the database does not answer at login, the player is refused (no player ever runs without an ID).

## Ranks (LuckPerms)

Read-only from the LuckPerms API: primary group or highest-priority inherited group
(`ranks.yml: mode`), mapped to display name / colour / icon. Rank changes in LuckPerms
(including temporary ranks expiring) are pushed to the client immediately. Without LuckPerms
everybody shows the `default` group display. Rank management from EAdmin is Phase 5.

## HUD data

The client receives its own ID, name, rank and playtime (`PlayerStatePacket`) on join and on every
change. Drawing the HUD is Phase 3. Until the character system exists, "name" is the account name.

## PlaceholderAPI (optional)

`%flowcity_id%`, `%flowcity_id_raw%`, `%flowcity_rank%`, `%flowcity_rank_colored%`, `%flowcity_playtime%`
– e.g. to show RP IDs in a tab-list plugin.

## Known limitations

* The server-side name tag fallback (scoreboard team `fc_hidden`) does not work for players that
  another plugin gives a separate scoreboard (e.g. TAB). Then set `nametags.use-scoreboard-team: false`
  and hide name tags in that plugin. Players with the FlowCity mod never see usernames anyway.
* Staff hierarchy checks ("a helper cannot change an owner's ID") arrive with EAdmin in Phase 5;
  for now `/setid` is limited by `flowcity.id.admin` only.
* Not yet tested on a live Arclight server with real clients (see PHASE1.md test procedure).
