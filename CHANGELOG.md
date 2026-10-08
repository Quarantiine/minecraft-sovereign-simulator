# Changelog

All notable changes to **Sovereign Simulator: Arcane Strategy & Engineering** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-10-08

### ⚔️ Initial Release: Sovereign Simulator: Arcane Strategy & Engineering

Initial public release of **Sovereign Simulator: Arcane Strategy & Engineering** (`modid-mmcli-agent-modding`), transforming Minecraft into an autonomous real-time strategy (RTS) theater and architectural sandbox for **Minecraft 1.21** on the **Fabric Loader**.

---

### Added

#### 🪄 Loki Command Scepter & Tactical Warfare
- **Loki Command Scepter (`command_scepter`)**: Epic tactical command staff supporting multiple command modes: `FOLLOW`, `STAY`, `MINE`, `BUILD`, `RECRUIT`, and `DESIGN`.
- **Channeled 90° Forward Sector ("Banner of Courage")**:
  - Right-click channeled ability projecting an expanding forward cone ($\pm 45^\circ$ FOV) with boundary laser particles and real-time outline highlighting.
  - Mass rallies enclosed minions, triggers instant role transfiguration, and coordinates focused Mass Assaults against detected hostiles with raid horn fanfare.
- **Remote Tactical Waypoint Pinging**: Quick-tap ground blocks with the scepter to deploy squads into yaw-aligned tactical stations without walking over.
- **Dual-Tier Panic Retreat System**:
  - **Tier 1 Tactical Squad Retreat (`R` key)**: Sounds an emergency warning bell, clears combat targets on selected minions within 64 blocks, cancels active building sessions, dismisses 3D wireframes, and recalls units into rank formations.
  - **Tier 2 Emergency Citadel Call (`Shift + R`)**: Fortress-wide emergency muster across a 128-block radius; unbinds all minions from patrols and hold postures, sounds a resonant Horn & Bell alert, and rushes every unit back to defend the commander.
- **Visual Pathway Patrol System**:
  - Link in-world ground waypoints with the scepter to establish repeating patrol routes.
  - Visual hologram renderer projects directional particle beams along patrol nodes.
  - Dedicated **Patrol Route Edit Modal (`PatrolRouteEditModalScreen`)** for route configuration and squad assignment.
- **Rules of Engagement & Target Filter System**:
  - **Minion Target Filter Modal (`MinionTargetFilterModalScreen`)** allows commanders to customize hostile mob targeting priorities (e.g., creepers, skeletons, zombies, or neutral exclusion).
  - Target rules sync across clients via `UpdateTargetFilterPayload`.

#### 🤖 Autonomous Minion Thralls & Archetype System
- **Minion Thralls (`MinionEntity`) & Spawn Egg**: Recruitable autonomous humanoid units with custom equipment slots, 9-slot backpacks, and overhead status badges.
- **Clean-Slate Spawning & Dynamic Role Disarming**: Minions initialize with empty hands and backpacks; switching roles automatically disarms invalid equipment into the backpack or drops overflow at their feet.
- **Upright Attention Stance**: Minions stand at full 100% military posture on station with zero sitting offsets or decoupled riding hitches.
- **Specialized 3-Pillar Archetype Architecture**:
  - **⚔️ Warrior**: Frontline vanguard master of both melee and ranged combat:
    - Automatically equips and wields Swords, Axes, Maces, Bows, Crossbows, Frost Grenades, and TNT Sticks.
    - **Trident Combat Duality**: Seamlessly executes close-quarters melee thrusts (< 5m) or ranged javelin throws (5–20m) with authentic throwing sounds and pickup prevention.
  - **🛡️ Sentinel**: Defensive midline shield unit featuring **Aegis of Restoration**:
    - Continuously scans allies within 10 blocks; channels a restorative beam to heal wounded minions or the player commander below 70% HP (+6.0 HP, Regeneration II for 5s, emerald/heart VFX, and arcane chime audio).
  - **🏗️ Builder**: Civil engineering and logistics specialist:
    - 100% zero-footprint **Arcane Levitation** flight for all elevated construction.
    - Autonomous obstacle vaulting, block phasing (`noClip`), post-construction egress, and 360° perimeter flank settlement.
- **Autonomous Agent Mode (`AUTO`)**: Dynamic contextual evaluator allowing minions to assess nearby allies, structures, and threats in real time to self-assign the optimal operational role (`Sentinel` if allies wounded, `Warrior` if enemies present, `Builder` if construction active, or peacetime escort).
- **Parallel Military Rank Formations**: Units align cleanly along commander-facing battle lines: Frontline Warriors (+4.0D), Midline Sentinels (+1.8D), and Rearguard Builders (-2.0D) with mutual push suppression to prevent collision jitter.

#### 🏛️ Multiblock Architecture & Spatial Blueprint Engine
- **Universal Arcane Levitation (Scaffolding-Free Construction AI)**:
  - Completely eliminates temporary scaffolding blocks, climbing hitches, and suffocating towers.
  - Builders fly and hover in 3D at optimal work stations with swirling portal/enchant rune particles and fall damage immunity.
- **Arcane Phase-Shift & Zero-Timeout Completion**:
  - Eradicates task abandonment and navigation surrender loops. If an interior detail or path is occluded for $\ge 40$ ticks, the builder executes an Arcane Phase-Shift, teleporting directly to the work station to resume placement.
- **Spatial Blueprint Capture (`DESIGN` Mode)**:
  - In-world anchor selection (`Pos1` and `Pos2`) starting directly on floor surfaces adhering to the Unified Surface Anchoring Contract.
  - Real-time vertical height adjustment using `Ctrl` + Mouse Scroll (with `Shift` for $\pm 5$ fast stepping), keyboard shortcuts (`[` / `]`, `PageUp` / `PageDown`), and interactive GUI stepper buttons without placing helper blocks.
  - **Blueprint Capture Modal (`BlueprintCaptureModalScreen`)** for blueprint naming, live voxel count verification, and server registration.
- **Tactical RTS Build Camera**: Elevated overhead perspective with zoom toggles (`H` key / `Ctrl` + Scroll), ceiling raycast clamping, 96-block placement raycasts, and semantic color-coded 3D wireframes:
  - 🚪 Doors: Bright Emerald Green
  - 🏮 Lighting: Warm Amber Gold
  - 📦 Utilities/Containers: Arcane Purple
  - 🏠 Roofing/Stairs: Soft Ice Blue
  - 🧱 Structural Walls/Ground: Vibrant Cyan-Magenta Grid
- **Multi-Modal Blueprint Rotation**: 90° clockwise blueprint rotation via dedicated `R` key in `BUILD` mode, direct Left-Click with scepter, or Command Hub GUI button.
- **Builder Block Phasing & Egress**: Builders phase through walls (`noClip = true`) during active construction and safely evacuate to exterior perimeter flank stations upon completion before collision physics are restored.
- **360° Perimeter Flank Settlement**: Builders distribute evenly across all 4 flanks of the completed build at uniform angular intervals ($360^\circ / N$), establishing an anchored guard ring with beacon beams and chime fanfare.
- **Free Survival Build Flight**: Unconstrained 3D flight while holding the scepter in `BUILD` mode in Survival, with an automatic flight cancel safeguard over water bodies.
- **Custom Blueprint Catalog Lifecycle**:
  - Pure custom catalog architecture: 100% player-authored structures stored natively in world saves (`data/minion_custom_blueprints.dat`).
  - Elevated confirmation modal (`Z = 400.0F` layering) for safe blueprint deletion without GUI bleed-through.

#### ⛏️ Structure Deconstruction & Area Mining
- **Dual Operational Modes**:
  - **`DIRECT` Mode**: Point-and-click deconstruction with 96-meter crosshair reach.
  - **`AREA` Mode**: Sequential corner selection (`Pos1` ➔ `Pos2`), fiery orange/amber ghost wireframes, real-time height adjustment (`Ctrl` + Scroll), and interactive confirmation modal (**`MiningConfirmModalScreen`**).
- **Top-to-Bottom Excavation Engine**: Scans volumes from top to bottom, skipping air and bedrock voxels with zero air-mining or pickaxe swinging desync.

#### 🌲 Survival Economy, Multi-Tier Supply Chains & Logistics
- **Autonomous Material Harvesting**: Builders quarry natural stone, deepslate, sandstone, sand, and clay when materials are missing from inventories.
- **Smart Supply Chain Transformations & Workstations**:
  - Multi-stage smelting (Cobblestone $\to$ Stone $\to$ Smooth Stone/Bricks; Sand $\to$ Glass; Clay $\to$ Bricks/Terracotta; Netherrack $\to$ Nether Bricks; Raw Ores $\to$ Metal Ingots/Blocks).
  - Gravel $\to$ Flint sifting (3 gravel $\to$ 1 flint with sifting SFX and particle bursts).
  - Autonomous world workstation discovery within 24m: Furnaces, Blast Furnaces, Smokers, Stonecutters, and Crafting Tables.
  - **Autonomous Workstation Self-Crafting**: If no furnace exists nearby and smelting is required, builders craft a Furnace from 8 cobblestone and deploy it on site.
- **Sustainable Agro-Forestry**: Builders fell natural trees, plant saplings, and apply bone meal via `Fertilizable.grow()` to rapidly accelerate timber growth for immediate harvesting.
- **Strict Build Safeguards**: Minions strictly refuse to harvest player-placed blocks, active structures, processed architectural blocks, or anything within 12 blocks of player beds, chests, and respawn anchors; includes 6-directional lava inspection.
- **Tool Self-Crafting & Supply Depots**: Builders autonomously synthesize wooden/stone pickaxes, axes, or shovels when tools break. Surplus items are stored in nearby containers or self-crafted 8-plank Double Chests.
- **Peer-to-Peer Block Sharing**: Allied minions pass required construction blocks across 24 meters via green energy transfer beams and pickup audio.
- **Squad Mob Hunting Contracts**: Builders contract nearby allied Warriors to hunt specific mobs for missing drops (wool, leather, bones, slime, prismarine) with 13 automated crafting transformations.
- **Material Bill of Materials (BOM) & Resource Estimator GUI (`BlueprintResourceEstimatorModalScreen`)**:
  - Real-time inventory delta calculating player inventory and all minion backpacks within 64 meters.
  - Category pill badges (🪨 Stone, 🪵 Timber, 🪟 Glass, 🌿 Organic).
  - Readiness meter with dynamic color coding and smart tags: `✔ Ready`, `⚒ Auto` (harvestable/craftable by minions), and `✕ Need` (manual gathering needed).

#### 💣 Tactical Ordnance & Special Items
- **Frost Grenade Projectile Stick (`frost_grenade_stick`)**: Throwable cryogenic stick freezing water to ice, lava to obsidian, and solid ground into snow blocks/powder snow while inflicting Slowness III and freezing ticks.
- **TNT Stick (`tnt_stick`)**: Tactical explosive stick with impact detonation.
- **Custom Construction Block (`construction_block`)**: Non-suffocating phase-through structural block for sapper bridging and chasm traversal.

#### 🖥️ Interactive User Interfaces & Visual Experience
- **Command Hub Screen (`CommandScepterScreen`, `V` key / Shift + Right-Click)**: Comprehensive tactical terminal with squad channel management (Alpha through Echo), mass role transfiguration, mode switches, blueprint catalog cards, and rotation buttons.
- **Minion Management Screen (`MinionScreen`)**: Right-click minion interface showing health, equipment slots, 9-slot backpack inventory, and 2-step confirmation destroy actions.
- **Clean In-Game GUI Overlays**: Custom transparent GUI backgrounds with disabled full-screen world blur (`applyBlur` disabled) across all modals, preserving complete battlefield situational awareness.
- **Overhead Crest Badges & Visual Identity**: Client-rendered overhead badges displaying minion role, health, squad channel, and activity state, complemented by squad-colored clothing highlights.

#### ⚙️ Cross-Version & Engine Invariants
- **Target Minecraft Version**: `1.21` (Compatible with `1.21.x` family).
- **Mod Loader**: Fabric Loader `>= 0.16.0`.
- **Dependencies**: Fabric API (`>= 0.100.4+1.21`).
- **Java Runtime**: Java 21+ (`sourceCompatibility = 21`).
- **Item Settings Reflection Shim**: `ModItems.createSettings` dynamically bridges differences between 1.21.0, 1.21.1, and 1.21.2+ mappings.
- **JVM VerifyError Prevention**: `isAlliedTeammate` decouples teammate logic from final vanilla methods across minor patch revisions.
- **Multi-Format Resource Packs**: Supports resource pack format ranges 34 to 48 with dual support for legacy models and modern 1.21.2+ item definitions.
- **Networking Protocol**: Custom C2S and S2C payloads registered under `com.example.network` using Fabric Networking API v1 (`MassRolePayload`, `RetreatPayload`, `AnchorConstructionPayload`, `SyncConstructionSessionPayload`, `RequestResourceEstimationPayload`, `SyncResourceEstimationPayload`, `UpdateTargetFilterPayload`, etc.).
- **License**: Visible Source & All Rights Reserved (ARR).
