# Changelog

All notable changes to **Sovereign Simulator: Arcane Strategy & Engineering** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.1] - 2026-10-11

### 🔍 3D Blueprint Clarity Overhaul, Tactical Target Filtering & Autonomous Construction

### Added

#### 🔍 Interactive 3D Architectural Blueprint Explorer GUI & Clarity Overhaul

- **Dedicated `BlueprintExplorerScreen`**: Added a dedicated GUI screen (`BlueprintExplorerScreen`) providing interactive 3D voxel structure previews, real-time catalog search, detailed spatial metrics, and single-click scepter arming.
- **True 3D Rotated Quad Projection**: Transformed the voxel engine from flat 2D stamps into a true 3D rotated quad renderer. For each visible face, all four spatial corners are projected onto the screen based on camera yaw and pitch, rendering perspective-accurate 3D quads via hardware vertex buffer shaders.
- **6-Directional Interior Block Occlusion Culling**: Implemented neighbor occlusion culling (`isOpaque(BlockState)`). Internal faces and completely enclosed blocks inside solid masonry, floors, and thick walls are culled before rendering, eliminating thousands of overlapping interior lines, black borders, and visual clutter while preserving transparent blocks (glass, doors, stairs, slabs, iron bars).
- **Directional Sunlight Shading ($N \cdot L$)**: High-contrast directional illumination from vector `(0.355, 0.862, -0.355)`. Top horizontal surfaces catch full overhead light ($1.18\times$), East/South faces catch angled sunlight ($0.8\times - 0.95\times$), North/West faces show architectural relief shadowing ($0.65\times - 0.75\times$), and bottom soffits sit in ambient occlusion ($0.45\times$).
- **Fullscreen / Maximize Viewport Mode (`[ ⛶ ]` / `[ 🗗 ]`)**: Toggle between split-pane mode and an expansive $404 \times 194$ px maximized viewport (hotkeys `Tab` or `F`), delivering nearly $3\times$ the viewing canvas for mega-structure inspection.
- **Architectural Floor Slicer / Cutaway Stepper (`[ ▾ ]`, `[ 🏢 All / L: X/Y ]`, `[ ▴ ]`)**: Step through floors vertically layer-by-layer to peel back roofs and inspect interior room layouts, furniture, and stairs.
- **2D Camera Panning & Expanded Zoom**: Right-click mouse drag camera panning (`panX`, `panY`), expanded mouse-wheel zoom range ($0.25\times$ to $8.0\times$), middle-click / `[ ↻ ]` view reset, and auto-spin toggle `[ ⏸ / ▶ ]` (or `Space` bar).
- **Ground Pedestal & Cardinal Compass**: Isometric ground grid pedestal for spatial scale grounding, alongside a floating HUD featuring a real-time rotating cardinal compass (`🧭 North / South / East / West`).
- **Botanical & Architectural Geometry Bounds (`getBlockBounds`)**: Scaled geometry bounds for slender flora and thin architectural blocks instead of rendering them as solid 1x1x1 cubes. Flowers, saplings, mushrooms, and crops render with slender profiles (`[-0.28, 0.28]` width, `[-0.5, 0.35]` height); torches, candles, and rods render slender vertical columns (`[-0.15, 0.15]`); slabs render as half-blocks (`[-0.5, 0.0]`); carpets render as thin floor layers (`[-0.5, -0.375]`); and glass panes render as thin vertical sheets.
- **Multi-Face Palette & Authentic Botanical Textures (`getBlockColor(state, face)`)**: Replaced generic stone fallbacks with face-specific colors:
  - **Botanical Flora**: Poppy scarlet bloom (`#E52521`), Dandelion gold bloom (`#FFD600`), Blue Orchid cyan (`#29B6F6`), Allium magenta (`#BA68C8`), Oxeye Daisy / Azure Bluet white petal (`#FFFFFF`), Tulips (orange `#FF6D00`, pink `#F48FB1`), Cornflower blue (`#3D5AFE`), Wither Rose obsidian (`#2B2B2B`), and Lilac petals (`#CE93D8`) with authentic stem greens on side and bottom faces.
  - **Agricultural Crops & Botanicals**: Golden wheat (`#D4AF37`), Carrot orange (`#FF6F00`), Potato gold (`#E0A93B`), Beetroot crimson (`#880E4F`), Melon green stripes, Carved Pumpkin glowing amber front face (`#FF8F00`), Cactus thorny green (`#2E7D32`), and Sugar Cane reeds (`#7CB342`).
  - **Tree Species & Multi-Face Bark/Rings**: Cut annual rings on top/bottom faces vs. authentic species bark on side faces for Oak, Birch (white bark `#EBEBEB`), Spruce, Jungle, Acacia, Dark Oak, Mangrove, Cherry, Bamboo, Crimson, and Warped stems.
  - **Crafting & Utility Workstations**: Bookshelves with colorful book spines on sides, Crafting Table grid top, Furnaces with glowing fire grates, and TNT with red dynamite casing and white primer top/bottom.
  - **Full 16-Color Dye Palette**: Authentic dye colors for wool, concrete, terracotta, carpets, beds, shulker boxes, and candles.
  - **Non-Opaque Culling Safety**: Ensured flowers, crops, saplings, mushrooms, torches, and slender voxels never cull adjacent solid blocks.
- **Real-Time Search Bar**: Interactive text field with instant substring filtering across blueprint titles, IDs, and descriptions, updating the catalog display dynamically as you type.
- **Zero Text Cut-Off Architecture**: Automatic text wrapping and ellipsis trimming ensuring pixel-perfect layout and readability across all Minecraft GUI scales (Normal, Large, Auto).
- **Direct Lifecycle Actions**: Integrated `[ ✔ Select ]` to arm the blueprint, `[ 📋 BOM ]` to open the Material Bill of Materials modal, and `[ 🗑 Del ]` to delete custom blueprints with safety confirmation.
- **Command Hub Integration**: Added a prominent `[ 🔍 3D View ]` launcher button directly inside the Command Scepter Hub (`BUILD` mode) next to the blueprint catalog header.

#### 🥇 Newest-First Custom Blueprint Ordering (Slide 1 Slot 1 Default)

- **Immediate Slide 1 Availability**: When players save a new custom blueprint, it is registered at index 0 (Slot 1 on Slide 1 / Page 0) by default in both `BlueprintRegistry` and world persistent state (`CustomBlueprintPersistentState`), eliminating the need to page through older blueprints.
- **Auto-Selection on Held Scepter**: Newly captured and saved blueprints are automatically armed on the commanding player's held Command Scepter on both client and server upon saving.

#### ⚔️ Frontline Warrior Attack Damage Boost (+4.0 Attack Damage)

- **Vanguard Strike Power (`WARRIOR_ATTACK_DAMAGE_BONUS = 4.0D`)**: Empowered frontline Warrior minions with a permanent `+4.0` Attack Damage bonus, raising bare-handed fist attacks to `9.0` base damage (vs. Sentinel/Builder `5.0`) and scaling all equipped melee weapons (`15.0` damage with Iron Sword; `16.0` with Diamond Sword).
- **Visceral Combat Particle & Audio Feedback**: Successful Warrior attacks trigger server-side critical strike particles (`ParticleTypes.CRIT`) and impactful knockback audio cues (`SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK`).
- **GUI & Tooltip Documentation**: Updated `MinionScreen` and role tooltips to accurately reflect the +4 Attack Damage frontline bonus for Warriors.

#### 🚩 Single Default Pathway, Zero Phantom Route Creation & Newest-First Route Ordering

- **Single Initial Default Route Invariant**: Players start with strictly **one** initial default pathway (**Route 1 🟡 Gold `#FFD700`**).
- **Zero Phantom Route Auto-Creation**: Querying, rendering, cycling, or opening modals for non-existent route IDs strictly returns `null` instead of auto-populating ghost channels in memory or world saves.
- **Newest-First Insertion Order**: Newly created custom routes are prepended at index 0 (Slide 1 Slot 1) across server storage (`PatrolRouteManager`), world persistence (`PatrolRoutePersistentState` via `LinkedHashMap`), and client trackers (`ClientPatrolRouteTracker`).
- **Dynamic Paginated Pathway Dashboard (`PATHWAY_PAGE_SIZE = 4`)**: Command Hub GUI manages routes across pages of 4 cards per page with Prev/Next buttons, custom route creation (`[ + New Route ]`), configuration modal (`PatrolRouteEditModalScreen`), and route deletion with automatic minion unbinding.

#### 🧱 Anti-Suffocation, Anti-Entombment & Safe Evacuation System

- **Surface Quarry vs. Subterranean Excavation Evacuation**: In `ConstructionManager.completeSession()`, mining (dismantle) sessions distinguish between open surface quarries (with sky access) and underground excavations. Open pit quarries evacuate minions to the surface rim surrounding the pit under open sky. Underground excavations safely station minions onto the cleared interior floor (`box.getMinY()`) or entrance anchor, completely preventing minions from being teleported into unmined perimeter rock walls.
- **Full-Column Safe Perimeter Scanning**: Replaced rigid -3..+2 downward-only scans in `findSafePerimeterGround()` with dynamic surface-level scanning (`world.getTopY`) down to foundation depth, evaluating candidate positions with strict `isSafeStandingPosition()` checks. Guaranteed safe footing with 2 vertical blocks of clear headroom, solid non-hazardous ground, and zero lava/fire hazards.
- **Continuous In-Wall Detection & Emergency Auto-Unstuck**: Added server-side `unstuckIfInSolidWall()` into `MinionEntity.tick()`. If a minion ever ends up with its head or feet inside solid blocks while not phasing (e.g. from physics nudges, falling gravel, block placements, or edge-case teleports), it immediately detects collision, finds the nearest safe 2-block open space within 8 blocks, and teleports them out with portal VFX and audio cues.
- **Egress & Landing Safety Verification**: `findExteriorExitPosition()` and `finishBuildingEgress()` validate exit vectors with `isSafeStandingPosition()` before settling minions, and `MinionBuildGoal` triggers `unstuckIfInSolidWall()` on touchdown upon deactivating arcane levitation.
- **Automated Safety Test Suite**: Added `MinionWallAndUndergroundSafetyTest.java` verifying safe standing position constraints, in-wall extraction simulations, hazardous floor rejection, and underground vs. surface stationing invariants.

#### 🏗️ Multi-Minion Task Stealing & Peer Handoff

- **Dynamic Absentee Task Stealing**: When multiple minions build a structure together, tasks claimed by absent or delayed workers are automatically detected. If a claimant is farther than 24 blocks away, missing from the server, or stalled past grace thresholds (`STEAL_GRACE_TICKS = 60L`, `STALLED_LEASE_TICKS = 200L`), nearby active builders steal the task without deadlocks.
- **Instant Peer Handoff & Memory Reset**: In `MinionBuildGoal`, each minion continuously monitors its task lease. The instant another minion completes or claims the task, the original claimant immediately forgets the task (`forgetLostTask`) as if completed and acquires the next available block task without idling or stalling.
- **World State Pre-Completion**: `ConstructionSession.claimNextTask()` checks if the target block already matches the blueprint state in the world, marking it completed immediately.
- **Lease-Safe Releases**: `ConstructionTask.releaseIfOwnedBy(UUID)` ensures former claimants cannot inadvertently release tasks currently reassigned to peer workers.

#### 🎯 Global Minion Target Filter Search & Named Presets

- **Real-Time Search Bar**: Added an interactive search box to `MinionTargetFilterModalScreen` allowing players to quickly filter candidates by mob name or ID path.
- **Named Presets**: Added `TargetFilterPresetManager` featuring 5 standard built-in presets:
  - `All Hostiles`: All hostiles enabled.
  - `Creeper Safe`: Disables Creepers to safeguard bases.
  - `Undead Only`: Targets Zombies, Skeletons, Phantoms, Drowned, Husks, Strays, Withers, and Zombified Piglins.
  - `Raiders`: Targets Pillagers, Vindicators, Evokers, Ravagers, and Witches.
  - `None (Passive)`: Disables on-sight aggression for all mobs.
- **Custom Preset Saving & JSON Persistence**: Commanders can save their current selection as a custom preset by title only, persisting to `config/sovereign-sim/target_filter_presets.json`. Custom presets can be cycled with one click or deleted safely.

#### 🛡️ Individual Minion Combat Policies

- **Per-Minion Combat Policy (`MinionCombatPolicy`)**: Added 4 distinct engagement policies configured per minion:
  - `GLOBAL`: Follows the master global target filter set in the Command Hub.
  - `PASSIVE`: Pacifist mode; never targets hostiles and never retaliates when harmed.
  - `DEFENSIVE`: Retaliates only when the minion or its leader/commander is directly attacked.
  - `CUSTOM`: Follows an individual per-minion target filter overriding the global list.
- **Minion Screen GUI Controls**: Added a combat policy toggle badge in `MinionScreen`'s header row, plus a `[ 🎯 Targets ]` button in the action panel to configure individual disabled targets when set to `CUSTOM`.
- **C2S Payload & NBT Persistence**: Registered `UpdateMinionCombatPolicyPayload` and serialized policies and individual disabled target lists to minion NBT.

#### 📖 Vanilla Recipe Book Integration & Advancements

- **Dynamic Recipe Book Blueprint Unlocking**: Native recipe advancements automatically unlock blueprints in the player's crafting table recipe book upon acquiring constituent ingredients in Survival mode (Amethyst Shards, Gold Ingots, Emeralds, Gunpowder, or Snowballs).
- **Dual Datapack Compatibility**: Populated both `advancement/` (Minecraft 1.21+ standard) and `advancements/` (legacy loader fallback) paths across `recipes/equipment/` and `recipes/misc/`.
- **Automated Validation Test Suite**: Added `RecipeAdvancementJsonValidationTest.java` verifying advancement syntax, root parent references, criteria, and reward IDs.

### Fixed

#### 🚶 Minion Waypoint Navigation & Pathing Anti-Jitter

- **Slab, Stair, Carpet, Snow & Path Block Clearance**: Fixed an issue where minions walking towards tactical ground waypoints jittered in place, spawned purple portal particles, made teleport sounds, and had forward momentum zeroed out.
- **Torso & Head Suffocation Safeguard**: Refined `unstuckIfInSolidWall()` to evaluate the minion's torso (`getY() + 0.6D`) and head (`getEyeY()`) using `shouldSuffocate()` and `isFullCube()`, excluding walkable ground surfaces beneath feet (slabs, stairs, carpets, snow layers, path blocks).
- **Teleport Cooldown & Anti-Looping**: Added a 20-tick safety cooldown to `unstuckCooldownTicks` on successful emergency extractions, completely eliminating stutter and teleportation loops.
- **Arcane Levitation Traversal Stability**: Ensured obstacle-climbing maneuvers maintain at least 4 ticks of lift before touchdown, preventing rapid ground chatter.

#### 🏗️ Blueprint Placement & Selection Persistence

- **Persistent Blueprint Selection in BUILD Mode**: Fixed an issue where placing down a blueprint or right-clicking into the air automatically cycled to the next blueprint in the catalog. Blueprint selection now stays firmly locked on the commander's chosen blueprint across repeated placements, quick taps, and cooldowns. Blueprints only change when explicitly selected in the Command Hub GUI (`V` key / Shift + Right-Click).

#### 📜 Minecraft 1.21 Recipe Ingredient Format

- **Strict Object Ingredients**: Fixed `Parsing error loading recipe` errors where Minecraft 1.21 DataFixerUpper/Codec rejected raw string identifiers in recipe `key` maps and `ingredients` arrays. Updated all recipe files (`command_scepter`, `frost_grenade_stick`, `tnt_stick`, `minion_spawn_egg`) across both `recipe/` and `recipes/` to use strict `{ "item": ... }` objects.
- **Automated Recipe Codec Test**: Expanded `RecipeAdvancementJsonValidationTest` to validate all recipe files and enforce 1.21 object ingredient standards.

#### 🌊 Shallow Water Build Flight Tolerance & Water Safeguards

- **Water 5 Blocks High Flight Allowance**: Updated `CommandScepterItem.isPlayerOverWater()` to measure the vertical column depth of water bodies beneath the player. If the water column is 5 blocks high or less ($\le 5$ blocks deep of water above solid ground, such as creeks, shallow ponds, canals, or streams), the player is permitted to fly over it freely in `BUILD` mode without triggering build cancellation or losing flight. Only deep water bodies ($> 5$ blocks deep) trigger the water flight safeguard, cancelling `BUILD` mode only (reverting held scepter to `FOLLOW` and revoking flight) while allowing active minion construction sessions to continue building uninterrupted.
- **Water Flight Edge Cases & Safe Descent Safeguard**:
  - **Bridges, Slabs & Glass Platforms**: Downward surface scanning checks `!state.getCollisionShape().isEmpty()`, ensuring players flying over bridges, glass walkways, slabs, stairs, or docks above deep water are recognized as standing over solid architecture and are not erroneously cancelled. Lily pads on open water are gracefully traversed down to evaluate the underlying depth.
  - **Head & Toe Dipping Water Detection**: Evaluates head (`player.getEyePos()`), feet, and sub-feet water states, guaranteeing that dipping toes or swimming through waterfalls accurately locks onto the true water column surface without depth measurement anomalies.
  - **Descent Fall Damage Immunity**: When `BUILD` mode and flight are cancelled over water, `fallDistance = 0.0F` is maintained via `ACTIVE_SERVER_BUILD_FLIERS` until the player safely touches ground or splashes into water (`isOnGround() || isTouchingWater()`), eliminating lethal fall damage from high-altitude cancellation.

#### 🖱️ 3D Blueprint Explorer Mouse-Click Button Priority & Viewport Interaction

- **Button Click Delegation Before Viewport Drag**: Fixed an issue where clicking toolbar buttons (`[ ⛶ / 🗗 ]` Maximize, `[ ⏸ / ▶ ]` Spin, `[ ↻ ]` Reset, `[ ▾ ]` Slice Down, `[ 🏢 All ]` Slice Reset, `[ ▴ ]` Slice Up) with the mouse failed to trigger actions because `BlueprintExplorerScreen.mouseClicked()` checked viewport coordinates first and consumed left-clicks for camera dragging. Calling `super.mouseClicked(mouseX, mouseY, button)` before viewport coordinates ensures all buttons handle mouse clicks cleanly while preserving smooth orbit dragging and right-click panning.

#### 🌸 Botanical Flora, Crops & Multi-Face Block Texturing

- **Elimination of Stone Fallback on Flora**: Fixed an issue where flowers, saplings, mushrooms, and crops appeared as dull gray stone cubes in the 3D Blueprint Explorer. Implemented dedicated botanical bounds and multi-face bloom/stem palettes for all Minecraft flowers and crops, wood species cut rings vs. bark, utility workstations, and the 16-color dye palette.

#### 🏷️ Stale `[HOLD]` Badge on Following Minions

- **Following Always Overrides Hold State**: Fixed minions that were following the commander (or escorting/patrolling) still displaying the `[HOLD]` overhead label because a leftover sit pose or guard anchor lingered. `MinionEntity.isHoldingPosition()` now returns `false` whenever a minion is selected, has a leader, or is on a patrol route, and `setSelected(true)` clears any stale guard anchor and sitting pose.

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
- **Survival Crafting Progression**:
  - Complete data-driven crafting recipes for the Sovereign Command Scepter, Minion Spawn Egg, TNT Stick, and Frost Grenade Stick.
  - Dual directory compatibility across `recipe/` (1.21+ standard) and `recipes/` (legacy loader fallback) namespaces.

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
