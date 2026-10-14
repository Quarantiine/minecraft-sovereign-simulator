# ⚔️ Sovereign Simulator: Strategy & Engineering

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.x-brightgreen.svg)](https://www.minecraft.net/)
[![Mod Loader](https://img.shields.io/badge/Loader-Fabric-blue.svg)](https://fabricmc.net/)
[![Java Version](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-Visible%20Source%20%26%20ARR-red.svg)](LICENSE)
[![Unit Tests](https://img.shields.io/badge/Tests-645%20Passing-success.svg)](src/test/java)

A tactical real-time strategy (RTS) and multiblock engineering mod for **Minecraft 1.21**, built on the **Fabric Loader**, **Fabric API**, and **Gradle Loom** toolchain.

**Sovereign Simulator** transforms your Minecraft world into a living strategic theater and automated architectural sandbox. Command squads of autonomous minion thralls, deploy military ranked formations, execute coordinated 90° mass assaults, script visual laser patrol routes, and capture or erect complex multiblock structures in real time.

---

## ⚖️ License & Permissions: At a Glance

This project is distributed under a **Visible Source & All Rights Reserved (ARR)** license model. We believe in open, transparent code that developers and players can inspect and learn from, paired with firm legal safeguards protecting our brand, custom art assets, and player community.

> 📖 Read the full legal agreement in the [**LICENSE**](LICENSE) file.

### Quick Reference Matrix

| Activity                                   |      Status       | Conditions / Details                                                                                                                        |
| :----------------------------------------- | :---------------: | :------------------------------------------------------------------------------------------------------------------------------------------ |
| **Inclusion in Modpacks**                  |  ✅ **ALLOWED**   | Free inclusion in public or private packs (CurseForge, Modrinth, FTB, Prism, etc.) with author attribution (`Mino`) and intact JAR/LICENSE. |
| **Content Creation & Streaming**           |  ✅ **ALLOWED**   | Record gameplay, stream on Twitch/YouTube, and monetize your videos without restriction.                                                    |
| **Source Code Inspection**                 |  ✅ **ALLOWED**   | Read, study, debug, and review the code for educational and security purposes.                                                              |
| **Local Personal Compilation**             |  ✅ **ALLOWED**   | Clone and build the project locally on your machine for private, non-commercial gameplay.                                                   |
| **Developing Addons / Compatibility Mods** |  ✅ **ALLOWED**   | Build 3rd-party addon mods compiling against Sovereign's public API without bundling core JARs or assets.                                   |
| **Submitting Bugfixes & Pull Requests**    |  ✅ **ALLOWED**   | Contributions to the official repository are welcomed under the [Contributor License Agreement](LICENSE#4-contributions--pull-requests).    |
| **Re-Hosting & Scraper Mirror Sites**      | ❌ **PROHIBITED** | No re-uploading binaries to external sites (e.g., 9Minecraft), custom launchers, or URL shorteners (AdFly).                                 |
| **Commercial Exploitation & Paywalls**     | ❌ **PROHIBITED** | No selling the mod, charging for access, or locking mod features behind server VIP ranks, donations, or crypto/NFTs.                        |
| **Art Asset & Texture Reuse**              | ❌ **PROHIBITED** | 3D models, textures, animations, audio, and UI graphics are proprietary All Rights Reserved. No extracting or repurposing in other mods.    |
| **Rebranded Clones & Public Forks**        | ❌ **PROHIBITED** | No publishing identical clones, rebranded derivatives, or repackaged versions that mimic the mod.                                           |

---

## 🧭 Navigation Portal

- [🎮 Player & Commander Guide](#-player--commander-guide)
  - [The 4 Minion Archetypes](#the-4-minion-archetypes)
  - [Core Strategic Systems](#core-strategic-systems)
  - [Survival Progression & Crafting Recipes](#survival-progression--crafting-recipes)
  - [Tactical Controls Cheat Sheet](#tactical-controls-cheat-sheet)
  - [Installation Guide](#installation-guide-for-players)
- [🛠 Developer & Contributor Hub](#-developer--contributor-hub)
  - [Architecture Blueprint & Codebase Map](#architecture-blueprint--codebase-map)
  - [Local Development Setup](#local-development-setup)
  - [Compiling & Running](#compiling--running)
  - [Publishing & Releases](#publishing--releases)
  - [Writing Addons & API Usage](#writing-addons--api-usage)
  - [Automated Testing & Invariants](#automated-testing--invariants)
- [📚 Companion Documentation](#-companion-documentation)

---

## 🎮 Player & Commander Guide

### The 4 Minion Archetypes

Minions are tameable, persistent humanoid thralls equipped with backpacks, equipment inventories, dynamic overhead hearts indicators, and specialized tactical AI routines.

```
       [⚔ WARRIOR]                  [🛡 SENTINEL]                  [🔨 BUILDER]                  [⚙ AUTO]
  Frontline Melee & Thrown      Perimeter Guard & Medic       Architect, Quarry & Logistics   Autonomous Adaptive AI
```

1. **⚔ Warrior (`MinionRole.WARRIOR`)**:
   - **Frontline Combatant & Hunter**: Masters swords, axes, maces, bows, and crossbows.
   - **Frontline Damage Boost (+4.0 Attack Damage)**: Built as offensive vanguard shock troops, Warriors hit significantly harder than other roles via `WARRIOR_ATTACK_DAMAGE_BONUS = 4.0D`. Unarmed punches deal `9.0` base physical damage (vs. Sentinel/Builder `5.0`), and equipped melee weapons deal `+4.0` bonus damage across all tiers (`15.0` damage with an Iron Sword vs. Sentinel's `11.0`; `16.0` with a Diamond Sword vs. Sentinel's `12.0`). Hits feature server-side critical hit particle bursts (`CRIT`) and visceral knockback audio cues.
   - **Trident Duality**: Throws piercing tridents at long range ($5\text{D}\text{--}20\text{D}$) and seamlessly transitions into deadly close-quarters melee thrusts ($\le 5\text{D}$) with server-side loyalty retrieval.
   - **Tactical Ordnance**: Wields throwable TNT Sticks and cryogenic Frost Grenades.
   - **Procurement Contracts**: Automatically accepts hunting commissions from Builders to hunt mob resources (wool, bones, slime, leather) and delivers them through peer-to-peer logistics.

2. **🛡 Sentinel (`MinionRole.SENTINEL`)**:
   - **Perimeter Guard & Combat Medic**: Guards designated anchor posts with an 8-block perimeter and 128-block leash, prioritizing shields in offhand. Deals baseline `5.0` physical damage (unaffected by warrior damage bonuses) to focus role attributes strictly on defensive endurance and squad sustain.
   - **Wall-Penetrating Sentry Senses & Echolocation**: Detects and locks onto allowed hostiles through solid walls, closed doors, ceilings, and floors within 24m horizontal and an expanded 12m vertical reach (`ignoreVisibility`). Obstruction detection triggers soul vibration particles (`SCULK_SOUL`), sculk sensor clicking, and resonant amethyst chimes, snapping gaze toward vibrations and mobilizing idle combat allies within 16m to intercept.
   - **Aegis of Restoration**: Autonomously channels healing beams to wounded player commanders, allied minions, and friendly **Iron Golems** under 70% HP within 10 blocks (restoring 6.0 HP + Regeneration II for 5s with heart VFX and amethyst resonance).
   - **Warrior Combat Dual-Mode**: Sentinels fight aggressively like Warriors (active hostile target scanning, melee charge at $1.35\text{D}$, and ranged weapon skirmishing) **strictly when all nearby minions, Iron Golems, and commanders need zero healing** (100% full health). The instant an ally takes damage, the Sentinel disengages immediately (`setTarget(null)`) to prioritize medical protection.

3. **🔨 Builder (`MinionRole.BUILDER`)**:
   - **Master Architect & Demolition Specialist**: Autonomously constructs captured blueprints, deconstructs designated volumes, and quarries stone top-to-bottom.
   - **Absolute Non-Combatant & Combat Immunity**: Builders strictly never engage in combat, acquire targets, or retaliate when struck. They are immune to assault commands, Sentinel echolocation alarms, and patrol breach alerts, and actively building minions never drop tools to fight.
   - **Builder Block Phasing & Anti-Entombment**: Seamlessly passes through blocks (`noClip = true`) while actively building or evacuating completed structures. Continuous server-side anti-suffocation safeguards guarantee minions are never trapped inside solid blocks or stranded underground.
   - **Logistics Autonomy**: Agro-forestry timber harvesting (using bone meal for instant sapling maturation), tool self-crafting, supply depot chest drops, peer-to-peer wireless block transfers, and expanded 32-block container search radius (16 vertical) with chunk-level indexing to pull materials or store excess.
   - **Intelligent Post-Work Evacuation & Free Roaming Relocation**: Automatically evacuates to the surface rim on surface quarries, cleared interior room floors on underground mines, and exterior safe ground around buildings with beacon beams and fanfare chimes, releasing minions to roam freely on foot until the next blueprint is placed.

4. **⚙ Auto (`MinionRole.AUTO`)**:
   - **The Default Dynamic Archetype**: Newly summoned or recruited minions start in `AUTO` mode, evaluating their tactical environment dynamically and reacting instantly when taking damage:
     - Morphs to **Sentinel** if this minion itself, the commander, or any nearby ally drops below 70% HP (equipping shield and channeling restorative healing) or when holding a guard post.
     - Morphs to **Warrior** if actively attacked, targeting an enemy, or hostile mobs enter within 16m (dynamically gaining the `+4.0` frontline Attack Damage bonus and aggressive assault AI).
     - Morphs to **Builder** if peaceful and an active blueprint or quarry task requires engineering hands within 128 blocks.

---

### Core Strategic Systems

- **Free Survival Arcane Build Flight & Shallow Water Tolerance**:
  - Enjoy unconstrained 3D creative-style flight in Survival mode while holding the Scepter in **`BUILD`** mode (Space to ascend, Shift to descend, full WASD navigation).
  - Shallow water $\le 5$ blocks high/deep from solid ground (creeks, ponds, moats, canals, puddles) is fully permitted for flight without interruption. Only deep water bodies ($> 5$ blocks deep) cancel flight and `BUILD` mode; minions continue building uninterrupted.
- **Anti-Suffocation, Anti-Entombment & Safe Evacuation**:
  - Minions safely phase through blocks while building (`noClip = true`), with automatic server-side anti-suffocation safeguards (`unstuckIfInSolidWall`) ensuring units are never stuck in walls or underground rock.
  - Mining sessions evacuate minions intelligently: surface quarries evacuate workers to the open-sky rim overlooking the pit, while underground room excavations station workers onto the cleared interior floor (`box.getMinY()`), never clipping into solid perimeter rock.
- **Collaborative Multi-Minion Building & Dynamic Task Stealing**:
  - Multiple builders seamlessly collaborate on the same blueprint. When a minion is distant, offline, or stalled past lease grace thresholds, peers automatically take over its claimed block task.
  - The moment another minion completes or steals a task, the original claimant immediately forgets the task and acquires the next available block, eliminating idle loops and build deadlocks.
  - Scepter blueprint placement persistence ensures repeated stamping of blueprints never auto-cycles the catalog.
- **Global Target Filtering, Instant Search & Named Presets**:
  - Press `[ 🎯 Targets ]` in the Command Hub GUI (`V`) to customize rules of engagement for all hostile mobs.
  - Features an instant search bar to find mobs by name or ID path, plus title-only saved presets (`All Hostiles`, `Creeper Safe`, `Undead Only`, `Raiders`, `None (Passive)`, and player custom presets saved to JSON).
- **Individual Minion Combat Policies**:
  - Customize engagement for individual minions via `MinionScreen`'s top badge bar (`GLOBAL`, `PASSIVE`, `DEFENSIVE`, `CUSTOM`).
  - Set logistics or builder minions to `PASSIVE` so they never attack or provoke enemies, or assign dedicated assault units custom target lists.
- **The Sovereign Command Scepter**:
  - Handheld tactical conduit supporting 64-block raycast targeting.
  - Left-Click to select/deselect individual units or hold Right-Click for 90° cone sweeps.
  - Right-Click the ground to deploy military waypoint lines.
- **Ranked Army Battle Formations**:
  - Minions automatically calculate disciplined, parallel 4-unit battle ranks: Warriors form the hard-hitting vanguard frontline ($+4.0\text{D}$) to intercept and shred threats, Sentinels form the protective midline escort ($+1.8\text{D}$) ready to shield and heal wounded units, and Builders bring up the rear support ($ -2.0\text{D}$).
- **Minion Escort Hierarchy & Squad Leaders**:
  - Delegate minions to bodyguard fellow minions. Shift-Punch an owned minion with the Scepter to designate a **Squad Leader**; all selected escorts dynamically form protective flanking ranks around their leader with active cyan-to-magenta Arcane Tether Beams.
- **Synchronized Minion Sneaking & Tactical Overhead Stealth**:
  - When the player sneaks (holds `Shift`), all currently selected minions (and squad bodyguards) immediately drop into a synchronized crouching pose (`EntityPose.CROUCHING`).
  - Completely suppresses and hides both vanilla nametags and modded overhead badges (`[HOLD]`, squad banners, role crests, health hearts) for covert night raids, stealth ambushes, and multiplayer scouting.
  - Automatically paces following movement to matched crouch walking speed ($0.65\text{D}$) within 8 blocks, accelerating to catch-up speed ($1.15\text{D}$) when separated by $>8$ blocks.
- **Custom In-World Blueprint Capture ('DESIGN' Mode)**:
  - Select two corners with left-clicks to enclose any building in a 3D holographic ghost prism.
  - Adjust height in real time with `Ctrl` + Scroll.
  - Name and save the structure directly to world data without destroying the original building. Minion builders can then replicate it anywhere with 100% exact block fidelity!
  - **Granular Palette Remapping & Material Replacement (`[ 🎨 Remap ]`)**: Accessible in both the capture dialog and the 3D Blueprint Explorer. Inspect the full block tally, substitute any block with another placeable block via the searchable `BlockPickerModalScreen`, or erase unwanted blocks into `minecraft:air`. Preserves directional blockstate properties (stairs, slabs, logs, pillars) and allows choosing **[ 💾 Overwrite ]** or **[ 📋 Save as Copy ]** with zero paging friction (Slot 1 Slide 1 default).
  - **Newest-First Catalog Ordering**: Newly captured blueprints immediately appear at Slot 1 on Slide 1 by default, automatically armed on your held Scepter.
  - **3D Blueprint Explorer & Clarity Viewport GUI**: Click `[ 🔍 3D View ]` in the Command Hub to open `BlueprintExplorerScreen`, featuring true 3D rotated quad rendering, 6-directional interior occlusion culling, authentic botanical flora and multi-face textures, directional sunlight ($N \cdot L$), Fullscreen/Maximize mode (`[ ⛶ ]` / `Tab` / `F`), an Architectural Floor Slicer (`▲` / `▼`), in-viewer Palette Remapping (`[ 🎨 Remap ]`), in-viewer Custom Blueprint Renaming (`[ ✏ Rename ]` / `[R]`) with catalog order preservation, an uncluttered spacious action button bar (`[ ✔ Arm ]`, `[ 🎨 Remap ]`, `[ 📋 BOM ]`, `[ 🗑 Del ]`, `[ Close ]`), high-performance pre-cached mesh rendering (`BlueprintMeshCache`) with zero-allocation depth sorting for mega-structures, 2D camera panning, $0.25\times$ to $8.0\times$ zoom, ground pedestal, real-time cardinal compass, and zero text cut-off across all resolutions.
- **Visual Pathway Patrols**:
  - Script custom patrol loops or ping-pong routes with glowing checkpoint badges (`[ 1 ]`, `[ 2 ]`) and luminous laser vector tethers.
  - **Single Initial Default Pathway (Zero Auto-Creation)**: Starts with only **one** initial default pathway (**Route 1 🟡 Gold `#FFD700`**). Pathways are **never** auto-created—players have total control to add more routes on their own.
  - **Newest-First Route Ordering**: Newly created routes appear **first in order** (Slide 1 Slot 1) across the Command Hub dashboard and scepter cycling, with exact order preserved across world saves.
  - Customize routes with 24-bit RGB hex colors or built-in presets via the interactive `PatrolRouteEditModalScreen` GUI.
  - **Autonomous Free Roaming on Route Deletion**: Deleting a patrol route cleanly unbinds assigned minions, clears guard anchors, and allows them to roam freely on foot rather than locking into a stationary hold position.
- **Dual-Tier Panic Retreat**:
  - Press **`R`** to immediately recall active squad members within 64m.
  - Press **`Shift + R`** for an army-wide **Emergency Citadel Call** (128m), sounding war horns and tolling fortress bells to sprint all stationed units to the commander's defense.
- **Survival Progression & Crafting Recipes**:
  - **Sovereign Command Scepter**: Crafted with 2 Gold Ingots, 1 Amethyst Shard, and 2 Blaze Rods (or Sticks) in a crafting table.
  - **Minion Spawn Egg**: Crafted shapelessly with 1 Egg, 1 Amethyst Shard, 1 Emerald, and 1 Gold Ingot.
  - **Tactical Ordnance**: Craft TNT Sticks (TNT + Stick) and cryogenic Frost Grenades (Ice / Snowball + Stick).
  - **Vanilla Recipe Book Integration**: Native recipe advancements automatically unlock these blueprints in your crafting table recipe book the moment you pick up their constituent ingredients (Amethyst Shards, Gold Ingots, Emeralds, Gunpowder, or Snowballs).
  - **Enthrallment in Survival**: Alternatively, switch the scepter to `RECRUIT` mode and right-click living vanilla mobs (zombies, skeletons, villagers, etc.) to transfigure them into minion thralls!

---

### Tactical Controls Cheat Sheet

| Key / Action           | Context            | Tactical Function                                                                                                                                    |
| :--------------------- | :----------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`V` Key**            | Scepter in Hand    | Opens the **Command Hub GUI** (squad channels, modes, patrol dashboard, blueprints, target filter).                                                  |
| **`R` Key**            | Scepter in `BUILD` | **Rotate Blueprint**: Rotates hologram 90° clockwise (0° → 90° → 180° → 270°).                                                                       |
| **`R` Key**            | Other Modes        | **Squad Retreat**: Recalls selected active squad minions within 64m.                                                                                 |
| **`Shift + R`**        | Scepter in Hand    | **Citadel Call**: Army-wide emergency alarm (128m) recalling all units and unbinding patrols.                                                        |
| **Left-Click**         | Scepter in `BUILD` | **Normal Hit / Break** (`BLUEPRINT`) / **Pos1 & Pos2** (`AREA`).                                                    |
| **`H` Key**            | Scepter in `BUILD` | Toggles the elevated **Tactical RTS Build Camera**.                                                                                                  |
| **Arcane Flight**      | Scepter in `BUILD` | **Survival 3D Flight**: Space/Shift/WASD flight; shallow water $\le 5$ blocks high permitted.                                                        |
| **`Ctrl` + Scroll**    | `DESIGN` / `MINE` / `BUILD` | Real-time in-world 3D boundary height adjustment (hold `Shift` for $\pm 5$ fast stepping).                                                   |
| **Right-Click (Hold)** | `FOLLOW` / `STAY`  | Charges the **Banner of Courage (90° Sector)**; release to launch a synchronized Mass Assault (preserves hold position, patrol routes, and roaming). |
| **Shift + Left-Click** | Minion Target      | **Designate Squad Leader**: Assigns all currently selected minions to escort the clicked leader.                                                     |
| **Sneak + Left-Click** | `DESIGN` / `MINE` / `BUILD` | Clears both `Pos1` and `Pos2` selection corners, restarting spatial targeting cleanly.                                                               |
| **Sneak (Hold Shift)** | Leading Minions    | **Tactical Squad Sneak**: Minions crouch in sync; hides nametags and all overhead HUD badges.                                                        |
| **`Tab` / `F` Key**    | In 3D Explorer     | **Maximize Viewport**: Toggles full-width $404 \times 194$ px 3D inspection view.                                                                    |
| **Right-Click Drag**   | In 3D Explorer     | **Camera Pan**: Translates 3D model horizontally and vertically.                                                                                     |
| **`▲` / `▼` Slicer**   | In 3D Explorer     | **Architectural Floor Slicer**: Steps through floors to inspect interior room layouts.                                                               |

> 📖 For the exhaustive controls manual, see [**COMMANDS.md**](COMMANDS.md).

---

### Installation Guide for Players

1. Install **Minecraft: Java Edition (1.21)** via the official launcher.
2. Install **Fabric Loader** ($\ge 0.16.0$) from [fabricmc.net](https://fabricmc.net/use/installer/).
3. Download the matching **Fabric API (1.21)** from [Modrinth](https://modrinth.com/mod/fabric-api) or [CurseForge](https://curseforge.com/minecraft/mc-mods/fabric-api).
4. Drop both the **Fabric API JAR** and the **Sovereign mod JAR** into your `.minecraft/mods` folder:
   - **Windows**: `%APPDATA%\.minecraft\mods\`
   - **macOS**: `~/Library/Application Support/minecraft/mods/`
   - **Linux**: `~/.minecraft/mods/`
5. Select the **fabric-loader-1.21** profile in the Minecraft launcher and launch the game!

> **Tip for Multi-Version Setups**: If you run multiple Minecraft versions (e.g., 1.21 alongside other versions or snapshots), configure each launcher installation with a dedicated "Game Directory" (such as `.minecraft/instances/1.21/`). This guarantees that 1.21 Fabric mods remain isolated and cannot cause class loader conflicts or crashes when launching other versions.

---

## 🛠 Developer & Contributor Hub

### Architecture Blueprint & Codebase Map

The mod follows an event-driven, server-authoritative architecture built on Fabric 1.21:

```
src/main/java/com/example/
├── ExampleMod.java                   # Mod initialization, lifecycle events, item registration
├── block/                            # Custom blocks and interactive multiblock stations
├── blueprint/                        # Spatial blueprint capture, CustomBlueprintManager & StructureBlueprint
├── construction/
│   ├── ConstructionManager.java      # Server-authoritative build session manager & voxel iterator
│   ├── ConstructionSession.java      # Active blueprint/mining session state machine
│   └── BlueprintManager.java         # NBT serialization and world-save persistence (.dat)
├── entity/
│   ├── custom/
│   │   ├── MinionEntity.java         # Core entity: state, equipment, physics, navigation & AI goals
│   │   ├── MinionRole.java           # Archetype roles (WARRIOR, SENTINEL, BUILDER, AUTO)
│   │   └── FrostGrenadeEntity.java   # Cryogenic projectile entity & terrain freeze logic
│   └── ai/
│       ├── goal/                     # Dedicated AI goals (Aegis healing, formations, patrols, sappers)
│       └── logistics/                # Autonomous harvesting, peer sharing & chest deposition
├── item/custom/
│   └── CommandScepterItem.java       # Handheld tactical relic, raycasts, UI triggers & packet dispatches
├── network/                          # Fabric Networking API (Custom payloads, client-server sync)
└── sound/                            # Custom SFX (amethyst chimes, war horns, bells, arcane resonance)

src/client/java/com/example/client/
├── ExampleModClient.java             # Client entrypoint, keybinding registration & render tick events
├── gui/                              # Screens (Command Hub, Minion GUI, Route Editor, Modals)
└── renderer/                         # 3D world renderers (holographic ghost grids, lasers, badges)
```

---

### Local Development Setup

#### Prerequisites

- **Java Development Kit (JDK) 21+** (required for Minecraft 1.21). Check with `java -version`.
- **Git** installed on your system.
- Modern IDE: **IntelliJ IDEA** (strongly recommended with Minecraft Development plugin) or **VS Code**.

#### Clone & Initialize

```bash
git clone https://github.com/<your-username>/minecraft-modding.git
cd minecraft-modding
```

---

### Compiling & Running

All builds and runtime tasks are managed via the included Gradle wrapper (`./gradlew`):

```bash
# Launch the Loom sandboxed Minecraft client with hot-reload and Yarn mappings
./gradlew runClient

# Launch a standalone dedicated server for network/multiplayer debugging
./gradlew runServer

# Run the complete automated test suite (639 tests)
./gradlew test

# Compile and package the production mod JAR (outputs to build/libs/)
./gradlew build
```

---

### Publishing & Releases

To bump the version, commit, build, and publish a GitHub release with the compiled mod `.jar` in a single command:

```bash
VERSION="1.1.1"
TITLE="v${VERSION} - Title"
NOTES="Description here..."

sed -i '' "s/^mod_version=.*/mod_version=${VERSION}/" gradle.properties && \
git add gradle.properties && \
git commit -m "chore: bump version to ${VERSION}" && \
git push && \
./gradlew build && \
gh release create "v${VERSION}" "build/libs/fabric-mmcli-agent-modding-${VERSION}.jar" \
  --title "${TITLE}" \
  --notes "${NOTES}"
```

---

### Writing Addons & API Usage

Sovereign explicitly permits independent third-party mods to compile against its public interfaces:

- **Querying Minion Archetypes**: Access `minion.getRole()`, `minion.getEffectiveRole()`, and `minion.matchesRole(MinionRole.X)`.
- **Interrogating Healing Status**: Query `minion.hasNearbyAlliesNeedingHealing(radius)` to evaluate squad medical status.
- **Teammate Integration**: `MinionEntity.isAlliedTeammate(Entity other)` seamlessly respects allied minions, owners, and non-hostile Iron Golems without overriding final methods.
- **Listening to Construction Events**: Hook into `ConstructionManager.getInstance()` to track blueprint start, progress, and completion callbacks.

---

### Automated Testing & Invariants

We maintain an extensive automated unit and invariant test suite located in `src/test/java/com/example/`. Tests execute in headless Java without launching a full graphical client:

- **Entity & AI Goals**: `SentinelHealGoalTest`, `SentinelSentrySenseTest`, `BuilderNoCombatTest`, `MinionFormationAndEquipTest`, `MinionSquadAndRoleTest`
- **Logistics & Harvesting**: `MinionLogisticsAndHarvestingTest`, `MinionAntiJitterAndLevitationSafetyTest`
- **Construction & Dismantling**: `StructureDismantlingTest`, `MiningChainingEgressTest`, `BedrockAndCeilingSafeguardTest`, `SpatialBlueprintCaptureAndSortingTest`
- **Blueprint UI & Palette Remapping**: `BlueprintPaletteRemapModalTest`, `BlueprintCaptureModalScreenTest`, `GuiBlurRemovalTest`
- **Ordnance & Physics**: `FrostGrenadeTest`
- **Recipes & Multi-Version Compatibility**: `ModRecipeJsonValidationTest`, `CommandScepterTooltipAndComponentsTest`, `FabricVersionPredicateTest`

Always verify that the entire suite passes cleanly before submitting contributions:

```bash
./gradlew test --rerun-tasks
```

---

## 📚 Companion Documentation

For in-depth guides and technical specifications, consult our companion documentation:

- 🎮 [**COMMANDS.md**](COMMANDS.md) — Exhaustive scepter control manual, keybindings, and tactical commands.
- ⚙️ [**FEATURES.md**](FEATURES.md) — Deep-dive architectural specification, math formulations, packet schemas, and AI goal hierarchies.
- 📜 [**CHANGELOG.md**](CHANGELOG.md) — Release notes and version history across all updates.
- ⚖️ [**LICENSE**](LICENSE) — The complete Visible Source & All Rights Reserved legal license.

---

<p align="center">
  <b>Sovereign Simulator: Arcane Strategy & Engineering</b><br>
  Copyright © 2026 Mino (and Project Contributors). All Rights Reserved.
</p>
