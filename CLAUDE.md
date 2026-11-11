# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

Milestones 1 (mod_info.json, jar build, mod plugin) and 2 (settings + XP tracking) are implemented. Mod id `xpsmods`, plugin class `xpsmods.XPSModsPlugin`, Java sources under `src/xpsmods/`.

- Settings are LunaLib mod settings: fields, defaults, ranges and tabs live in `data/config/LunaSettings.csv` (ids `xpsmods_<field>`). `XPSModsSettings.init()` (called in `onApplicationLoad`) reads them and registers a `LunaSettingsListener`, so menu changes apply without a restart. The hotkey change applies the next time refit opens. A new tunable needs a CSV row, a static field, and a line in `settingsChanged`. There is no JSON settings file any more.
- `XPData` stores XP as a `HashMap<String, Float>` in sector persistent data under `"xpsmods_xp"`, keyed by `FleetMemberAPI.getId()`.
- `XPBattleListener` (a transient `BaseCampaignEventListener`, added again in `onGameLoad`) awards XP in `reportPlayerEngagement`. Per-ship hull damage comes from `EngagementResultAPI.getLastCombatDamageData()`. Kill XP is split by each ship's share of the damage dealt to that enemy, because the API doesn't record who landed the kill. With `debugLog` on, it logs one line per ship.
- Milestone 3 (extra slots):
  - `XPData` also stores bought slots under `"xpsmods_slots"` (String → Integer). `getSlotBonus` caps them at `maxExtraSlots`; the `debugGrantSlots` setting overrides them for testing.
  - `XPSlotsHullmod` (`data/hullmods/hull_mods.csv`, id `xpsmods_slots`) applies the bonus.
  - `SlotBonus.ensureHullmod(member)` adds the hullmod, copying shared stock/goal/empty-hull variants first. It's called on the player fleet in `onGameLoad`, and must also be called whenever slots are bought.
- Milestones 4 and 5 (refit screen; milestone 5 not yet tested in the game). LunaLib is a required dependency.
  - `XPSModsRefitButton` (a LunaLib `BaseRefitButton`) only opens `BuildInDialog`. That dialog is styled after vanilla's story-point "Build in" dialog (polish step 2): a table of hullmods with icon, design type, OP, cost and status, where ticking rows marks them for building in or removal, plus a right column with S-mods used/limit, XP, credits, the S-mod bonuses of all ticked hullmods stacked like vanilla, leaving out those without one (`addSModEffectSection` or the `sModDesc` text), slot buying, and one Confirm (G) / Cancel (Q). Rows are `addAreaCheckbox` with labels on top, the same technique as Progressive S-Mods' `ButtonWithImage`.
  - `RefitFrame` places the dialog where vanilla's sits: over the refit frame, minus the fleet list column (213 units), which gets a click-swallowing dimmer like vanilla's. The frame is measured from the refit tab panel if it's frame-sized. Otherwise it uses vanilla's size rule, `min(1250, max(screenW - 20, 1010)) + 2` by `min(850, max(screenH - 74, 734)) + 2` (the same numbers as Exiled Sector's `RefitScreenLayout`), centred above the 33-unit bottom tab bar. `initPanel` repositions LunaLib's background panel, and `onPanelClose` removes the dimmer.
  - `RefitButtonHook` also attaches the button inside the vanilla "add hullmod" picker: a core UI child that holds another instance of the refit `getModDisplay()` class, found by class comparison.
  - `SModRules` holds the rules and costs. The build-in list and S-mod count follow vanilla (`getNonBuiltInHullmods()` minus hidden, d-mod, perma and `no_build_in` mods; `Misc.getCurrSpecialMods`). Enhanced built-ins (`variant.getSModdedBuiltIns()`) are rows of their own: they need `hasSModEffect() && !isSModEffectAPenalty()` and, like vanilla, don't count against the S-mod limit.
  - Module tabs of multi-section ships work. Their own hullmods and S-mods are listed, each module may use up to the main ship's limit (as in Progressive S-Mods), and slot buying stays on the main hull. Vanilla's own story-point build-in on a module still shows the vanilla limit, because the hidden hullmod is only on the main hull.
  - `ShipDataPruner` (a transient script that runs once a month in game time) marks ships seen in the player fleet or any market storage, across all locations, under `"xpsmods_lastSeen"`. It removes data unseen for `pruneAfterMonths` (default 12 game months, converted with the clock's own days per month; 0 = never). Battle losses are handled by `XPBattleListener`. During engagements, player ships are almost always "disabled", not "destroyed" (the auto-resolver only ever disables), and whether one is really lost is only decided after recovery. So knocked-out ships are remembered, and in `reportBattleOccurred`, which fires after the post-battle recovery dialog, any not back in the fleet lose their data. That covers destroyed ships, ships not recovered, and ships lost on a retreat. A ship we hold data for counts as the player's even after the game has taken it out of the fleet. When it changes a ship, it must add the hullmod to the refit screen's `variant` as well as `member.getVariant()`, then call `refreshVariant()`, because LunaLib's refresh saves the refit variant back onto the ship.
  - `RefitButtonHook` (a transient every-frame script) re-attaches a "Ship upgrades" button with the `refitHotkey` under vanilla "Build in" via `coreUI.getCurrentTab().getRefitPanel().getModDisplay().getMods().getPerm()`. It opens the panel the way LunaLib's `RefitButtonAdder` does (Exiled Sector's `RefitPanelLauncher` does the same). On any reflection failure it logs once and switches off until the game restarts.
  - `UiReflect`: the game blocks `java.lang.reflect` in mod code, so reflection goes through `MethodHandles` on `Class.forName("java.lang.reflect.Method")`, the same as LunaLib. Reference sources: `../LunaLib-2.0.5/src`, `../Progressive S-Mods-1.2.0/src`. Exiled Sector (`../Exiled Sector-0.12.4`) only ships jars; read them with `javap -p -c -constants`.
- Polish step 1 (upgrade icon) is done. The icon is shown on the refit screen only; the fleet screen was deliberately skipped.
  - `UpgradeIconOverlay` is an input-less panel on the refit tab. It draws vanilla's `graphics/icons/intel/log_upgrade.png` in the top-left of ship cards (the top-right holds vanilla's S-mod/d-mod indicators) where `XPData.canBuySlot` is true, reading `refitTab.getFleetList().getItems()` and `refitTab.getButtonToMember()` every frame. It has its own failure switch, separate from the "Build in" button's.
- Battle report (setting `battleReport`): after awarding XP, `XPBattleListener.report` writes to the post-battle dialog's text panel (`getCurrentInteractionDialog()`, falling back to the last dialog from `reportShownInteractionDialog`). It lists XP per deployed ship, highest first, a reserve summary, and the ships that can buy a slot, with the ones that just became ready marked "(new)".
- Auto-resolve gotcha: `BattleAutoresolverPluginImpl`'s results always return `false` from `EngagementResultForFleetAPI.isPlayer()` and have no damage data, so kill XP is split evenly across the deployed ships there. Identify the player's side with `Misc.isPlayerOrCombinedContainingPlayer(side.getFleet())`.

## Build

- `./build.sh` compiles everything under `src/` with `javac --release 17` against the game's jars (`starfarer.api.jar`, `json.jar`, `log4j`, `xstream`, `lwjgl`) and writes `jars/xpsmods.jar`. That jar is what the game loads (see `"jars"` in `mod_info.json`), so rebuild before launching. There are no tests.
- `../../jre_linux` is a JRE only, with no `javac` or `jar`. The build uses the system JDK 17 (`jdk17-openjdk`, `/usr/lib/jvm/java-17-openjdk`). Set `JAVA_HOME` to use a different one.
- New game jars needed at compile time go in the `for j in ...` list in `build.sh`. Library mod jars go in the second loop (currently LunaLib), and must match `dependencies` in `mod_info.json`.
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

Name: Veteran Refits (mod id `xpsmods`, which must not change: saves depend on it; working title was "XP S-Mods"). A lighter take on the idea behind Progressive S-Mods. Ships earn XP in combat. XP raises a ship's S-mod limit. S-mods are built in with credits, never story points.

Core loop
- Ships earn XP. After every battle the player's fleet takes part in, each deployed ship earns XP, stored per fleet member.
- XP raises the S-mod limit. The player spends a ship's XP to raise that ship's max S-mods by +1. Each extra slot costs more than the last, and there's a configurable cap.
- Credits build in S-mods. The player picks a hullmod that is already installed on the ship and pays credits to make it an S-mod. This costs no story points, and it can only happen while the ship has a free S-mod slot.

Rules and defaults

Every number below is a LunaLib mod setting (`data/config/LunaSettings.csv`), so players can tune it in game. The values are the defaults.

- XP gain: XP for hull damage dealt plus kill XP for enemy ships disabled or destroyed, with kill XP scaled by the enemy's hull size. There is no base XP for just being deployed (removed in the balance pass). Mothballed ships earn nothing. Undeployed reserve and civilian ships earn reserveShareOfAverage (default 0.25, lowered from 0.5 in the balance pass so deploying clearly matters) of the deployed ships' average, and a deployed ship that earned less than that is raised to it.
- Slot cost: baseSlotXP * slotCostMult^(extraSlotsBought), for example 1000 × 2.0^n. Default cap: +3 extra slots on top of vanilla.
- Build-in cost: max(minBuildInCredits, creditsPerOP * hullmod OP cost * buildInSizeMult[size]), defaults 1000 and 1000. `buildInSizeMult` is separate from the XP `hullSizeMult`.
- Enhancing a built-in: enhanceCostMult (default 0.5) × the build-in cost, because built-ins' OP costs made it very expensive (195k for one logistics built-in). Removing an enhancement costs removalFeeFraction of that, with no minimum.
- Removing S-mods: allowed (setting allowRemoval) for removalFeeFraction of the build-in cost, with the same minimum. The hullmod is removed from the ship entirely (`removePermaMod` + `removeMod`), not left as a normal hullmod. The slot stays bought.
- Vanilla story-point build-in stays untouched. This mod adds a second way to build in hullmods.
- Upgrade indicator: instead of showing a ship's XP count in its tooltip, show an icon that tells the player an upgrade is available for that ship.

Where the player does it

In the refit screen there's a "Ship upgrades" button under the vanilla "Build in" button (hotkey `refitHotkey`, default S; U is vanilla refit's Undo), plus an entry in LunaLib's refit button list. Both open the same panel for the selected ship: see its XP, slots and costs, then buy a slot, or build in or remove a hullmod. Buying slots with XP works anywhere. Building in and removing S-mods for credits needs the player docked at a market (LunaLib's `market` is non-null). This replaced the original plan of a market dialog through rules.csv.

Implementation notes (verify every API name in starfarer.api.zip before using it)

- Persistence: store XP and bought slots in Global.getSector().getPersistentData(), keyed by FleetMemberAPI.getId(). Wrap this in a small data class. Prune entries for ships that no longer exist (done by `ShipDataPruner`).
- Extra S-mod slots: vanilla reads the limit from `Stats.MAX_PERMANENT_HULLMODS_MOD` (`Misc.getMaxPermanentMods`). The bonus is applied by the hullmod `xpsmods_slots` (`hidden` and `hiddenEverywhere` in hull_mods.csv, so it doesn't show in refit's hull features list either), which reads `stats.getFleetMember()` and calls `modifyFlat`. It's added with `addPermaMod(id, false)` so refit strip/autofit can't remove it, the same approach as Progressive S-Mods (`../Progressive S-Mods-1.2.0/src`, a useful reference). Don't run both mods together: they stack bonuses on the same stat.
- Building in: use the variant's permanent/S-mod methods (for example ShipVariantAPI.addPermaMod(id, true)). Check how vanilla does it and copy that behaviour.
- XP from battles: use a campaign listener (BaseCampaignEventListener, for example reportPlayerEngagement or reportBattleFinalized) and read per-ship results from the engagement result. If per-ship damage isn't exposed there, use an EveryFrameCombatPlugin to record damage during combat, then hand it to the campaign side afterwards.
- Credits: Global.getSector().getPlayerFleet().getCargo().getCredits().
Build a jar, not loose Janino scripts, because this needs listeners, a custom dialog and generics. Mod plugin: BaseModPlugin subclass, registering listeners in onGameLoad.
- Save compatibility on removal is NOT a goal: the mod is not safe to remove from a save (the hidden `xpsmods_slots` hullmod is stored on ship variants). Don't add strip/restore-on-save workarounds. Plain maps of primitives and strings in persistent data are still preferred for simplicity.

Milestones (one at a time, test in-game after each)
- mod_info.json plus a jar build that compiles against starfarer.api.jar, with an empty mod plugin. The game loads it with no log errors.
- Settings file plus XP tracking. A debug log line shows XP earned per ship after each battle.
- Extra-slot stat bonus applied from stored data. The vanilla refit screen shows the raised limit.
- Refit screen panel and button under "Build in": view a ship's XP and buy slots.
- Build in and remove S-mods for credits in the same panel.
- Polish: refit panel styled close to vanilla UI; upgrade-available icon instead of an XP count; balance; edge cases (ship sold, scuttled, lost or recovered; pruning stored entries for ships that no longer exist; multi-section ship modules; building in built-in hullmods that vanilla can enhance; d-mods; hullmods that can't be built in).
