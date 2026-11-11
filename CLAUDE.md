# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

Milestones 1 (mod_info.json, jar build, mod plugin) and 2 (settings + XP tracking) are implemented. Mod id `xpsmods`, plugin class `xpsmods.XPSModsPlugin`, Java sources under `src/xpsmods/`.

- `XPSModsSettings` loads `data/config/xpsmods_settings.json` in `onApplicationLoad`. A missing key falls back to the default in the class and logs a warning.
- `XPData` stores XP as a `HashMap<String, Float>` in sector persistent data under `"xpsmods_xp"`, keyed by `FleetMemberAPI.getId()`.
- `XPBattleListener` (a transient `BaseCampaignEventListener`, added again in `onGameLoad`) awards XP in `reportPlayerEngagement`. Per-ship hull damage comes from `EngagementResultAPI.getLastCombatDamageData()`. Kill XP is split by each ship's share of the damage dealt to that enemy, because the API doesn't record who landed the kill. With `debugLog` on, it logs one line per ship.
- Auto-resolve gotcha: `BattleAutoresolverPluginImpl`'s results always return `false` from `EngagementResultForFleetAPI.isPlayer()` and have no damage data, so kill XP is split evenly across the deployed ships there. Identify the player's side with `Misc.isPlayerOrCombinedContainingPlayer(side.getFleet())`.

## Build

- `./build.sh` compiles everything under `src/` with `javac --release 17` against the game's jars (`starfarer.api.jar`, `json.jar`, `log4j`, `xstream`, `lwjgl`) and writes `jars/xpsmods.jar`. That jar is what the game loads (see `"jars"` in `mod_info.json`), so rebuild before launching. There are no tests.
- `../../jre_linux` is a JRE only, with no `javac` or `jar`. The build uses the system JDK 17 (`jdk17-openjdk`, `/usr/lib/jvm/java-17-openjdk`). Set `JAVA_HOME` to use a different one.
- New game jars needed at compile time go in the `for j in ...` list in `build.sh`. Library mod jars (for example LazyLib) need adding there too.
- `build/` and `jars/xpsmods.jar` are build outputs and are git-ignored.

## Environment

- The repo is the mod folder itself: `<Starsector>/mods/MyFirstMod`. The game install is two levels up (`../..`), so the game loads the mod straight from the working tree.
- Game version in use: **0.98a-RC8** (the version the installed mods target). Set `"gameVersion"` in `mod_info.json` to match.
- Bundled runtime: `../../jre_linux` (Zulu OpenJDK 17). Compile against Java 17 or lower.
- Game API to compile against: `../../starfarer.api.jar`. API sources for reference: `../../starfarer.api.zip`.
- Library mods installed in `../`: `LazyLib-3.0.0` (id `lw_lazylib`) and `MagicLib-1.5.6`. If you use them, list them under `dependencies` in `mod_info.json` and add their jars to the compile classpath.
- Run the game: `cd ../.. && ./starsector.sh`. The mod must be enabled in the launcher or listed in `../enabled_mods.json`.
- Logs: `../../starsector.log`. Script compile errors, missing-class errors, and mod plugin errors show up here, so check it first when the game fails to load.

## How Starsector loads a mod

- `mod_info.json` at the mod root is required. It declares `id`, `name`, `version`, `gameVersion`, an optional `jars` list, an optional `modPlugin` class (a `BaseModPlugin` subclass), and `dependencies`. `../Ashlib-2.2.4/mod_info.json` is a working example.
- Game data under `data/` (for example `data/hulls/`, `data/weapons/`, `data/config/`, and `.csv` files) is **merged** with core and other mods' files by matching path and row id. Don't replace whole core files.
- Java code ships in one of two ways:
  - **Compiled jar** listed in `"jars"`. Classes in a jar take precedence over loose sources.
  - **Loose `.java` files** under `data/scripts/`. The game compiles these at load time with Janino, which supports only a limited subset of Java (no lambdas and limited generics). Prefer a jar for anything non-trivial.
- To see how other mods are laid out, look at the installed mods in `../`.


## What the mod does

What this mod does

Working title: XP S-Mods. A lighter take on the idea behind Progressive S-Mods. Ships earn XP in combat. XP raises a ship's S-mod limit. S-mods are built in with credits, never story points.

Core loop
- Ships earn XP. After every battle the player's fleet takes part in, each deployed ship earns XP, stored per fleet member.
- XP raises the S-mod limit. The player spends a ship's XP to raise that ship's max S-mods by +1. Each extra slot costs more than the last, and there's a configurable cap.
- Credits build in S-mods. The player picks a hullmod that is already installed on the ship and pays credits to make it an S-mod. This costs no story points, and it can only happen while the ship has a free S-mod slot.

Rules and defaults

Every number below goes in data/config/xpsmods_settings.json so it can be tuned without recompiling. The values are starting defaults.

- XP gain: a base amount for being deployed, plus XP based on damage dealt and ships disabled or destroyed, scaled by hull size. Disabled or retreated ships still earn the base amount. Mothballed ships earn nothing. undeployed reserve and civilian ships earn a configurable average of xp earned by deployed ships. 
- Slot cost: baseSlotXP * slotCostMult^(extraSlotsBought), for example 1000 × 2.0^n. Default cap: +3 extra slots on top of vanilla.
- Build-in cost: creditsPerOP * hullmod OP cost * hullSizeMult, with a flat minimum.
- Removing S-mods: allowed for a smaller credit fee (setting allowRemoval). The slot stays bought.
- Vanilla story-point build-in stays untouched. This mod adds a second way to build in hullmods.
- Optional setting: show a ship's XP in its tooltip or in the refit screen if that's feasible.

Where the player does it

Milestone 1: a market interaction option added through data/campaign/rules.csv, "Ship upgrades (XP S-Mods)", available when docked at a market with a spaceport. It opens a dialog: pick a ship, see its XP, slots and cost, then buy a slot or build in or remove a hullmod. Later, only if it's practical: hook into the refit screen instead. Don't attempt this before milestone 1 works.

Implementation notes (verify every API name in starfarer.api.zip before using it)

- Persistence: store XP and bought slots in Global.getSector().getPersistentData(), keyed by FleetMemberAPI.getId(). Wrap this in a small data class. Prune entries for ships that no longer exist.
- Extra S-mod slots: vanilla reads the max permanent hullmods from a ship stat (look for something like Stats.MAX_PERMANENT_HULLMODS_MOD and Misc.getMaxPermanentMods). Apply the bonus through that stat. A small hidden hullmod is one option, and a stat applied from the mod plugin each frame is another. Choose whichever the API supports cleanly so the vanilla UI shows the right limit.
- Building in: use the variant's permanent/S-mod methods (for example ShipVariantAPI.addPermaMod(id, true)). Check how vanilla does it and copy that behaviour.
- XP from battles: use a campaign listener (BaseCampaignEventListener, for example reportPlayerEngagement or reportBattleFinalized) and read per-ship results from the engagement result. If per-ship damage isn't exposed there, use an EveryFrameCombatPlugin to record damage during combat, then hand it to the campaign side afterwards.
- Credits: Global.getSector().getPlayerFleet().getCargo().getCredits().
Build a jar, not loose Janino scripts, because this needs listeners, a custom dialog and generics. Mod plugin: BaseModPlugin subclass, registering listeners in onGameLoad.
- Saves must keep loading if the mod is removed later. Avoid storing custom classes in the save where possible, and prefer maps of primitives and strings.

Milestones (one at a time, test in-game after each)
- mod_info.json plus a jar build that compiles against starfarer.api.jar, with an empty mod plugin. The game loads it with no log errors.
- Settings file plus XP tracking. A debug log line shows XP earned per ship after each battle.
- Extra-slot stat bonus applied from stored data. The vanilla refit screen shows the raised limit.
- Market dialog: view a ship's XP and buy slots.
- Build in and remove S-mods for credits in the same dialog.
- Polish: tooltips, balance, edge cases (ship sold, scuttled, lost or recovered; d-mods; built-in hullmods; hullmods that can't be built in).
