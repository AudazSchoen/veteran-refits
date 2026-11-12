# Veteran Refits

A Starsector mod where your ships earn XP in combat. Spend a ship's XP to raise its S-mod limit, then build in or remove S-mods with credits.

Vanilla's story-point build-in still works. This mod adds a second way to get S-mods.

- **Game version:** 0.98a-RC8
- **Requires:** [LunaLib](https://fractalsoftworks.com/forum/index.php?topic=25658.0)
- **Download:** see [Releases](https://github.com/AudazSchoen/veteran-refits/releases), or install and update it with [TriOS](https://github.com/wispborne/TriOS) (supports Version Checker)

## Features

- **Ship XP from combat.** Deployed ships earn XP for the hull damage they deal and for the enemy ships they help knock out. Ships in reserve get a share, and a deployed ship never earns less than that share. Ships disabled in the battle earn nothing (optional).
- **Extra S-mod slots.** Spend a ship's XP to raise its S-mod limit. The raised limit also shows in the vanilla refit screen.
- **Build in and remove S-mods for credits.** This uses a dialog styled after vanilla's build-in dialog. It also handles:
  - enhancing built-in hullmods that have an S-mod effect, like vanilla
  - modules of multi-section ships, where each module may use up to the main ship's limit
  - removing S-mods for a fee (the bought slot stays)
- **Upgrade indicators.**
  - A green arrow on refit ship cards marks ships that can buy their next slot.
  - After each battle, a report lists the XP each ship earned and which ships have an upgrade available.
- **Everything is configurable** in LunaLib's mod settings, and changes apply without a restart.

## Installation

1. Install [LunaLib](https://fractalsoftworks.com/forum/index.php?topic=25658.0).
2. Download `Veteran-Refits-<version>.zip` from [Releases](https://github.com/AudazSchoen/veteran-refits/releases).
3. Unzip it into `Starsector/mods/`, so you get `mods/Veteran Refits/mod_info.json`.
4. Enable **Veteran Refits** and **LunaLib** in the launcher.

The mod can be added to an existing save.

## How to use

Open the refit screen and press **Ship upgrades** under vanilla's **Build in** button (hotkey **S**). It also appears in the add-hullmod picker and in LunaLib's refit button list.

- **Buy a slot:** spend the ship's XP on an extra S-mod slot. This works anywhere.
- **Build in or remove:** tick hullmods in the table, then press **Confirm (G)**. You must be docked at a market, and it costs credits.

## Default rules

All of these can be changed in LunaLib's mod settings (F3 by default, then **Veteran Refits**).

| | Default |
|---|---|
| Damage XP | 0.05 XP per point of hull damage dealt |
| Kill XP | 100 per enemy ship disabled or destroyed, × enemy size (frigate 1, destroyer 1.5, cruiser 2, capital 3), split by each ship's share of the damage (evenly when auto-resolved) |
| Reserve ships | 25% of the average XP of the deployed ships that weren't disabled (mothballed ships get nothing) |
| Disabled ships | earn no XP from that battle |
| Extra slots | up to +3 per ship |
| Slot cost | 1,000 XP × 2ⁿ (1,000 / 2,000 / 4,000) |
| Build-in cost | 1,000 credits per OP × hull size (1 / 1.25 / 1.5 / 2), minimum 1,000 |
| Enhancing a built-in | half the build-in cost; doesn't use a slot |
| Removing | 25% of the build-in cost |
| Forgetting ships | data for ships not seen in your fleet or any storage for 12 months is dropped; ships lost in battle are dropped right away |

## Compatibility

- **Not safe to remove from a save.** A hidden hullmod is stored on your ships to apply the extra slots.
- **Don't use it together with Progressive S-Mods.** Both raise the same S-mod limit stat.
- The refit button and the ship card icons reach into the game's refit UI through reflection. If a game update breaks that, they switch off and log an error. The entry in LunaLib's refit button list keeps working.

## Credits

Some techniques were learned from reading these mods. No code was copied.

- **[LunaLib](https://fractalsoftworks.com/forum/index.php?topic=25658.0)** by Lukas04: the refit button and panel API, the mod settings, and the workaround for reflection into the game's UI.
- **[Progressive S-Mods](https://fractalsoftworks.com/forum/index.php?topic=23006.0)** by qcwxezda and chdcl: the original idea of earning S-mods through ship XP, raising the S-mod limit with a hidden hullmod, and its build-in rules.
- **[Exiled Sector](https://fractalsoftworks.com/forum/index.php?topic=36072.0)** by Portals: placing a button under vanilla's "Build in" button, and the refit screen's frame measurements.

## AI use

This mod was made with an AI coding assistant, Claude (Anthropic), used through Claude Code.

- **The author:** designed the mod, decided its features and balance, and tested every change in game.
- **Claude:** wrote most of the code, based on the author's design, feedback and in-game screenshots. Commits it helped write carry a `Co-Authored-By: Claude` line.

## License

[MIT](LICENSE)
