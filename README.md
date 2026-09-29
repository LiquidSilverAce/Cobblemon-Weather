# Cobblemon Weather

Minecraft **1.21.1 / Java 21** addon for Fabric and NeoForge. Successful Cobblemon battle weather changes affect the Overworld, with configurable player permissions, duration, cooldown, and battle priority.

## Configuration

Starting a server or opening a single-player world creates `config/cobblemon_weather.json`:

```json
{
  "enableWeatherIntegration": true,
  "battleWeatherDurationTicks": 24000,
  "weatherChangeCooldownTicks": 0,
  "allowPrimalOverride": true,
  "allowCrossBattleOverride": false,
  "clearWeatherOnBattleEnd": false,
  "enableThunderstormIntegration": true,
  "weatherWhitelist": []
}
```

| Setting | Behavior |
| --- | --- |
| `weatherWhitelist` | Empty allows everyone. Otherwise only the listed players can cause this addon's weather changes. Accepts Minecraft account names (case insensitive) and canonical UUIDs. |
| `battleWeatherDurationTicks` | Positive integer; how long applied world weather lasts. Default `24000` = 20 minutes at 20 ticks/second. |
| `weatherChangeCooldownTicks` | Nonnegative integer; minimum time between accepted battle weather changes in the Overworld. Default `0` allows back-to-back changes. `600` = 30 seconds; `1200` = one minute. |
| `enableWeatherIntegration` | Master switch for battle-triggered weather, including thunderstorms. |
| `enableThunderstormIntegration` | Enables the additional Thunder, Thunderbolt, and Wildbolt Storm effects. |
| `allowCrossBattleOverride` | By default, the battle that set the weather keeps control until it ends or its weather duration expires. When true, another battle can override it with higher priority. |
| `allowPrimalOverride` | When cross-battle overrides are enabled, allows priority-2 effects to replace other priority-2 effects. |
| `clearWeatherOnBattleEnd` | When true, clears the weather when the owning battle ends, including fleeing, capture, disconnect, draws, and forced stops. Default false lets the configured weather duration continue. |

For example, add the following entries to restrict changes:

```json
"weatherWhitelist": ["LiquidSilverAce", "01234567-89ab-cdef-0123-456789abcdef"]
```

Use a player's actual UUID instead of the example UUID. UUIDs continue working after account-name changes. Permissions are checked against the **player whose Pokémon caused the effect**, not another participant, the opponent, a nickname, or a spectator. When the list is populated, wild/NPC Pokémon and weather messages with no attributable player cannot change world weather. An empty list preserves unrestricted battle effects, including wild Pokémon.

Cooldowns are shared across players because Overworld weather is shared. Rejected attempts do not restart the timer, and blocked effects are not queued for later. Cooldown `0` removes the timing restriction; the configured battle priority rules still apply. Cooldown state resets when the server restarts. Vanilla weather timers govern duration, including normal Minecraft sleeping and `doWeatherCycle` behavior.

After editing, use **`/cobbleweather reload`** as an operator, or restart/reopen the world. Missing fields use defaults, so an existing config without a cooldown still permits back-to-back changes. Invalid JSON, invalid whitelist entries, and out-of-range values are rejected without overwriting the file or silently switching to unrestricted access. A failed reload preserves the last valid configuration.

## Weather effects

| Battle effect | World result | Priority |
| --- | --- | --- |
| Rain Dance | Rain | 0 |
| Sunny Day | Clear | 0 |
| Sandstorm, Snowscape, Hail | Precipitation | 0 |
| Drizzle, Drought, Sand Stream, Snow Warning, Orichalcum Pulse | Corresponding rain, clear, or precipitation | 1 |
| Primordial Sea, Desolate Land, Delta Stream | Rain, clear, clear respectively | 2 |
| Thunder or Thunderbolt | Thunderstorm **only if the world is already raining** | 0 |
| Wildbolt Storm | Thunderstorm, including from clear weather | 2 |

**Wildbolt Storm is the only move that bypasses the rain prerequisite for thunderstorms.** It still respects the whitelist, master switch, thunderstorm switch, configured cooldown, and battle priority. This is an addon effect on move use; it does not depend on Wildbolt Storm creating a Showdown weather condition or on a particular species using it.

Regular weather moves and abilities act on Cobblemon's confirmed weather messages, including switch-ins and ability changes. Failed weather moves and turn-by-turn weather upkeep do not apply or refresh world weather. Within one battle, ordinary moves and abilities can replace each other, but priority-2 weather resists lower priorities until the battle weather ends. This addon changes world weather, not battle mechanics.

Only the Overworld is affected. Snow remains biome-dependent; sandstorm visuals require a compatible client weather renderer such as Particle Rain. Neither sand nor snow is forced into every biome. The addon contains no client particle renderer and requires no Particle Rain installation on the server.

## Commands

All `/cobbleweather` commands retain their operator-level-2 requirement:

- `/cobbleweather reload` — reload the configuration.
- `/cobbleweather debug <clear|sun|rain|sand|snow|thunderstorm>` — force weather for the configured duration.
- `/cobbleweather thunderstorm` — shortcut for the thunderstorm debug command.

Player-issued debug commands also enforce the whitelist. Server console commands and configuration reloads remain available for administration. Debug weather intentionally bypasses battle switches, priority, and cooldown; it releases any existing battle weather ownership and starts the cooldown for subsequent battle effects. The whitelist governs this addon's weather changes; it does not alter vanilla `/weather` permissions, natural weather, sleeping, or other mods.

## Installation and build

Install the release jar matching your loader from `fabric/build/libs/` or `neoforge/build/libs/`. Do not install `common`, `sources`, or `dev-shadow` jars. Install on a dedicated server or in a single-player client; the addon does not register custom client assets or networking.

Dependencies: Minecraft 1.21.1, Java 21, Architectury API 13.0.8+, and either Fabric Loader 0.18.4+ with Fabric API or NeoForge 21.1.215+. The build targets Cobblemon 1.7.3; Cobblemon 1.8.1 is a compatibility target. Use the language dependencies required by your Cobblemon installation. Without Cobblemon, only administrative weather commands are available.

```sh
./gradlew build :fabric:runGametest
./gradlew build :fabric:runGametest -Pcobblemon_version=1.8.1+1.21.1
```

Unit tests cover configuration and weather identifiers. The isolated Fabric GameTest executes the actual Cobblemon move/weather instructions and injected callbacks, checking source-player permissions, successful versus failed weather moves, thunder prerequisites, durations, cooldown boundaries, priority, ended-battle cleanup, and command permissions. Test code is excluded from release jars. It does not replace an interactive client battle or a full modpack/NeoForge gameplay check.

Local verification: all 26 unit tests passed, both loader release jars built against Cobblemon 1.7.3 and 1.8.1, and the Fabric dedicated-server GameTest passed with both versions. NeoForge gameplay and client particle rendering have not been tested.

## Audit fixes

- Preserve the causing Pokémon/player through battle dispatch, so an allowed opponent cannot grant permission to an unlisted player.
- Use confirmed weather messages instead of applying attempted weather moves twice or guessing abilities from the initial team. Recognize Showdown's actual `RainDance`/`SunnyDay` identifiers.
- Process effects in the battle dispatch queue rather than before earlier queued moves and switches finish.
- Route thunderstorms through the same enable switches, ownership, priority, duration, and cooldown rules. Wildbolt Storm is handled as a move, not as a nonexistent standard Showdown weather condition.
- Release stale ownership after all battle endings and reset transient state between server sessions. Release primal protection when battle weather ends.
- Validate configuration and prevent read/parse errors from falling back to an empty whitelist.
- Fix invalid NeoForge TOML, inaccurate template metadata, build dependencies, Kotlin metadata remapping, and shared source packaging for both loaders.
