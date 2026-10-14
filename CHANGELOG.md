# Changelog

All notable changes to **Sovereign Simulator: Arcane Strategy & Engineering** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1.1] - 2026-10-14

### 🔨 Area Fill / Volumetric Construction, Water Logistics & Builder Minion Survival Crafting

### Added

#### 💧 Water & Fluid Source Block Construction Logistics
- **Water Bucket Material Mapping (`BlueprintBlock.getRequiredItem`, `StructureBlueprint.Builder.build`)**:
  - Blueprint water source blocks (`Blocks.WATER`) now properly resolve to `Items.WATER_BUCKET` instead of returning `Items.AIR`.
  - Lava source blocks (`Blocks.LAVA`) resolve to `Items.LAVA_BUCKET`.
  - Blueprints, bills of materials (BOM), and material checks never request or display `Items.AIR` when building structures with water features (e.g. fountains, ponds, aqueducts).
- **Flowing Fluid Filtering (`StructureBlueprint.captureBlocks`)**:
  - Blueprint capture strictly distinguishes between fluid source blocks (`state.getFluidState().isStill()`) and flowing fluids (`!state.getFluidState().isStill()`).
  - Flowing water/lava is filtered out of captured blueprints, ensuring only true water source blocks are captured. When placed, Minecraft's native fluid physics naturally cascade water down, preventing bloated bucket requirements and unwanted source flooding.
- **Survival Water Bucket Lifecycle & SFX (`MinionBuildGoal`)**:
  - In Survival mode, builder minions consume 1 `Items.WATER_BUCKET` and immediately return an empty `Items.BUCKET` into their inventory (or drop it at their feet if full).
  - In Creative mode, minions place fluid source blocks freely without requiring item consumption.
  - Realistic bucket emptying sound effects (`SoundEvents.ITEM_BUCKET_EMPTY` / `SoundEvents.ITEM_BUCKET_EMPTY_LAVA`) play upon liquid placement.
- **Autonomous Water Gathering & In-Inventory Bucket Crafting (`MinionHarvestingHelper.gatherWaterBucket`)**:
  - When a builder minion needs a water bucket in Survival mode, it can autonomously scoop nearby natural water sources within 16 blocks using an empty bucket.
  - If the minion lacks an empty bucket but carries at least 3 iron ingots, it autonomously crafts a bucket on demand and scoops the water source.
- **Structure & Blueprint Water Source Protection (`MinionHarvestingHelper.isProtectedWaterSource`, `gatherWaterBucket`)**:
  - **Fixed Water Disappearance Bug**: Resolved an issue where builder minions carrying empty buckets would scoop up and delete water source blocks (`Blocks.WATER`) that were just placed into a structure (e.g. fountains, ponds) when searching for water for subsequent water placement tasks, causing placed water to vanish immediately after placement.
  - **Comprehensive Session & Bounding Box Safeguards**: Minions are strictly barred from scooping water located within any active construction session's task coordinates or bounding box (`session.containsBlockPos`, `ConstructionManager.isPositionInAnyActiveSession`).
  - **Architectural Material Contact Protection**: Water directly touching processed architectural materials (stone bricks, quartz, prismarine, purpur, copper, stairs, slabs, walls, polished/smooth stone) is classified as part of a structure and never harvested.
  - **Immediate Fluid Ticking & Fluid Splash SFX (`MinionBuildGoal`)**: Placing water or lava now schedules immediate fluid ticks via `scheduleFluidTick` and emits realistic fluid splash particles (`ParticleTypes.SPLASH` / `ParticleTypes.LAVA`).
  - **Precise Pre-Placed Fluid Verification (`ConstructionSession.claimNextTask`)**: Pre-completion checks now verify `isStill()` for fluids, preventing minions from walking over to re-place valid water sources already in the build.
- **Topological Fluid Containment Ordering (`BlueprintBlock.compareTo`, `isFluid`)**:
  - Enforced a strict containment ordering guard on identical vertical layers: **all solid blocks (basin floors, perimeter retaining walls, rims) are guaranteed to be placed BEFORE any fluid source blocks (water/lava)** on that layer.
  - Prevents premature water pouring where liquid cascades into unbuilt open air or washes away neighboring redstone, torches, and scaffolding.
- **Vertical Reach for Elevated Fluid Harvesting (`MinionHarvestingHelper.gatherWaterBucket`)**:
  - Symmetrically expanded the vertical harvesting search radius from 6 to the full 16 blocks (`HARVEST_SEARCH_RADIUS`).
  - Allows builder minions constructing high aqueducts, watchtowers, or rooftop fountains to safely reach ground-level rivers, ponds, and aquifers below without stalling water logistics.

#### 📦 Nearby Chest & Container-Inclusive Readiness Percentage & BOM Logistics
- **Nearby Container Inventory Aggregation (`MinionHarvestingHelper.countItemsInNearbyContainers`)**:
  - Construction readiness calculations now fully evaluate items stored in nearby container block entities (chests, barrels, trapped chests, shulker boxes, dispensers, droppers) within 32 blocks of the player or session anchor.
  - Recognizes that while the player's personal bag and minion thralls may be temporarily empty, builder minions autonomously scavenge required materials from nearby base chests/depots to complete construction tasks without player intervention.
  - Double chests, single chests, barrels, and shulkers are queried directly through loaded chunk block entities with zero redundant slot iteration.
- **Resource Estimator S2C Networking & Payload (`ResourceEstimateEntry`, `SyncResourceEstimationPayload`, `ModNetworking`)**:
  - `ResourceEstimateEntry` now tracks `chestCount` alongside `playerCount` and `minionCount`.
  - `totalAvailable()` sums `playerCount + minionCount + chestCount`, enabling blueprints to reach 100% readiness when materials are located entirely within nearby chests.
  - `SyncResourceEstimationPayload` streams `nearbyContainersCount` to the client so commanders know exactly how many chests are contributing to the readiness score.
- **Material Bill of Materials (BOM) GUI Enhancements (`BlueprintResourceEstimatorModalScreen`)**:
  - Added dedicated **`Chests`** quantity column between `Minions` and `Status / Delta`.
  - Summary progress bar now displays both nearby contributing minions and container counts: `§fReadiness: §lX% §7(A/Bb) §8| §eN Minions §8| §6M Chests`.
  - Item row hover tooltips provide detailed breakdowns: personal inventory, minion backpacks, and nearby containers across $M$ containers within 32m.
- **Command Scepter & Placement Action Bar Delta (`CommandScepterItem.executeBuildPlacement`, `CommandScepterScreen`)**:
  - When anchoring blueprints in Survival mode, the action bar BOM notification factors in container stockpiles: `§6📋 BOM Delta: 100% Ready (All materials in bags or nearby chests)`.
  - Blueprint catalog tooltips in Command Hub GUI display `Nearby Chests Contributing: M`.

#### ⛏️ Mining & Dismantle Fluid Mechanics & Water Source Removal
- **Liquid Block Tool Immunity (`ConstructionSession`, `MinionBuildGoal.executeDismantleWork`)**:
  - In both Survival and Creative modes, minions mining or dismantling structures strictly ignore water, lava, and any fluids (`isLiquid()` / `FluidBlock`).
  - Minions will never swing tools at water/lava or attempt to clear fluids with pickaxes.
- **Builder Minion Water & Fluid Source Removal (`MinionBuildGoal.removeFluidSource`)**:
  - Builder minions dismantling or clearing water/lava features (e.g. fountains, canals, moats) can actively remove true fluid source blocks (`isStill()`):
    - **Creative Mode**: Instantly cleared to `Blocks.AIR` with bucket scoop SFX and splash particles.
    - **Survival Mode**: Scooped with an empty `Items.BUCKET` and stored as `Items.WATER_BUCKET` in the minion's inventory. If lacking a bucket, minions autonomously craft one from 3 iron ingots, scavenge one from nearby containers within 32 blocks, or displace the source block with solid blocks (cobblestone, dirt, sponge) before breaking.
  - Flowing fluids (`!isStill()`) are never scooped or mined; they naturally recede as the controlling source blocks are removed.
- **Mining Area Blueprint Filtration (`StructureBlueprint.createAreaBlueprint`)**:
  - Volumetric area mining blueprints automatically exclude all liquid and fluid blocks during creation, ensuring task queues only contain real destructible blocks.
- **Fluid Clearance Non-Blocking (`ConstructionSession.isDismantleTaskReady`, `hasRemainingBlocksInWorld`)**:
  - Water and lava above or around blocks are treated as cleared obstacles, preventing lower layers from stalling if water flows over them.
  - Natural seeping fluids or ocean aquifers no longer prevent dismantle sessions from reaching 100% completion.

#### 🛡️ Safe Solid Ground Positioning & Aquatic Builder Safeguards
- **Aquatic Construction Drowning Safeguard (`MinionEntity.damage`)**:
  - Builder minions actively engaged in construction tasks or arcane levitation are granted drowning damage immunity (`DamageTypes.DROWN`) and replenish air supply.
  - Guarantees minions building deep fountains, harbor foundations, underwater tunnels, or flooded moats will never suffocate underwater during work.
- **Autonomous Safe Solid Ground Recovery (`MinionEntity.tickGentleDescent`, `isSafeStandingPosition`)**:
  - When minions finish construction, dismantling, or mining tasks while airborne or surrounded by water, lava, or hazardous fluids, they no longer descend into liquids or sink to ocean/river bottoms.
  - `tickGentleDescent` automatically identifies the nearest verified dry, solid, non-liquid ground within 32 blocks (or near their commanding player/master) and smoothly glides horizontally across the liquid surface before touching down.
  - `isSafeStandingPosition` strictly validates that neither the ground beneath nor the minion's foot/head space contains fluids (water, lava) or hazard blocks (cacti, fire, sweet berries, magma), guaranteeing minions always disengage onto firm dry terrain.
  - Structure egress (`finishBuildingEgress`) and task goal termination (`MinionBuildGoal.stop`) preserve active arcane levitation if over fluids until the minion is safely centered above solid ground.

#### ✨ Area Fill Mode Particle Cleanup
- **Blueprint Perimeter Particle Suppression (`CommandScepterItem.inventoryTick`)**:
  - Suppressed idle ground perimeter bounding particles when holding the scepter in `BUILD (AREA)` mode, preventing blueprint outlines from cluttering the screen while using Area Fill.
- **Corner Selection Particle Removal (`CommandScepterItem.handleBuildPos1`, `handleBuildPos2`)**:
  - Removed particle bursts when selecting `Pos1` and `Pos2` in Area Fill mode for a clean, minimal visual experience.
- **Active Area Fill Hologram Particle Suppression (`ConstructionManager.renderHologramParticles`)**:
  - Hologram aura and corner particles are suppressed for active Area Fill sessions, leaving only the clean Cyan/Aqua wireframe render.

### Added

#### ◫ Area Fill / Volumetric Construction Mode (`BUILD` Mode Duality)

- **Dual Operational Sub-Modes (`BuildingMode.BLUEPRINT` vs `BuildingMode.AREA`)**:
  - `BUILD` mode now features two dedicated construction modes designed with **perfect architectural symmetry to `MINE` mode's `DIRECT` vs `AREA` duality**:
    1. **`BLUEPRINT` Sub-Mode**: Standard structure placement from custom captured blueprints or catalog presets.
    2. **`AREA` Sub-Mode**: Direct volumetric construction and area filling with any chosen block in the game.
  - Command Scepters track the active sub-mode via the `BUILDING_MODE` data component, cycling seamlessly via Command Hub GUI controls or contextual hotkeys.
- **Sequential In-World Corner Capture (`Pos1` and `Pos2`)**:
  - Symmetrical to `MINE` and `DESIGN` modes, commanders define a 3D building volume using sequential left-clicks with the Command Scepter:
    - **1st Left-Click (`Pos1`)**: Anchors Corner 1 directly on top of the targeted block surface, bursts cyan sparkle particles, rings an amethyst chime, and alerts the action bar (`✦ Build Pos1: [X, Y, Z]`).
    - **Candidate Preview**: Aiming across the world renders an expanding candidate bounding wireframe and live ghost grid to the crosshair.
    - **2nd Left-Click (`Pos2`)**: Anchors Corner 2, completing the base footprint and locking in the 3D construction volume.
    - **Subsequent Left-Clicks**: Initiates a fresh selection cycle (`Pos1` updated, `Pos2` cleared).
    - **Shift + Left-Click**: Instantly resets both `Pos1` and `Pos2` with a deep bass note.
- **In-World Real-Time Height Adjustment**:
  - Commanders can expand or contract the fill height in real time by holding **`Ctrl` + Mouse Scroll** (or using **`[`** / **`]`** or **`PageUp`** / **`PageDown`** keys) without placing dirt towers or temporary scaffolding.
  - Hold **`Shift`** for fast $\pm 5$ stepping, with real-time dimensions displayed in the action bar.
- **3D Cyan / Dark Aqua Hologram & Ghost Grid (`BlueprintHologramRenderer`)**:
  - Enclosed construction volumes illuminate in the world with a vibrant Cyan / Dark Aqua (`#00E5FF` / `#00A3CC`) bounding wireframe and internal voxel ghost grid, distinguishing build areas from amber mining quarries and magenta design captures.
- **Dedicated Area Fill Confirmation Modal (`BuildAreaConfirmModalScreen`)**:
  - Right-clicking with `Pos1` and `Pos2` selected immediately opens the glassmorphic Area Fill Confirmation dialog:
    - **Dimension & Volume Inspection**: Displays clear width, height, length (`W × H × L`), and total block volume.
    - **Interactive Height Steppers**: Quick `[-5]`, `[-1]`, `[+1]`, and `[+5]` buttons to fine-tune volume height directly from the GUI.
    - **Block Selector & Visual Slot Preview**: Renders the selected fill block with an interactive **`[ 🔍 Change ]`** button opening `BlockPickerModalScreen` to choose any block in Minecraft.
    - **Action Controls**: One-click **`[ ✔ Fill Area ]`** to authorize construction, **`[ ⌫ Reset ]`** to clear corners, and **`[ ✖ Cancel ]`** to dismiss.
- **Bottom-to-Top Layer-by-Layer Execution (`StructureBlueprint.createAreaFillBlueprint`)**:
  - Dynamically synthesizes an in-memory area blueprint ordered deterministically bottom-up layer-by-layer ($Y = 0 \to H-1$, $Z$, $X$), ensuring builder minions erect stable foundations before working upwards.
- **Autonomous Minion Volumetric Construction (`ConstructionManager.startAreaBuildSession`)**:
  - Authoritative server session mobilizes nearby Builder minions within 112 blocks.
  - Builders activate 3D Arcane Levitation and block phasing (`noClip = true`), query materials, craft necessary blocks on demand, and place every block in the volume until 100% completed.
- **Command Hub GUI (`CommandScepterScreen`) Integration**:
  - Features dedicated `[ 🏛 Blue ]` vs `[ ◫ Area ]` sub-mode toggle buttons in the `BUILD` mode control bar.
  - Displays live area volume dimensions and provides a quick **`[ ✦ Fill ]`** modal trigger.
- **Active Area Build Hologram Persistence (`BlueprintHologramRenderer`)**:
  - The in-world cyan/aqua wireframe and voxel ghost grid now persist dynamically in the world throughout the entire duration of active area build sessions until all blocks are placed, giving commanders full visual feedback on minion progress matching area mining quarries.
- **Keyboard Height Adjustment Parity (`ExampleModClient`)**:
  - Full keyboard height stepping with **`[`** / **`]`** and **`PageUp`** / **`PageDown`** (plus **`Shift`** for $\pm 5$) is now supported in `BUILD (AREA)` mode alongside `Ctrl + Scroll`.
- **Contextual Keybind Parity (`R` Key)**:
  - In `BUILD (AREA)` mode, the `R` key triggers minion retreat and emergency citadel recall (matching `MINE (AREA)` and `DESIGN`), while 90° blueprint rotation is cleanly scoped to `BUILD (BLUEPRINT)` mode.
- **World Height Limits & Boundary Safety (`ModNetworking`)**:
  - Authoritative server validation verifies that bounding volumes stay strictly within world height limits (`world.getBottomY()` to `world.getTopY() - 1`) for both area building and mining, preventing out-of-bounds tasks.
- **Modal Corner State Safety (`BuildAreaConfirmModalScreen`)**:
  - Dynamically hides the block picker button when corners are incomplete, ensuring a clean and error-free modal presentation.
- **Strict Sub-Mode Isolation & Server Synchronization (`UpdateScepterPayload`, `ModNetworking`, `CommandScepterItem`)**:
  - Completely isolated Area Fill mode from Blueprint mode: right-clicking in Area mode strictly opens the Area Fill confirmation modal and will never trigger blueprint placement or signal minions to build previous blueprints.
  - Sub-modes (`buildingMode` and `miningMode`) are serialized and synchronized over the network via `UpdateScepterPayload`, ensuring server and client states stay 100% in sync and gating anchor construction packets exclusively to `BLUEPRINT` and `DIRECT` sub-modes.

- **On-Demand Block-to-Block Crafting (`MinionHarvestingHelper.tryCraftBlockFromInventory`)**:
  - Builder minions in survival mode can now craft architectural, masonry, utility, and decorative blocks on demand directly from base materials in their own 9-slot inventory (functioning like a player using a crafting table).
  - Evaluated immediately before container scavenging and world harvesting, preventing minions from stalling or felling trees when holding base materials (e.g., crafting planks into stairs or logs into planks).
- **Wood Type Fidelity & Proportional Crafting**:
  - **Full Species Fidelity (`resolvePlanksForWoodenItem`, `resolveLogForWoodenItem`)**: Preserves exact wood species across transformations (e.g., birch log $\to$ birch planks $\to$ birch stairs/slabs/doors/fences/gates/buttons/signs).
  - **Proportional Crafting**: When minions hold fewer than the standard full recipe batch (e.g. holding 1–5 planks when a standard recipe requires 6), minions craft derivative blocks proportionally (1 plank $\to$ 1 stair/slab/button) so construction never stalls on partial stacks.
  - **Automatic Stick Generation (`ensureSticks`)**: Automatically converts available planks or logs into sticks when crafting fences, gates, signs, ladders, and tools.
  - **In-Inventory Log Conversion**: Automatically converts raw logs into matching planks before attempting to fell wild trees or perform agro-forestry.
- **Architectural & Masonry Transformations**:
  - **Blackstone Architecture (`craftBlackstoneDerivative`)**: Blackstone $\to$ Polished Blackstone $\to$ Polished Blackstone Bricks $\to$ Stairs, Slabs, Walls, Pressure Plates, and Buttons (utilizing stonecutters for 1:1 efficiency).
  - **Tuff Masonry (`craftTuffDerivative`)**: Tuff $\to$ Polished Tuff $\to$ Tuff Bricks $\to$ Chiseled Tuff Bricks $\to$ Stairs, Slabs, and Walls.
  - **End Stone Architecture (`craftEndStoneDerivative`)**: End Stone $\to$ End Stone Bricks $\to$ Stairs, Slabs, and Walls.
  - **Purpur Synthetics (`craftPurpurDerivative`)**: Popped Chorus Fruit $\to$ Purpur Block $\to$ Stairs, Slabs, and Pillars.
  - **Prismarine Architecture (`craftPrismarineDerivative`)**: Prismarine Shards $\to$ Prismarine / Dark Prismarine / Prismarine Bricks $\to$ Stairs, Slabs, and Walls.
- **Container Ingredient Scavenging Fallback (`MinionBuildGoal.scavengeNearbyContainers`)**:
  - When nearby containers (chests, barrels, shulker boxes within 32 blocks) do not hold the finished target block, minions scan for base crafting ingredients using `MinionHarvestingHelper.getCraftingIngredients(requiredItem)`.
  - Minions withdraw candidate ingredients (e.g., planks, logs, cobblestone, clay) from containers, craft the target block on site, and consume it to place the blueprint block.
- **Peer-to-Peer Cooperative Allied Crafting (`MinionLogisticsHelper.requestItemFromAllies`)**:
  - Allied minions can now craft requested blocks on demand from their own inventory or share base crafting ingredients with requesting minions to assemble the target blocks cooperatively.
- **Agro-Forestry Spaced-Out Sapling Planting (`hasNearbySaplingOrTrunk`, `findPlantingPosition`)**:
  - Builder minions now intelligently space out planted saplings into spacious orchards instead of planting them directly adjacent to each other in crowded clusters.
  - **Multi-Pass Spacing Strategy**: Evaluates generous orchard spacing (radius 4 blocks) before falling back to standard tree spacing (radius 3 blocks).
  - **Trunk & Sapling Avoidance**: Actively scans for existing saplings (`BlockTags.SAPLINGS`) and tree trunks/logs (`BlockTags.LOGS`) within the horizontal radius, guaranteeing at least 3–4 blocks of clear distance between trees so canopies have room to grow naturally.
  - **Expanded Agro-Forestry Search Area**: Expands planting search radius to 16 blocks across valid soil blocks (`BlockTags.DIRT`, moss, farmland, mud) with at least 6 blocks of vertical air clearance above each planting site.
- **Smart Autonomous Supply Depot Deployment & Container Chest Scavenging (`findChestInNearbyContainers`, `withdrawChestItemFromContainer`)**:
  - Builder minions now inspect existing nearby containers within 32 blocks (chests, barrels, trapped chests) for stored chest items before crafting new ones when offloading full backpacks.
  - **Anti-Redundancy Priority**: If nearby storage is full, the builder scavenges a stored chest item out of an existing container and places it down (merging into a Double Chest if adjacent to a single chest), preserving logs and planks.
  - **Crafting Fallback**: Only if no chest items are stored in nearby containers and none are held in the minion's inventory will the builder craft a new chest from 8 planks or 2 logs.
- **Natural Tree Log Validation, Edge Case Hardening & Blueprint Protection (`isNaturalTreeLog`, `fellLog`, `deployAutonomousChest`, `isPositionInAnyActiveSession`)**:
  - Fixed builder minions felling or stealing log blocks from active blueprints or nearby player builds when harvesting timber in Survival mode.
  - **Strict Living Tree Criteria (`isNaturalTreeLog`)**: Logs are strictly validated as part of a real living tree before felling. A valid tree log must be contiguous to a trunk rooted in natural soil (`BlockTags.DIRT`, moss, mud, mangrove roots, nylium, netherrack), have canopy foliage (`BlockTags.LEAVES`, wart blocks, shroomlight) near or above the trunk column, and cannot be attached to processed architectural building blocks.
  - **Structure Contact & Base Protection**: Any block (stone, cobblestone, log, deepslate, dirt) directly adjacent to processed building materials or player fixtures (planks, bricks, stairs, slabs, glass, terracotta, iron bars, chains, pressure plates, buttons, levers, signs, banners) is recognized as part of a structure and immune from quarrying or felling.
  - **Base-First Contiguous Whole-Trunk Felling (`fellLog`)**: When felling a verified natural tree, minions first trace downwards through contiguous logs to the bottom-most base log, then fell the entire trunk upwards (up to 24 blocks), eliminating floating tree remnants and half-felled stumps in one clean pass.
  - **Agro-Forestry Full Maturity Loop & Species Matching (`getSaplingFromLog`, `getLogFromSapling`)**: When bone-mealing saplings in agro-forestry, minions loop fertilizer application up to 3 times to guarantee saplings sprout into full leafy trees. Felling natural trees replenishes a matching sapling for perpetual replanting, and fallback yields match the planted species (birch, spruce, acacia, jungle, dark oak, cherry, mangrove, crimson/warped stem).
  - **Autonomous Supply Depot Headroom & Trapped Chests (`deployAutonomousChest`)**: When deploying autonomous chests for full backpack offloading, minions verify clear headroom (`!isSolidBlock` above candidate) so chests never spawn blocked under ceilings, and accurately place Trapped Chest blocks if a trapped chest was scavenged.
  - **Safe Overflow Item Drops (`giveOrDropItem`)**: When felling logs or quarrying stone with a completely full backpack, surplus items are safely spawned in the world at the minion's feet rather than being voided.
  - **Multi-Session Task & Volume Protection (`isPositionInAnyActiveSession`)**: Blocks at any coordinate designated by an active blueprint task or volume across any session on the server are strictly protected from harvesting.
  - **Zero Build Cannibalization**: If no natural living trees exist in the area, builders strictly rely on agro-forestry (planting and bone-mealing saplings into full trees) rather than taking logs from buildings.

---

## [1.1.0] - 2026-10-13

### 🎨 Blueprint Palette Remapping, Rotation Cleanup, & Architecture Overhaul

### Added

#### 🎨 3D Blueprint Palette Remapping & Save Choice Dialog (`BlueprintExplorerScreen` & `BlueprintSaveChoiceModalScreen`)

- **Direct 3D Viewer Palette Modification**: Players can now modify the block palette of any blueprint directly from within the 3D Architectural Blueprint Explorer (`BlueprintExplorerScreen`) via a new `[ 🎨 Remap ]` action button.
- **Save Choice Dialog (`BlueprintSaveChoiceModalScreen`)**:
  - Modal dialog allowing players to choose between **[ 💾 Overwrite ]** (updates existing blueprint in-place) or **[ 📋 Save as Copy ]** (generates a new unique blueprint identifier and registers a new custom blueprint).
  - Configurable display name text field, prefilled with the blueprint name.
  - Complete validation ensuring names are not empty and preventing saving blueprints where all blocks have been erased with Air (`getBlockCount() > 0`).
  - Seamless networking integration: dispatches `CreateCustomBlueprintPayload`, updates the client registry immediately, and automatically arms the new blueprint onto the player's active Command Scepter.
- **Directional Property Preservation (`StructureBlueprint.createRemapped`)**:
  - Automatically transfers directional properties (`facing`, `half`, `shape`, `axis`, `waterlogged`, etc.) from source states to replacement blocks.
  - Dynamically re-normalizes coordinate offsets to `(0, 0, 0)` if outer boundary blocks were erased.

#### ✏️ Custom Blueprint Direct Renaming in 3D Viewer (`BlueprintExplorerScreen`)

- **Direct 3D Viewer Renaming**: Players can now rename any custom blueprint directly from within the 3D Architectural Blueprint Explorer (`BlueprintExplorerScreen`) via a dedicated **`[ ✏ Rename ]`** button or keyboard shortcut **`[R]`** without having to recapture or clone the structure.
- **Glassmorphic Rename Modal Dialog**:
  - Centered modal dialog (`240x104`) with dark arcane styling (`#141E2B`), cyan glowing border (`#00E5FF`), and prefilled `TextFieldWidget` (up to 64 characters).
  - Spacious action buttons: **`[ ✔ Save ]`** (`Enter`) and **`[ Cancel ]`** (`Escape`) with instant validation preventing empty or blank names.
  - Crisp audio feedback: plays resonant chime (`BLOCK_AMETHYST_BLOCK_CHIME`) upon saving and warning bass note (`BLOCK_NOTE_BLOCK_BASS`) on blank input errors.
- **Immediate Network & World Synchronization**:
  - Dispatches `CreateCustomBlueprintPayload` to the server, updates client-side `BlueprintRegistry`, and refreshes catalog cards in real time.
  - Held Command Scepters armed with the previous blueprint name automatically synchronize to the new name on both client and server.
- **Catalog Order Preservation**:
  - Updating or renaming an existing custom blueprint preserves its exact slot position in `customBlueprints` and `BlueprintRegistry` rather than bumping it to index 0, keeping the player's browsing context intact.

#### 🥷 Selected Minion Sneaking & Tactical Overhead Stealth (`MinionEntity`, `MinionEntityRenderer`, `MinionOverheadBadgeFeatureRenderer`)

- **Commander Synchronized Sneaking**:
  - Selected minions (`isSelected() == true`) now automatically sneak and crouch whenever their commanding player sneaks (`owner.isSneaking() == true`).
  - Escort minions following a designated squad leader dynamically sneak when their leader is sneaking, enabling synchronized squad-wide stealth maneuvers.
  - Zero-latency client prediction updates sneaking state instantly on the local client when the commander holds Shift, while server-side ticks ensure authoritative synchronization over the network (`Entity.FLAGS` and `EntityPose.CROUCHING`).
- **Complete Overhead Tag & Name Tag Suppression**:
  - Overrode `hasLabel` and `renderLabelIfPresent` in `MinionEntityRenderer` to completely hide default and custom name tags while a minion is sneaking or in crouching pose.
  - Updated `MinionOverheadBadgeFeatureRenderer` to abort rendering when sneaking, completely hiding all overhead badges (squad banner, role crest, health hearts, and `[HOLD]`/`[Stationed]` status indicators).
- **Stealth Creep Movement Pacing**:
  - Updated `MinionFormationFollowGoal` and `MinionFollowLeaderGoal` to adjust movement speed to stealth pace (`0.65D`) when sneaking, matching the commander's crouch-walking speed and preventing minions from overrunning or colliding with the player. If minions fall behind by > 8 blocks, they safely accelerate to standard march speed (`1.15D`) while remaining crouched.
- **Kinematic & Activity Exemptions**:
  - Minions that are sitting, actively constructing buildings (`isActivelyBuilding()`), or navigating in 3D Arcane Levitation are cleanly exempted from sneaking to prevent posture conflicts and maintain building throughput.

#### ⚔️ 90° Mass Assault State Preservation (`CommandScepterItem`, `MinionEntity`)

- **Non-Intrusive Tactical Mass Assault**:
  - Channeling the 90° forward sector (Banner of Courage) over hostile mobs now directs combat-capable minions into a coordinated Mass Assault without converting them into a following squad or setting `selected = true`.
- **Stationed Post Preservation (`guardAnchorPos`)**:
  - Minions holding position or stationed at guard posts maintain their anchor coordinates during combat. Sitting minions without an explicit anchor automatically anchor at their current post before standing up to engage. Once all queued assault targets are eradicated, minions automatically return to their guard posts via `returnToOwnerPostCombat()`.
- **Patrol Route Preservation (`patrolRouteId`)**:
  - Minions running waypoint patrol routes engage hostiles in the sector without having their route assignment cleared. Upon eliminating the threats, `MinionPatrolGoal` naturally resumes their patrol loop.
- **Ambient Roaming Preservation**:
  - Freely roaming minions remain unselected and resume wandering ambiently once combat concludes.
- **Following Squad Integrity**:
  - Minions that were already following the commander retain their selected status and reform ranks once targets are eliminated.
- **Strict Peaceful Rally Partitioning**:
  - The squad rally logic (which marshals minions into a following squad and sets `selected = true`) now triggers strictly when **no** hostiles are enclosed in the sector, preventing accidental disbanding of defensive garrisons and patrols during combat orders.

#### 🎨 Granular Blueprint Palette Remapping & Material Replacement (`DESIGN` Mode)

- **Dedicated Palette Remapping Modal (`BlueprintPaletteRemapModalScreen`)**:
  - Live volumetric scan aggregates unique non-air blocks into a sorted tally table (descending by block frequency).
  - Offers granular per-block row controls: `[ 🔍 Change ]` (opens searchable block picker), `[ ✕ Erase ]` (maps block to `minecraft:air` to omit it from the blueprint), and `[ ↺ Reset ]` (reverts to original captured block).
  - Includes batch actions: `[ ↺ Reset All ]` and `[ ✔ Apply ]` with real-time active substitution count badges.
- **Strict Placeable Block Picker (`BlockPickerModalScreen`)**:
  - Searchable 9-column grid modal featuring real-time sub-millisecond query filtering across block names and registry identifiers.
  - **Strict Placeable Block Guardrail**: Strictly indexes items where `item instanceof BlockItem && !blockItem.getBlock().getDefaultState().isAir()`, permanently preventing players from selecting non-block items (swords, tools, armor, food, diamonds, sticks, etc.).
- **Directional BlockState Property Preservation (`transferBlockStateProperties`)**:
  - Intelligently transfers directional and architectural properties (`facing`, `half`, `shape`, `axis`, `type`, `waterlogged`) when substituting blocks (stairs, slabs, pillars, logs, fences, walls), ensuring architectural fidelity and orientation remain intact.
- **Air as "Eraser" Block**:
  - Erased blocks mapped to `minecraft:air` are cleanly omitted from blueprint compilation; minions never queue, place, or require erased blocks.
- **Full Networking & Server-Side Security Enforcement**:
  - `CaptureSpatialBlueprintPayload` carries `Map<Identifier, Identifier> blockReplacements` with custom VarInt count and Identifier packet codecs.
  - Server validation in `ModNetworking.handleCaptureSpatialBlueprint` strictly verifies all replacement targets resolve to registered `BlockItem` instances or `minecraft:air`, dropping any illegal non-block submissions.
- **Dynamic Survival Bill of Materials (BOM) Recalculation**:
  - Substituted blocks dynamically recalculate `requiredItems`, immediately updating survival mode builder resource requirements and the Material Bill of Materials (BOM) in `BlueprintResourceEstimatorModalScreen`.

#### ⚡ High-Performance 3D Voxel Engine (`BlueprintMeshCache` & Zero-Allocation Depth Sorting)

- **Eliminated 3D Viewer Lag on Massive Structures**:
  - Fixed severe frame drops when inspecting large captured structures (e.g., villages, keeps, or castles with 10k–50k blocks).
  - **Surface Face Pre-Caching (`BlueprintMeshCache`)**: Solid-to-solid interior faces are pre-culled once when the blueprint loads, discarding 85–95% of hidden faces from runtime consideration.
  - **Zero-Allocation Primitive Depth Buffer**: Replaced per-frame `ProjectedFace` object allocations and boxed `Double` comparator sorting with reusable primitive `long[]` sorting (`Arrays.sort`) encoding 32-bit depth and face index, completely eliminating per-frame garbage collection pauses.

#### 🛡 Sentinel Wall-Penetrating "Hearing" & Sentry Senses (`MinionActiveTargetGoal`, `MinionEntity`)

- **Exclusive Wall-Penetrating Echolocation for Sentinels**:
  - Sentinels (and `AUTO` minions dynamically adapted as Sentinels) gain supernatural sentry senses (`MinionEntity.hasSentrySense()`), autonomously detecting and targeting allowed hostile mobs through solid walls, closed doors, floors, and ceilings within their 24-block horizontal and expanded 128-block perimeter guard zone.
  - **Expanded Vertical Sentry Search Box**: Vertical detection expanded from `4.0D` to `12.0D` (`±12.0D` vertical search box) exclusively for Sentinels, allowing sentries on bastion towers or elevated battlements to detect intruders infiltrating beneath them or underground.
  - **Dual Predicate Architecture**: `MinionActiveTargetGoal.warriorPredicate` retains standard strict line-of-sight visibility checks (`canSee == true`), while `sentinelSentryPredicate` applies `TargetPredicate.createAttackable().ignoreVisibility()`.
  - **Strict Role Isolation**: Warriors, Builders, and all other non-Sentinel roles strictly require direct line of sight to acquire targets and drop aggro if an enemy remains obscured behind walls for >60 ticks (`unseenMemoryTicks`).
  - **Atmospheric Echolocation Alerts & VFX/SFX**:
    - The instant a Sentinel senses an enemy behind a wall, it triggers `triggerSentinelSentrySenseAlert()`, snapping its gaze toward the vibration vector.
    - Emits teal soul vibration particles (`ParticleTypes.SCULK_SOUL`) and arcane glyphs (`ParticleTypes.ENCHANT`), accompanied by sculk clicking (`SoundEvents.BLOCK_SCULK_SENSOR_CLICKING`) and resonant crystal vibrations (`SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE`).
    - Periodic echolocation pulses repeat every 20 ticks (1.0s) while actively tracking an obscured target.
  - **Allied Outpost Tactical Mobilization**:
    - Upon sensing an obstructed hostile, the Sentinel emits an outpost alert dispatching nearby allied combat minions (Warriors and Sentinels within 16 blocks without an active target) to investigate and intercept the enemy.
  - **Pathfinding & Weapon Engagement Coordination**:
    - When locked onto a target behind cover, Sentinels navigate using pathfinding toward the hostile or flank around obstructions. If equipped with ranged weapons (`MinionRangedAttackGoal`), they maneuver around walls until line of sight is achieved before releasing arrows, fireworks, or tridents.

### Changed & Fixed

#### 🎯 Left-Click / Ground-Hitting Blueprint Rotation Removed

- **Standard Left-Click Behavior in Build Mode**: Left-clicking in `BUILD` mode is now a standard attack (survival) or break block (creative) action. Ground-hitting no longer triggers blueprint rotation.
- **Exclusive `[R]` Key & GUI Rotation**: Blueprint rotation is now exclusively triggered via the `[R]` key or GUI rotation buttons.
- Updated action bar flight guide (`[R] Rotate`), Command Scepter item tooltips, and client input contract tests.

#### 📦 Expanded Builder Minion Container Search Radius (32 Blocks)

- **Expanded Scavenge Radius**: Builder minions now search nearby containers (chests, barrels, shulker boxes) within **32 blocks horizontally and 16 blocks vertically** (expanded from 12 blocks).
- **Sub-Millisecond Chunk Querying**: Implemented `chunk.getBlockEntities()` chunk-level container discovery, preventing server lag across large search volumes.
- Minion harvesting helpers (`MinionHarvestingHelper`) updated to deposit excess materials and search for storage containers across 32 blocks.

#### 🚶 Free Roaming on Patrol Route Deletion

- **Eliminated Sitting/Holding Position on Route Deletion**: When a patrol route is deleted, assigned minions have their route assignment cleared (`setPatrolRouteId(-1)`), are set to not sit (`setSitting(false)`), have their guard anchor cleared, and stop navigation so their ambient wandering AI (`WanderAroundFarGoal`) engages immediately to roam freely.

#### 🖥 Card Title & Size Text Overlap Resolved in 3D Explorer

- **Fixed UI Collision**: Replaced vanilla `ButtonWidget` centering with `CardSlotButton` and two-line rendering in `BlueprintExplorerScreen.render()`:
  - Card Title rendered at `cardY + 5`.
  - Card Dimensions and Block Count rendered at `cardY + 18`.
  - Selection indicators (`▶`) and badges (`[C]`, `[B]`) rendered with hover highlights without text collision.

#### 🎛️ Uncluttered 3D Viewer Action Button Architecture (`BlueprintExplorerScreen`)

- **Eliminated Button Crowding**: Redesigned the 3D Blueprint Explorer button layout to guarantee clean visual hierarchy, generous margins, and full-word labels without cramming or unreadable abbreviations.
- **Specifications Card Rename Integration**:
  - In standard split-pane mode, elevated the **`[ ✏ Rename ]`** button directly into the Specifications Card header (`vpX + vpW - 58, specY + 3, 52, 14`) right beside the blueprint title it modifies.
  - Automatically trims long titles with ellipsis while reserving dedicated space for the rename button when a custom blueprint is selected, and gives full width to built-in blueprint titles.
- **Spacious Bottom Action Bar**:
  - The bottom bar in split-pane mode now houses exactly 5 full-word action buttons across the 238px pane with 3px margins: **`[ ✔ Arm ]`** (48px), **`[ 🎨 Remap ]`** (54px), **`[ 📋 BOM ]`** (44px), **`[ 🗑 Del ]`** (40px), and **`[ Close ]`** (40px).
  - Maximized mode seamlessly distributes all 6 actions across the expanded 376px bottom toolbar with spacious gaps and zero visual clutter.

#### 📚 Documentation Organization & Numbering Harmonization (`COMMANDS.md`, `FEATURES.md`)

- **COMMANDS.md Structure & Categorized Cheat Sheets**:
  - Implemented an interactive Table of Contents linking to all 13 core sections.
  - Deconstructed the monolithic cheat sheet into 7 focused, categorized control tables (Core Scepter Directives, Tactical Combat & Retreat, Multiblock Construction & Flight, Custom Blueprint Capture & Palette Remap, Mining & Area Clearance, Visual Pathway Patrols, and Minion Direct Interactions & Squad Escorts).
  - Harmonized and sequentialized all section headings 1 through 13, eliminating duplicate numbers and missing dividers.
- **FEATURES.md Technical Hierarchy & Section 28 Integration**:
  - Added full Table of Contents entries for Section 28 (Minion Tactical Sneaking & Overhead Stealth) and comprehensive subsection links for Sections 7, 17, 21, and 27.
  - Standardized naming across documents from "Loki Command Scepter" to "Sovereign Command Scepter".
  - Normalized subsection numbering across in-world spatial capture (21.1 through 21.9).

#### 📋 Material Bill of Materials (BOM) Cleanup

- Removed the redundant category tallies section (`Stone 0/N | Timber 0/N | Glass 0/N | Organic 0/N`) from `BlueprintResourceEstimatorModalScreen`, shifting table rows up and displaying 5 rows per page for a cleaner layout.

#### ⛏ Seamless Building & Mining Session Chaining & Egress Prevention

- **Eliminated Egress Traps & Forced Ground Landings for Builders & Miners**:
  - `ConstructionManager.completeSession` now completely bypasses `startEgressFromStructure` when chaining to an active adjacent session (`nextSession.isPresent()`) for both construction and mining. Minions never trigger building egress or perimeter wall sweeps when another job is ready.
  - Added `MinionEntity.clearBuildingEgress()`, which immediately purges egress state without commanding downward glide descent (`-0.22D`) or disabling Arcane Levitation.
  - In `MinionBuildGoal.setupTaskKinematics`, replaced `finishBuildingEgress` with `clearBuildingEgress()`, ensuring workers flying between distant or lower-elevation tasks remain in permanent 3D Arcane Levitation without falling back to ground navigation.
- **Immediate Session Completion State (`markCompleted`)**:
  - `ConstructionSession.markCompleted()` updates `status` to `COMPLETED` immediately during `completeSession()`, ensuring `session.isActive()` evaluates to `false` without tick delay.
- **Instant Goal Chaining & Removed Session Purging (`MinionBuildGoal`)**:
  - In `tryClaimTaskOrChainNextSession`, actively verifies session presence in `ConstructionManager.getSession(id)` to immediately discard completed sessions and claim chained tasks.
  - Extended flight stall tolerance to 160 ticks (8.0s) so minions flying between distant sites remain in uninterrupted Arcane Levitation.
  - Gated `startEgressFromStructure` in `MinionBuildGoal.stop()` and `tick()` behind `!hasChainedSession`.
- **Synchronized 128-Block Construction Engagement**:
  - Expanded default `isMinionEngagedInConstruction` radius to 128.0 blocks to match builder chaining and operational search radius.

#### 🔨 Absolute Builder Combat Immunity & Non-Combatant Enforcement (`MinionEntity`, `MinionBuildGoal`, `MinionActiveTargetGoal`, `CommandScepterItem`)

- **Strict Target Acquisition Lockdown**:
  - `MinionEntity.setTarget(LivingEntity)` and `MinionEntity.isTargetAllowed(LivingEntity)` immediately reject and discard any hostile target when the minion's role is `MinionRole.BUILDER`.
  - Switching to `BUILDER` (manually via `setRole` or dynamically via `setAdaptiveRole`) immediately purges active targets (`setTarget(null)`) and clears all queued assault targets (`clearAssaultTargets()`).
  - Added continuous tick validation in `MinionEntity.tick()` to instantly nullify any assigned targets if a builder somehow receives one.
- **Complete AI Combat Goal Suppression**:
  - Builder minions strictly bypass melee attack (`MeleeAttackGoal`), ranged attack (`MinionRangedAttackGoal`), retaliation (`RevengeGoal`), owner combat tracking (`TrackOwnerAttackerGoal`, `AttackWithOwnerGoal`), and squad leader combat tracking (`TrackLeaderAttackerGoal`, `AttackWithLeaderGoal`).
  - Minion attack methods (`tryAttack` and `shootAt`) immediately return `false`/no-op if invoked on a builder, ensuring builders never swing weapons or fire projectiles.
- **Scepter Command & Tactical Signal Exclusion**:
  - Command Scepter mass assault cone sweeps, single-target attack clicks, and `ATTACK` mode broadcasts strictly filter out Builder minions, preventing commanders from commanding builders into combat.
  - Sentinel echolocation sentry pings and Patrol Route breach alerts exclude builders from allied combat dispatch alarms, ensuring builders never mobilize to investigate or engage detected hostiles.
- **Dynamic Adaptation in `AUTO` Mode**:
  - Minions operating under `MinionRole.AUTO` dynamically adapt:
    - If the minion itself, commander, or any nearby ally/golem needs healing (< 70% max HP), they adapt to **`MinionRole.SENTINEL`** (medic).
    - If actively attacked, targeting an enemy, or hostiles enter within 16m, they adapt to **`MinionRole.WARRIOR`** (combat vanguard) to defend and eliminate threats.
    - If peaceful with construction tasks available, they adapt to **`MinionRole.BUILDER`**.
    - Taking damage immediately triggers `evaluateAutoRole()` without waiting for tick intervals.
  - Dedicated `MinionRole.BUILDER` minions strictly remain non-combatants and never fight or adapt under any circumstances.
- **Zero Solo Mob Hunting Fallback (`MinionHarvestingHelper`)**:
  - Completely eradicated solo mob hunting fallback for builders. When organic/mob materials are required, builders commission nearby Warriors on tactical hunting contracts; if no warrior is available, the builder cleanly pauses and waits for assistance instead of attempting to hunt hostiles solo.

#### 🚶 Autonomous Free Roaming on Construction & Mining Completion

- **Eliminated Hold Position Lock on Build/Mine Finish**:
  - `ConstructionManager.completeSession` now releases minions to roam freely upon concluding construction or mining sessions when no chained blueprint is queued (`minion.setSitting(false); minion.setGuardAnchorPos(null); minion.setSelected(false);`).
  - Minions safely egress from completed structures/quarries onto solid ground, land cleanly from Arcane Levitation, and naturally engage `WanderAroundFarGoal` on foot without displaying `[HOLD]` or `[Stationed]` overhead badges.
  - Placing down a subsequent blueprint or mining area within 128 blocks immediately mobilizes the wandering minions via `MinionBuildGoal.canStart()` (AI priority 4) without requiring manual interaction.

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
