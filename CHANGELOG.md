# Changelog

All notable changes to **Sovereign Simulator: Arcane Strategy & Engineering** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.3] - 2026-10-23

### 🛠️ Builder Minion Survival Never-Stall Upgrade, Standby Default Mode, Quarry Flight & Supply Chains

> **Release Overview**:
> - 🕊️ **Neutral Standby Mode (`DEFAULT`)**: Keep your Sovereign Command Scepter equipped 24/7 in either your main hand or off-hand without interrupting normal gameplay! In Default mode, the scepter steps aside so you can open chests and doors, use crafting tables and furnaces, trade with villagers, breed animals, and eat food just like in vanilla Minecraft. Press `V` or Sneak + Right-Click in the air whenever you want to issue orders!
> - 🦅 **Quarry & Architect Aerial Flight (Mining & Design Modes)**: Take flight in Survival mode while holding the scepter in either Mining or Design mode! Survey massive excavation quarries, check slope boundaries from above, frame custom blueprint selection boxes (`Pos1`/`Pos2`), and capture sprawling structures from high altitude with smooth, unconstrained 3D flight and safe touchdown landing.
> - 🛡️ **Airborne Minion Ground-Tracking & Anti-Fall Teleportation**: When following you while you fly high in the air, minions no longer spawn in thin air and plummet to their death! If there is no safe elevated platform of at least 2x2 solid blocks near your altitude, minions safely track you from the ground below—teleporting along the (X, Z) coordinates directly onto safe solid terrain or water surfaces. If you fly over a lethal lava sea, minions strictly refuse to teleport into the fire and remain patiently waiting on the shore!
> - 💧 **Minion Water Gathering Outside Barriers**: Builder minions needing a water bucket for construction or crafting can now autonomously leave protected barrier walls to scoop water from exterior lakes, rivers, and oceans. Minions also recognize self-replenishing infinite water pools and cauldrons, and can even fetch empty buckets from nearby village storage chests so your blueprints never get stuck!
> - 🌱 **Organic Substrates & Living Growth Biology**: Builder minions understand that Grass Blocks and Mycelium cannot be crafted and drop Dirt without Silk Touch. Minions prefer silk-touch grass/mycelium if present in storage, but seamlessly accept `Items.DIRT` as the precursor. In construction sessions, placed dirt is recognized as a satisfied precursor. If the minion holds Bone Meal and is adjacent to existing grass, the minion accelerates the spread with bone meal particles!
> - 🔥 **Nylium Substrate & Bone Meal Transformation**: `Blocks.CRIMSON_NYLIUM` and `Blocks.WARPED_NYLIUM` map to `Items.NETHERRACK`. When Netherrack is placed adjacent to existing Nylium and the minion carries Bone Meal, the minion consumes 1 Bone Meal to trigger instant vanilla Nylium conversion!
> - 🪴 **Compound & Multi-Block Placements**: Potted flowers (`FlowerPotBlock`) require `Items.FLOWER_POT` (and consume the flower item if present). Double-block structures (doors, tall flowers, bed heads, piston heads) require 0 extra items for the upper half/head. Double slabs consume a second slab item from inventory.
> - 🛠️ **Never-Stall Survival Building**: Builder minions in Survival mode will no longer deadlock or freeze on complex structures. Missing materials trigger a 200-tick per-minion deficiency cooldown on that specific block so workers seamlessly claim and construct other ready tasks in the queue!
> - 🌾 **Virtual Non-Item Transformations**: Blocks without direct item forms (Farmland, Dirt Paths, growing Crops, Redstone Wire, Tripwire) now map to real survival items (`Items.DIRT`, seeds, redstone, string) instead of impossible `Items.AIR` requirements.
> - 🪓 **In-World Tool Transformations**: Builders autonomously craft and wield Hoes to till Farmland, Shovels to flatten Dirt Paths or smother campfires, Axes to strip logs, and Shears to carve pumpkins with realistic sound effects and tool durability.
> - 📚 **Multi-Tier Non-Block to Block Synthesis**: Minions can process complete survival crafting pipelines: Sugar Cane $\to$ Paper $\to$ Books $\to$ Bookshelves $\to$ Lecterns; Sand/Gravel/Dye $\to$ Concrete Powder $\to$ Hydrated Solid Concrete; Dirt/Water/Wheat $\to$ Mud Bricks; Redstone mechanisms; Apiculture candles and waxed copper; and wild flora foraging.
> - 🎒 **Automatic Backpack Hygiene**: Builders offload non-essential byproducts (seeds, excess dirt, saplings) into nearby containers when backpack free slots drop below 3, preventing full inventories from blocking builds.
> - ⛏️ **Mining Storage Auto-Offload & Drop Safety**: When mining minions' 9-slot backpacks become full during excavation or quarrying, they now automatically seek out nearby storage blocks (chests, double chests, barrels, trapped chests, shulker boxes, hoppers) up to 128 blocks away across the entire world height and offload all mined materials (cobblestone, deepslate, ores, dirt, gravel) into storage! If no storage blocks exist or all nearby storages are 100% full, minions will NOT place unwanted chests—instead dropping excess items on the ground so mining never stalls!
> - 🎨 **Creative-Only Block Purge & Safe Palette Fallbacks**: GUI block pickers and network packets strictly filter out creative-only and operator blocks (`Blocks.LIGHT` / "Light bulb", `BARRIER`, `COMMAND_BLOCK`, `SPAWNER`, `BEDROCK`, etc.) while whitelisting Farmland and Dirt Paths. Uncraftable exotic materials automatically fall back to harmonious palette substitutes.
> - 🧱 **Physical Placement Safeguards**: Enforces solid foundations below falling blocks (sand, gravel, concrete powder) and agricultural substrates (Farmland for crops, Soul Sand for Nether Wart, Jungle Logs for Cocoa), forces `LeavesBlock.PERSISTENT = true` to stop leaf decay, and safely converts Nether water into Blue Stained Glass with steam VFX.

### Added

#### ⛏️ Mining Storage Auto-Offload & Drop Safety (`MinionHarvestingHelper`, `MinionBuildGoal`)
- **Mining Storage Auto-Offload (`offloadMinedItemsToStorages`, `findNearbyStorageBlocks`, `getContainerInventory`)**:
  - When mining minions' 9-slot backpacks become full during excavation or quarrying, minions automatically search for nearby storage blocks (chests, double chests, barrels, trapped chests, shulker boxes, hoppers) up to 128 blocks horizontally across full vertical world height.
  - Minions deposit their mined items (cobblestone, deepslate, stone, dirt, ores, minerals, gravel, netherrack, etc.) into these containers, freeing up backpack space so they can keep mining uninterrupted.
  - Strictly preserves essential equipment: tools, weapons, armor, food rations, torches, lanterns, buckets, and command scepters are never deposited.
  - Seamlessly handles vanilla Double Chests via `ChestBlock.getInventory`, giving minions access to all 54 slots across single and double chests.
- **No Autonomous Chest Placement for Miners / Ground Drop Safety**:
  - If no storage blocks exist within range, or if all nearby storage blocks are completely full, mining minions do NOT craft or place down unwanted chests.
  - Instead, excess mined items that cannot fit into their backpack drop safely onto the ground as loose items, allowing miners to keep clearing their tasks without getting stuck or deadlocked.

#### 🕊️ Neutral Standby Command Mode (`CommandMode.DEFAULT`, `CommandScepterItem`)
- Added `DEFAULT("default", 0.6F, "Default", "§7")` as the 8th operational scepter mode.
- In `DEFAULT` mode, `CommandScepterItem.useOnBlock` returns `ActionResult.PASS` for non-sneaking interactions, enabling vanilla interaction with chests, barrels, doors, trapdoors, crafting tables, furnaces, anvils, and redstone switches.
- `CommandScepterItem.useOnEntity` returns `ActionResult.PASS`, enabling villager trading, animal breeding, shearing, milking, and riding mounts.
- `CommandScepterItem.use` returns `TypedActionResult.pass(stack)` and `getMaxUseTime` returns `0` with `UseAction.NONE`, preventing channeled blocking or raycasting.
- `MinionEntity.interactMob` yields to normal empty-hand station/follow toggling when the player holds a scepter in `DEFAULT` mode.
- `ExampleMod.java` interaction callbacks (`AttackBlockCallback`, `AttackEntityCallback`) cleanly pass through in `DEFAULT` mode for normal melee combat and block breaking.
- Added localization key `mode.modid-mmcli-agent-modding.default` in `en_us.json` and implemented `CommandMode.getTranslationKey()`.

#### 🦅 Quarry Flight in Mining Mode (`BuildFlightManager`, `CommandScepterItem`)
- Extended survival flight mechanics to `CommandMode.MINE`, granting seamless 3D creative-style flight while holding the scepter.
- Displays `§6✦ Quarry Aerial Surveyor` in the client action bar during flight in `MINE` mode.
- Enforced server-side fall damage immunity and safe touchdown landing via `ACTIVE_SERVER_BUILD_FLIERS`.
- Supports unrestricted marine flight over deep water and underwater excavation surveying.

#### 📐 Architect Aerial Flight in Design Mode (`BuildFlightManager`, `CommandScepterItem`)
- Extended 3D survival flight to `CommandMode.DESIGN` while holding the Command Scepter.
- Displays `§d✦ Architect Aerial Surveyor §8| §fFree Flight §8| §7[Space/Shift] Alt §8| §7[Pos1/Pos2] Capture` in the action bar.
- Initial hover height targets `initialGroundY + 10.0D` (clamped below ceilings), allowing commanders to survey, frame, and capture blueprints with unconstrained vantage angles without placing temporary dirt towers.
- Safe descent glide and server fall damage immunity on landing or mode exit.

#### 🛡️ Airborne Minion Ground-Tracking & Safe Teleportation (`MinionEntity.teleportToPlayer`)
- **2x2 Platform Validation (`hasAtLeast2x2Platform`)**: When the commander is elevated, verifies candidate positions have at least a 2x2 solid base beneath them, preventing minions from teleporting onto 1x1 needles or slippery edges.
- **Air-to-Ground Vertical Scanning (`findSafeTeleportDestination`)**: If the player is airborne in `BUILD`, `MINE`, `DESIGN`, or Creative flight with no 2x2 platform nearby ($|\Delta Y| > 3$), minions do NOT teleport into mid-air. Instead, they scan downward vertically to locate the highest safe solid ground directly beneath the player, matching the player's X and Z coordinates on ground level with zero fall distance!
- **Safe Water Landing (`isSafeWaterLanding`)**: If the airborne player is hovering over open water (ocean, river, lake) without solid land below, minions safely teleport to the surface of the water, utilizing their water breathing protection and buoyant swimming.
- **Lava Refusal Guard**: If the player is flying over lava (e.g. the Nether lava sea), minions search up to 16 blocks horizontally for safe shore. If no safe land or water exists, minions strictly refuse to teleport into lava or mid-air, remaining safely stationed on the bank.

#### 💧 Exterior Water Gathering & Chest Bucket Retrieval (`MinionHarvestingHelper`, `MinionBuildGoal`)
- Added two-pass geometric search in `MinionHarvestingHelper.gatherWaterBucket`: immediate 16-block cuboid scan followed by an extended 64-block horizontal and 32-block vertical scan.
- Permitted water harvesting outside barrier defense perimeters (`!isPositionProtected(pos)`), allowing citadel builders to journey beyond base boundaries to collect water from surrounding biomes.
- Added `isInfiniteWaterSource(world, pos)` detection ($\ge 2$ horizontal still water neighbors) and cauldron validation, permitting safe water retrieval inside barriers without depleting single-source decorative pools.
- Added `withdrawBucketFromNearbyContainers` allowing minions needing a `WATER_BUCKET` to autonomously scavenge empty buckets (`Items.BUCKET`) or filled water buckets (`Items.WATER_BUCKET`) from nearby village chests up to 64 blocks away.

#### 🖥️ Command Hub GUI 8-Mode Symmetrical Grid (`CommandScepterScreen`)
- Updated Command Hub mode selection to an 8-mode 2x4 grid layout.
- Added dedicated Neutral Standby Mode dashboard in the right pane when `DEFAULT` mode is selected.
- Moved `targetFilterBtn` (`§c⚔ Targets`) symmetrically to the left header at `startX + 87, startY + 100` (width 65, height 15) mirroring `explorerBtn` (`§3🔍 3D View`) on the right.

#### 🌾 Virtual Items & Tool Transformation Engine (`BlueprintBlock`, `MinionBuildGoal`)
- **Virtual Non-Item Item Mappings**: Implemented virtual item resolution in `BlueprintBlock.getRequiredItem()` for blocks lacking item forms:
  - `Blocks.FARMLAND` and `Blocks.DIRT_PATH` map to `Items.DIRT`.
  - Agricultural crops (`WHEAT`, `CARROTS`, `POTATOES`, `BEETROOTS`, `PUMPKIN_STEM`, `MELON_STEM`, `NETHER_WART`, `SWEET_BERRY_BUSH`, `COCOA`) map to their respective seeds, vegetables, and berries.
  - Mechanics and botanicals (`TRIPWIRE` $\to$ `Items.STRING`, `REDSTONE_WIRE` $\to$ `Items.REDSTONE`, `KELP_PLANT` $\to$ `Items.KELP`, `BAMBOO_SAPLING` $\to$ `Items.BAMBOO`, `CAVE_VINES` $\to$ `Items.GLOW_BERRIES`).
  - Completely eliminates impossible `Items.AIR` material requests.
- **In-World Tool Transformations (`ToolTransformationType`)**:
  - Added tool transformation classification and base state resolution: `HOE_TILL` (Dirt/Grass $\to$ Farmland, dropping bonus wheat seeds on grass), `SHOVEL_FLATTEN` (Dirt/Grass $\to$ Dirt Path), `AXE_STRIP` (Logs/Wood $\to$ Stripped variants), `SHEAR_CARVE` (Pumpkin $\to$ Carved Pumpkin, dropping 4 Pumpkin Seeds), and `SHOVEL_EXTINGUISH` (Lit Campfire $\to$ Unlit Campfire).
  - Implemented 2-phase execution in `MinionBuildGoal.executeBuildWork()`: the minion places the base block, equips the appropriate tool, plays tool usage sound effects (`ITEM_HOE_TILL`, `ITEM_SHOVEL_FLATTEN`, `ITEM_AXE_STRIP`, `PUMPKIN_CARVE`), applies 1 durability damage to the tool, and sets the transformed block state in the world.

- **Living & Organic Substrates (`Blocks.GRASS_BLOCK`, `Blocks.MYCELIUM`, `Blocks.CRIMSON_NYLIUM`, `Blocks.WARPED_NYLIUM`)**:
  - `BlueprintBlock.getRequiredItem()` maps Grass Blocks and Mycelium to `Items.DIRT`, and Crimson/Warped Nylium to `Items.NETHERRACK`.
  - Minions automatically check for Silk Touch blocks (`Items.GRASS_BLOCK`, `Items.MYCELIUM`, `Items.CRIMSON_NYLIUM`, `Items.WARPED_NYLIUM`) in their inventory and nearby storage first. If available, they consume the silk block and place it directly.
  - If Silk Touch blocks are not available, minions consume the survival precursor (`Items.DIRT` or `Items.NETHERRACK`).
  - **Bone Meal Catalysis**: If a minion places Dirt adjacent to an existing Grass Block or Mycelium and carries Bone Meal, it consumes 1 Bone Meal to accelerate spread. When placing Netherrack adjacent to Crimson or Warped Nylium with Bone Meal in inventory, the minion instantly converts the Netherrack into the respective Nylium block!
  - `ConstructionSession.isSatisfiedBlockState()` recognizes `Blocks.DIRT` at a `Blocks.GRASS_BLOCK` coordinate as a satisfied growing precursor, preventing endless replacement or deconstruction loops.
- **Compound, Multi-Block & Potted Plants**:
  - `FlowerPotBlock` correctly passes `isSurvivalPlaceableBlock()` and maps to `Items.FLOWER_POT`. When placing a potted flower, minions consume the flower pot and also consume the contained flower or sapling from inventory if available.
  - Double-block structures (`DoubleBlockHalf.UPPER` doors, tall flowers, sunflowers) and bed heads (`BedPart.HEAD`) cost `Items.AIR` as their placement is handled automatically by the base item.
  - Double Slabs (`SlabType.DOUBLE`) consume a second slab from the minion's inventory when placed.
- **Survival Unobtainable Fallbacks**:
  - `Blocks.BUDDING_AMETHYST` $\to$ `Items.AMETHYST_BLOCK`
  - `Blocks.SUSPICIOUS_SAND` / `Blocks.SUSPICIOUS_GRAVEL` $\to$ `Items.SAND` / `Items.GRAVEL`
  - `Blocks.FROSTED_ICE` $\to$ `Items.ICE`
  - `Blocks.PETRIFIED_OAK_SLAB` $\to$ `Items.OAK_SLAB`
  - `Blocks.INFESTED_STONE` variants $\to$ standard stone item counterparts.
- **Dynamic BlockState Equality**:
  - `ConstructionSession.isSatisfiedBlockState()` tolerates runtime properties that naturally vary in survival: dynamic leaf decay distance (`LeavesBlock`), composter fill levels, chest double-connection left/right flags, redstone wire power fluctuations, and cauldron liquid levels.

#### 🧰 Autonomous Tool Crafting, Wielding & Durability Management (`MinionHarvestingHelper`)
- **Dynamic Tool Autonomy (`ensureTool`)**:
  - Added `ensureTool(minion, world, toolClass)` which dynamically inspects the minion's inventory for a valid tool matching the required class (`HoeItem`, `ShearsItem`, `ShovelItem`, `AxeItem`, `PickaxeItem`, `FlintAndSteelItem`).
  - Added durability check in `findToolSlot`: tools with less than 2 durability remaining are rejected so minions do not swing broken tools.
  - Added autonomous tool crafting: `craftHoe` (2 Sticks + 2 Planks/Cobblestone/Iron), `craftShears` (2 Iron Ingots), and `craftFlintAndSteel` (1 Iron Ingot + 1 Flint).

#### 📚 Multi-Tier Supply Chains & Crafting Pipelines (`MinionHarvestingHelper`)
- **Non-Block to Block Synthesis**:
  - *Scholastic Chain*: Sugar Cane $\to$ Paper $\to$ Book (+ Leather/Rabbit Hide) $\to$ Bookshelf $\to$ Lectern (Bookshelf + 4 Slabs) & Chiseled Bookshelf.
  - *Concrete Hydration*: Sand + Gravel + Dye $\to$ Concrete Powder $\to$ hydrated into solid Concrete blocks.
  - *Mud & Adobe*: Dirt + Water $\to$ Mud $\to$ Packed Mud (+ Wheat) $\to$ Mud Bricks $\to$ Stairs/Slabs/Walls.
  - *Redstone Utility*: Hoppers, Pistons, Sticky Pistons, Observers, Dispensers, Droppers, Redstone Lamps, Target Blocks, Daylight Detectors, Repeaters, and Comparators.
  - *Apiculture & Waxing*: Honeycomb + String $\to$ Candles (with 16 dye colors); Honeycomb + Copper $\to$ Waxed Copper.
  - *Atmospheric Decor & Lighting*: Campfires, Soul Campfires, Chains, Flower Pots, Item Frames, Glow Item Frames, Armor Stands.
  - *Wild Flora Foraging*: Autonomous foraging within 32 blocks for Sugar Cane, Bamboo, Pumpkins, Melons, Sweet Berries, Glow Berries, Cocoa Beans, Kelp, and Seeds.

#### ⏱️ Non-Blocking Task Queueing & Deficiency Cooldown (`ConstructionTask`, `ConstructionSession`, `MinionBuildGoal`)
- **Per-Minion Task Deficiency Cooldown**:
  - Added `deficiencyCooldowns` map to `ConstructionTask` with `hasDeficiencyCooldown`, `setDeficiencyCooldown`, and `clearDeficiencyCooldown`.
  - When a builder minion cannot resolve materials for a task, it tags that task with a 200-tick cooldown for itself and drops failure cooldown to 10 ticks.
  - `ConstructionSession.claimNextTask()` skips tasks on deficiency cooldown for the requesting minion, allowing the worker to immediately claim other ready tasks in the queue rather than stalling the entire project.

#### 🎒 Automatic Backpack Hygiene & Surplus Item Offload (`MinionHarvestingHelper`)
- **Proactive Inventory Clearing (`maintainInventoryHygiene`)**:
  - Evaluates minion backpack capacity before each build cycle.
  - If free slots fall below 3, offloads excess byproducts (seeds, excess dirt/cobblestone, saplings) into nearby chests or barrels within 32 blocks, preventing inventory clogging.

#### 🎨 Creative-Only Block Purge & Safe Palette Fallbacks (`BlueprintBlock`, `BlockPickerModalScreen`, `ModNetworking`)
- **Survival Block Whitelist & Filtering (`isSurvivalPlaceableBlock`)**:
  - Strictly purges creative-only, operator, and indestructible blocks (`Blocks.LIGHT`, `BARRIER`, `COMMAND_BLOCK`, `STRUCTURE_BLOCK`, `JIGSAW`, `SPAWNER`, `BEDROCK`, `END_PORTAL`, etc.) from block pickers and network payloads.
  - Whitelists `Blocks.FARMLAND` and `Blocks.DIRT_PATH` in `BlockPickerModalScreen` with virtual tool placement.
- **Harmonious Palette Fallbacks (`resolvePaletteFallback`)**:
  - Allows builder minions to substitute unavailable exotic materials (Purpur, Nether Bricks, End Stone, Deepslate Bricks) with available harmonious palette blocks (Stone Bricks, Cobblestone, Planks) rather than stalling.

#### 🧱 Physical Placement Safeguards (`ConstructionSession`, `MinionBuildGoal`)
- **Solid Foundation Contract for Falling Blocks**:
  - `ConstructionSession.isBuildTaskReady()` validates that gravity-affected blocks (Sand, Gravel, Concrete Powder, Anvils) have a solid block directly beneath them before scheduling placement.
- **Substrate Readiness**:
  - Enforces Farmland beneath crops, Soul Sand beneath Nether Wart, and Jungle Logs adjacent to Cocoa Beans.
- **Physics & Dimension Safety**:
  - Forces `LeavesBlock.PERSISTENT = true` on all placed tree leaves to prevent decay.
  - Preserves fluid state when placing waterlogged blocks (`Properties.WATERLOGGED = true`).
  - Nether Water Safety: Converts water placements in the Nether into Blue Stained Glass with cloud steam particles and hiss sound effects.

---

## [1.2.2] - 2026-10-21

### ⚔️ Combat Cluster Assault, Auto Minion Mining Retention, Post-Build Escort & Aquatic Construction

> **Release Overview**:
> - ⚔️ **Smarter Group Combat**: Right-click an enemy and your squad will focus-fire it and line up all nearby monsters to eliminate them in sequence. If you click more enemies mid-fight, minions finish off their current target first before pivoting to the newly clicked targets—no more abrupt mid-battle distractions!
> - ⛏️ **Auto Minions Keep Their Mining Loot**: Minions in Auto mode mining in Survival now safely store all mined blocks and ores in their backpacks just like dedicated Builders do, rather than dropping them when switching roles.
> - 🛡️ **Guards Stay With You After Building**: Minions that were following you before helping with a build or mining job will immediately resume following you once the project finishes, while unselected helpers stay behind to guard the new site.
> - 🎨 **Recent Blocks Sidebar**: When customizing blueprint palettes, a new "Recent Picked" panel lets you 1-click your favorite or frequently used blocks without typing searches every time.
> - 🌊 **Ocean & Underwater Construction**: Build flight is now completely unrestricted over water bodies of any depth and underwater. Builder minions can work submerged without drowning or floating away, making docks, bridges, and underwater bases effortless to build.

### Added

#### ⚔️ Clustered Multi-Target Attack System (`CommandScepterItem`)
- **Area-Wide Assault Queuing**: Right-clicking a hostile mob with the Command Scepter now targets the clicked entity as the primary focus target and automatically queries all nearby clustered hostiles within a 16-block radius (`clusterBox.expand(16.0D, 8.0D, 16.0D)`).
- **Assault Queue Chaining & Non-Interrupting Queueing**: All valid monsters and enemy mobs in the cluster are populated directly into each combat minion's `assaultTargets` queue (`minion.setAssaultTargets(fullAssaultQueue)`). When the commander right-clicks multiple enemies, minions actively engaged in combat append the newly clicked targets to their assault queue (`minion.addAssaultTargets(fullAssaultQueue)`) so they finish killing their current enemy first before seamlessly pursuing the newly clicked mobs, preventing abrupt target swaps mid-fight.
- **Visual Feedback & Action Bar Notification**: Displays sweeping lock-on particles around the primary target, subtle linkage sparks on secondary targets, and an action bar confirmation: `Minions targeting cluster: N hostile(s) queued!`.

#### 🎨 Recent Picked Blocks Sidebar (`BlockPickerModalScreen`)
- **Recent Block Quick-Selection Panel**: Integrated a dedicated "Recent Picked" sidebar panel inside `BlockPickerModalScreen` (width expanded to 380px) displaying up to 10 recently selected placeable blocks.
- **LRU Cache & 1-Click Replacement**: Selecting a block pushes it to the front of the recent blocks list. Commanders can click any recent block directly from the sidebar to instantly select and remap without re-typing searches. Includes slot borders, item renderers, and tooltip info.

#### 🌊 Flying Over Water & Under Water Construction
- **Unrestricted Aquatic & Marine Flight**: Removed the previous 5-block water depth flight cancellation. Players in `BUILD` mode can now freely fly and hover over water bodies of any depth (oceans, rivers, deep lakes) and fly underwater to construct submerged structures, ocean monuments, docks, and bridges.
- **Minion Underwater Construction & Breathing**: Minions engaged in construction or arcane levitation now yield `SwimGoal` so they are not forcibly bounced to the water surface, and their air supply is continuously replenished (`setAir(getMaxAir())`) while building underwater to prevent drowning. Hover station calculation also accepts water blocks for construction stations.

### Changed & Fixed

#### ⛏️ Auto Mode Survival Mining Inventory Retention
- **Resource Retention Invariant**: Fixed an issue where minions in `AUTO` mode, upon mining blocks or completing dismantle tasks in survival mode, dumped their inventory because `evaluateAutoRole()` dynamically reassigned them to `WARRIOR` or `MinionHarvestingHelper` treated their materials as excess.
- **Unified Builder/Auto Logistics**: `MinionHarvestingHelper.checkAndDepositExcessMaterials` and `ConstructionManager.completeSession` now ensure that `AUTO` minions retain mined resources (stone, ores, dirt, wood) in their backpack inventory until full, matching dedicated `BUILDER` behavior. Overspill drops during block breaks also share into `AUTO` minion inventories.

#### 🛡️ Post-Build / Post-Mine Follow State Preservation
- **Escort Preservation**: If a minion was selected and actively following the player before helping with a build or mining session, it retains `isSelected() == true` upon session completion. As soon as construction finishes, `MinionFormationFollowGoal` immediately resumes formation escort.
- **Unselected Helpers Roam**: Minions that were unselected helpers are stationed at structure waypoints or safe fallbacks and roam freely to guard the completed build site.

---

## [1.2.1] - 2026-10-16

### 🎯 Mining Curation: Selective Block Filtering & In-World Excavation Focus

### Added

#### 🎯 Selective Mining Curation Modal (`MiningCurationModalScreen`)

- **Granular Block & Non-Block Selection Modal**:
  - Added a dedicated **Mining Curation Modal** (`MiningCurationModalScreen`, 360x240px) accessible directly from the Mining Confirmation dialog via the new **`[ 🎯 Curate Blocks ]`** button.
  - Allows commanders to selectively specify which block types minions should dismantle or leave untouched in a confirmed 3D quarry volume (e.g., harvesting only wood logs and leaves from a forest while leaving dirt/stone, or targeting only precious ores while skipping deepslate).
- **Independent Column-Based Mining Progression**:
  - Refactored `ConstructionSession.isDismantleTaskReady()` Rule 3 from a global layer-wide freeze to independent vertical column ($X, Z$) clearance.
  - Minions excavating separate columns now dig down into subsequent layers immediately once their overhead vertical column is cleared, completely eliminating layer-wide stalling where workers were previously forced to wait idle for peers to finish the entire horizontal layer.
- **Strict Area Fill Modal Isolation & Seamless Blueprint Placement**:
  - Fixed an issue where right-clicking near the ground in `BLUEPRINT` mode triggered the Area Fill modal warning (`✦ Left-click two blocks to set Pos1 and Pos2 before confirming area fill!`) and blocked blueprint placement if leftover corner coordinates were present on the scepter.
  - Scepter right-click routing in `useOnBlock` and `use()` now strictly validates `buildingMode == BuildingMode.AREA`, ensuring blueprints place effortlessly at any distance or proximity to the ground without false modal intercepts.
- **Water & Non-Block Mining & Removal**:
  - **Universal Block & Non-Block Demolition**: Builder minions now actively mine and remove water, lava, and non-blocks (plants, flowers, torches, tall grass) within the mining volume unless curated not to be mined.
  - **Fluid Curation & Bucket Icons**: Water and Lava appear directly in the curation modal list with recognizable `WATER_BUCKET` and `LAVA_BUCKET` icons and exact block counts, selected by default. Commanders can deselect Water to preserve ponds, moats, and aquifers.
  - **Aqua Ghost Grid Highlighting**: Water source blocks render with a distinct aqua/cyan ghost grid outline, and lava with a fiery red-orange outline, dynamically filtered by curation.
  - **Autonomous Water Scooping & Build Clearance**: Builder minions scoop water into buckets, craft buckets from iron ingots, scavenge nearby containers, or displace water with solid blocks. In `BUILD` mode, builders automatically clear water source blocks before placing structure blocks.
- **Real-World Terrain Scanning & Count Tallies**:
  - Automatically scans real-world client chunk blocks within the selected `[pos1, pos2]` bounding box upon opening.
  - Detects all unique destructible block types present in the volume and tallies exact block counts per type.
  - Sorts block entries descending by abundance, rendering 3D block icons (`ItemStack`), localized display names, registry identifiers (`namespace:path`), and total block counts.
- **Real-Time Search Bar & Bulk Controls**:
  - Integrated real-time search field (`[ 🔍 Search blocks... ]`) supporting block display names, namespace prefixes, and registry paths.
  - Added **`[ ✔ All ]`** and **`[ ✖ Clear ]`** quick controls for effortless bulk selection.
  - Interactive row toggle buttons (`[ ✔ Mine ]` in emerald green vs `[ ✕ Don't Mine ]` in muted dark red) for individual block management.
- **Non-Overlapping Bottom Tray & Status Bar**:
  - Relocated the Selected Targets summary section (`Selected: X / Y Types (N blocks to mine)`) into its own dedicated dashboard bar positioned cleanly above the **`[ ✖ Cancel ]`** and **`[ ✔ Apply ]`** action buttons, completely preventing text and button overlap.
- **Selective In-World Fiery Ghost Grid Highlighting**:
  - Integrated `ClientMiningCaptureTracker.isBlockCurated` and `ClientConstructionTracker.ActiveSessionClientData.curatedBlockIds` with `BlueprintHologramRenderer.renderMiningGhostGrid`.
  - **Zero Curation Invariant**: If no curation filter is applied, all destructible block types in the mining volume render fiery ghost grid boxes.
  - **Selective Curation Invariant**: If the commander selects a subset of block types (e.g. 2 block types such as coal and iron ore), **strictly and exclusively those curated block types** are highlighted with grids; all unselected blocks are completely hidden from the grid overlay.
  - **Active Session Synchronization**: Confirmed mining sessions transmit `curatedBlockIds` via `SyncConstructionSessionPayload` so persistent active mining operations continue rendering ghost grid boxes strictly on the curated targets as minions mine them, with boxes vanishing as blocks turn to air.
  - **Live Preview & Safe Revert**: Updates live in real time as blocks are toggled inside the curation modal, with automatic cancellation revert to the previous state.
- **Fail-Safe Apply Logic & Live Target Summary**:
  - Unchecking all blocks and pressing **`[ ✔ Apply ]`** automatically triggers a fail-safe that repopulates all detected blocks, preventing accidental 0-block lockouts.
  - Live summary label in `MiningConfirmModalScreen` displays real-time target status: `Targets: All Types (N blocks)` or `Targets: X/Y Types (M blocks)`.
  - The confirm dialog's `Blocks: N` counter dynamically recalculates to display the exact curated destructible block count.

#### ⚙️ Server-Side Selective Excavation Engine & Clean Session Lifecycle

- **Selective Blueprint Construction (`StructureBlueprint`)**:
  - Upgraded `StructureBlueprint.createAreaBlueprint` to accept an optional `allowedBlockIds` filter set (`Set<Identifier>`).
  - Blocks not present in the curated whitelist are omitted from task generation, allowing minions to excavate only chosen targets.
- **Autonomous Curated Task Queueing (`ConstructionSession`)**:
  - Initialized `ConstructionSession` with `curatedBlockIds` filter set for `DISMANTLE` sessions.
  - Minions only receive and execute tasks for whitelisted blocks in strict reverse topological (top-to-bottom) order.
- **Accurate In-World Completion Contract**:
  - `ConstructionSession.hasRemainingBlocksInWorld()` now strictly filters against `curatedBlockIds`.
  - Intentionally unmined blocks left behind (e.g., dirt, stone, or obsidian) no longer prevent the session from completing. Once all curated targets are cleared, the excavation session finishes cleanly, removes the wireframe highlight, and mobilizes minions.
- **Uncurated World State Safeguard (`ConstructionSession` & `MinionBuildGoal`)**:
  - In `ConstructionSession.claimNextTask()`, if a block at a queued task coordinate changed in the world (e.g., from falling sand/gravel, fluid flow, or player placement) to an uncurated block type, the task auto-completes immediately without dispatching workers.
  - In `MinionBuildGoal.executeDismantleWork()`, added a strict guard ensuring minions never break an in-world block that is not present in `curatedBlockIds`, safely skipping uncurated replacements and advancing to the next target.
- **Active Session Synchronization on World Join (`ConstructionManager.syncToPlayer`)**:
  - Added `ConstructionManager.syncToPlayer()` wired to `ServerPlayConnectionEvents.JOIN`, ensuring newly joined or reconnecting players instantly receive active construction and quarry session wireframes and ghost grids with their complete curation whitelist.
- **Network Protocol Upgrade (`StartMiningAreaPayload` & `SyncConstructionSessionPayload`)**:
  - Upgraded both `StartMiningAreaPayload` and `SyncConstructionSessionPayload` records with `List<Identifier> curatedBlockIds`.
  - Retained overloaded backward-compatible constructors ensuring zero breakage for legacy clients and network packets.

### Fixed

#### 🛡️ Barrier Visual Suppression Outside of Barrier Mode

- **Strict Visual Isolation for Sovereign Barriers**:
  - Resolved an issue where sealed barrier wireframes and floating billboard tags (`[ Sovereign Barrier #1 ]`) remained visible in the world whenever the Command Scepter was held in other modes (such as `MINE`, `PATROL`, `ASSAULT`, `RALLY`, `DESIGN`, or `BUILD` with `BLUEPRINT` / `AREA` sub-modes).
  - Added an early return guard in `BarrierHologramRenderer` that checks `(mode == CommandMode.BUILD && bMode == BuildingMode.BARRIER)`. If the player is not actively in Barrier mode, all barrier rendering (wireframes, laser columns, ghost lines, hover boxes, and waypoint tags) is completely suppressed, keeping the commander's view clean and distraction-free.

#### 🛏️ BedBlock Sleeping Fix: Sanitized Occupied State & Paired Multiblock Completion

- **Eliminated False "Bed is Occupied" Block**:
  - Resolved vanilla Minecraft's `BedBlock.OCCUPIED` bug where beds captured from existing worlds (e.g. village houses) or placed by minions retained `OCCUPIED=true`.
  - Vanilla Minecraft's `BedBlock.onUse` checks `(Boolean)state.get(OCCUPIED)` and immediately rejects the player with `"This bed is occupied"`.
  - Enforced automatic sanitization of `BedBlock.OCCUPIED` to `false` across all blueprint capture (`StructureBlueprint.captureBlocks`), property transfer (`StructureBlueprint.transferBlockStateProperties`), blueprint block instantiation (`BlueprintBlock`), and in-world placement (`MinionBuildGoal.placeBlueprintBlock`), ensuring players can always sleep in constructed beds immediately.
- **Paired Multiblock Auto-Completion**:
  - Upgraded `ConstructionSession.completeTask()` to auto-complete the corresponding other bed half (head or foot) whenever either half is completed by a minion, matching `DoorBlock` behavior in both `BUILD` and `DISMANTLE` modes.

#### ⛏️ Non-Destructive Job Queueing: Sequential FIFO Execution & Overlapping Job Gating

- **Strict Overlapping Job Gating (`isBlockedByOverlappingOlderSession`)**:
  - Implemented `isBlockedByOverlappingOlderSession()` in `ConstructionManager` and enforced it in `ConstructionSession.claimNextTask()`.
  - When multiple jobs (quarry mining areas, building blueprints, volumetric area fills) overlap geometrically in 3D space, minions strictly finish the older active job 100% as a whole before ANY minion touches or claims tasks from the newer overlapping job.
  - Completely prevents partial build/mine interleaving or race conditions where minions place blocks into an active quarry or break blocks placed by an overlapping blueprint.
- **Zero-Hitch Seamless Session Chaining (Smooth Transition)**:
  - Eliminated the 1-tick downward glide, weapon-swapping stutter, and disengagement jitter during session transitions.
  - When an active session completes or all remaining tasks are cleared, `MinionBuildGoal.tryClaimTaskOrChainNextSession()` falls through to chain the next queued session in the exact same tick.
  - Builders transition directly from blueprint construction to mining (or vice versa) in mid-air, dynamically swapping held items from blueprint blocks to pickaxes while maintaining smooth flight altitude.
- **Mining Exclusion from Constructed Structures**:
  - Updated `ConstructionManager.completeSession()` so only constructive sessions (`!session.isDismantle()`) are recorded in `constructedStructureBounds`. Completed excavation pits and quarries are no longer falsely flagged as constructed buildings.
- **Eliminated Destructive Overlap Cancellation**:
  - Resolved an issue where anchoring a new blueprint or area job while minions were actively mining caused them to abort/stop mining and immediately start building the blueprint in mid-air.
  - Removed destructive bounding box overlap cancellation (`intersects(newBox)`) from `ConstructionManager.startSession()`, allowing mining areas, structure blueprints, and volumetric area fills to coexist in a clean queue even when occupying the exact same site (e.g., clearing a hillside and then erecting a building on the cleared ground).
- **3D Bounding Box Proximity Metric (`getSquaredDistanceToSession`)**:
  - Upgraded `ConstructionManager.findNearestSessionForMinion()` and `isMinionNearActiveSession()` to use 3D bounding box proximity (`getSquaredDistanceToSession()`) rather than anchor coordinates, ensuring deep subterranean mining excavations are never falsely deemed out of range by surface workers.
- **Strict Sequential FIFO Task Commitment**:
  - Enforced that minions commit 100% of their labor to the oldest active session (`session.getCreatedTick()`) in range until all blocks are completely placed or excavated.
  - Minions seamlessly chain one by one to subsequently queued jobs upon 100% completion of the active job.
- **Uncluttered Active Mining Visuals (Zero Interior Ghost Grid Clutter)**:
  - Addressed player feedback regarding in-world grid visuals: after confirming a mining quarry, the client displays only the clean outer bounding box wireframe outline (`renderMiningBoundingBox`).
  - Interior 1×1×1 block ghost grid wireframes (`renderMiningGhostGrid`) are now displayed strictly during the drafting/selection and curation stages so players can preview targeted blocks, and are completely suppressed once confirmed so the world and mining site remain completely visible, clean, and free of grid clutter while minions are working.

---

## [1.2.0] - 2026-10-15

### 🛡️ Sovereign Barrier Defense, Volumetric Area Fill, Unified Depth Excavation & Autonomous Threat Triage

### Added

#### ⚔️ AUTO Mode Danger-First Priority & Villager Medical Triage

- **Danger Elimination First (`WARRIOR`)**:
  - In `AUTO` mode, minions strictly prioritize eliminating danger above all else. If an auto minion is actively building or mining and a threat is detected (hostile mobs within 16m, minion/owner attacked, active target acquired, or scepter assault targets queued), the minion immediately shifts its adaptive role to **`WARRIOR`**.
  - `MinionBuildGoal` checks `evaluateAutoRole()` periodically every 5 ticks; when adapting away from `BUILDER`, it immediately stops execution, safely releases its claimed task back to the `ConstructionSession` (preventing lost work or skipped blocks), restores held weapons, and lands safely from Arcane Levitation to combat the threat.
  - Removed `isActivelyBuilding()` blocks from targeting goals (`TrackOwnerAttackerGoal`, `AttackWithOwnerGoal`, `RevengeGoal`) for `AUTO` minions, guaranteeing instantaneous retaliation and target tracking.
- **Comprehensive Medical Triage & Villager Healing (`SENTINEL`)**:
  - Once all threats and hostile entities are eliminated (or in peaceful conditions), the minion evaluates health for nearby allies within 16m (< 70% max health).
  - Explicitly integrated **Villagers (`VillagerEntity`)** into medical triage: minions now treat villagers as allied teammates (`isAlliedTeammate()`), detect wounded villagers within 16m (`hasNearbyAlliesNeedingHealing()`), adapt to `SENTINEL`, and channel _Aegis of Restoration_ (`SentinelHealAllyGoal`) with healing, Regeneration II, and heart particles.
  - Also covers player commanders, multiplayer allies, fellow squad minions, non-hostile Iron Golems, and self-healing for the minion itself when under 70% max health.
- **Seamless Return to Building / Mining (`BUILDER`)**:
  - Once all nearby allies and villagers are healed to 70% max health or higher AND no threats exist, the auto minion automatically adapts back to **`BUILDER`**.
  - Scans for the nearest active construction or mining session within 128 blocks via `ConstructionManager`, claims the next task in strict FIFO order, takes flight with Arcane Levitation, and resumes work.

#### ⛏️ Unified In-World Height & Underground Depth Controls (Mining & Area Fill Modes)

- **Unified Bi-Directional Vertical Controls (`ClientMiningCaptureTracker`, `ClientBuildCaptureTracker`)**:
  - Eliminates the need to dig down underground to set the bottom corner of an excavation or area fill selection.
  - Decreasing the selection height past 1 block at the baseline surface layer (`initialBaseY`) automatically expands the volume **downward into the ground**.
  - Increasing height shrinks underground depth back toward the surface and seamlessly crosses over into upward above-ground expansion.
  - Strictly limited to **Mining Area** (`MINE` mode `AREA` sub-mode) and **Build Area Fill** (`BUILD` mode `AREA` sub-mode); custom blueprint capture (`DESIGN` mode) remains strictly upward-oriented.
- **Underground Actionbar Feedback & HUD Depth Indicators**:
  - Live HUD actionbar and modal labels display clear depth tracking (`[↓ Xb deep]`) when expanded underground while preserving dimension strings.
  - Updated `MiningConfirmModalScreen` and `BuildAreaConfirmModalScreen` button tooltips and labels (`[-5]`, `[-1]`, `[+1]`, `[+5]`) to reflect underground expansion and reduction.
- **Bedrock & Height Limits Safety Safeguards**:
  - Safely clamps underground expansion to `world.getBottomY()` (down to $Y = -64$) and `MAX_HEIGHT = 96` blocks.
  - Hologram rendering (`BlueprintHologramRenderer`) projects seamlessly below terrain surface with zero visual clipping.

#### ⛏️ Exclusive Custom Area Selection & Modal Stepper Layout Fix

- **Strict Custom Area Selection for Mining**:
  - Removed the legacy experimental `DIRECT` point-and-click dismantle sub-mode from `MINE` mode.
  - Mining operates exclusively via **Custom Area Selection** (`MiningMode.AREA`), guaranteeing that all excavation commands have explicit 3D boundary previews, real-time block counts, bedrock safety verification, and confirmation modals.
  - Command Hub GUI (`CommandScepterScreen`) removes the sub-mode toggle button and displays `✦ Custom Area Boundary Quarry` header with clean `[ ⛏ Confirm Area ]` and `[ ⌫ Reset ]` actions.
  - Scepter item (`CommandScepterItem`) and client interaction logic consistently route right-clicks and attacks in `MINE` mode to 3D area confirmation and corner stepping.
- **Modal Stepper Layout Fix (Overlap Elimination)**:
  - In both `MiningConfirmModalScreen` and `BuildAreaConfirmModalScreen`, the `[-5]`, `[-1]`, `[+1]`, and `[+5]` stepper buttons were right-aligned against the dialog window edge (`startX + 184` through `startX + 284`).
  - Formatted the left label to `§eHeight / Depth:` when expanded underground, leaving over 40px of clear space and completely eliminating text overlap between the depth indicator (`[↓ Xb deep]`) and stepper buttons.

#### ⛏️ Strict FIFO Multi-Site Job Queueing (100% Finish-Before-Start)

- **100% Finish-Before-Start Guarantee**:
  - When commanders place down multiple jobs across the world (whether building blueprints, volumetric area fills, or mining quarries), minions strictly commit 100% of their labor to the earliest placed active job (`session.getCreatedTick()`).
  - Minions will **never** abandon an older blueprint or mining area to start a newer job, even if the newer job is physically closer.
  - Only when the earliest job reaches 100% completion do minions mobilize and transition to subsequently placed jobs.
  - When sessions share the exact same creation tick, minions break ties deterministically by proximity to the closest site.
- **Thread-Safe Session Queueing**:
  - Enhanced `ConstructionManager.sessionsByOwner` with `CopyOnWriteArrayList` to ensure atomic, concurrency-safe session registration and iteration during multi-site commands.

#### 🛡️ Unloaded Chunk AI & Construction Safeguards

- **Synthetic Inner Class Elimination (`MinionFormationFollowGoal`)**:
  - Refactored `calculateFormationOffset` from an enum switch to `if-else`, eliminating the anonymous synthetic inner class `MinionFormationFollowGoal$1.class` from bytecode to prevent classloader linkage mismatches and `NoClassDefFoundError`.
- **Chunk Loading Bounds Checks in Entity AI**:
  - Added `world.isChunkLoaded` checks to `MinionFormationFollowGoal.canStart()`, `shouldContinue()`, and `resolveWalkableY()`, preventing minions from attempting to read block states or navigate across unloaded chunk sections.
  - Added `world.isChunkLoaded` checks to `MinionFollowLeaderGoal.canStart()` and `shouldContinue()` for both minion and leader coordinates.
  - Protected `MinionEntity.isSafeStandingPosition()` and `findNearestSafeStandingPosition()` against querying blocks in unloaded chunks.
- **Unloaded Chunk Session Preservation (`ConstructionSession`)**:
  - In `ConstructionSession.hasRemainingBlocksInWorld()`, verified that all chunks intersecting the session's bounding box are loaded before inspecting block states. If any chunk is unloaded, the method returns `true` (preserving remaining blocks), preventing active mining sessions from falsely concluding they are finished when chunks unload.
  - In `claimNextTask()` and `isDismantleTaskReady()`, added chunk loading checks to ensure tasks in unloaded chunks are not auto-completed based on Minecraft's virtual air fallback.

#### 🛡️ Sovereign Barrier Mode (`BuildingMode.BARRIER`, `BarrierZone`, `BarrierManager`)

- **Dedicated BUILD Sub-Mode: Barrier**:
  - Added `BuildingMode.BARRIER` alongside `BLUEPRINT` and `AREA` modes.
  - Cycled seamlessly in the Command Scepter GUI (`BLUEPRINT` -> `AREA` -> `BARRIER` -> `BLUEPRINT`) or right-clicked with the Command Scepter.
  - Allows commanders to establish protective perimeters around villages, bases, structures, and farms.
- **Clear Defensive Purpose: Minion Harvesting Protection**:
  - **Zero Accidental Harvesting**: Minions will **never** quarry stone, fell trees, scoop water, or break blocks inside any protected barrier zone by accident.
  - **Automated Agro-Forestry Plants Outside Barriers**: When builder minions require timber for blueprint construction, `findPlantingPosition` filters candidate spots through `isProtectedBlock`. Builder minions strictly plant saplings and fell trees **outside** sealed barrier perimeters in the wild, leaving village grounds and decorative trees inside the perimeter untouched.
  - **Other Minion Modes Completely Unaffected**:
    - **Follow Mode**: Minions walk, run, and cross barrier perimeters freely alongside their commander without physical obstruction.
    - **Hold Mode**: Minions hold positions normally anywhere inside or outside the barrier.
    - **Patrol (Pathway) Mode**: Minions patrol routes, guard gates, and follow waypoints inside, along, or across barrier borders without restriction.
    - **Combat / Defense**: Minions engage hostiles, fire arrows, and heal allies across barrier zones seamlessly.
  - **Explicit Task Exceptions**: Minions can only break/modify blocks inside barrier perimeters if explicitly commanded by the player via:
    - **Mining Mode** (Direct or Area mining tasks).
    - **Blueprint Construction** (explicit structural task placement).
    - **Area Fill Mode** (explicit volumetric fill tasks).
- **Dynamic Multi-Node Perimeters (Barrier 1, 2, 3.. N)**:
  - Unlike rigid 2-point rectangular boxes, Barrier mode supports an arbitrary sequence of $N$ boundary points (`Barrier 1, 2, 3... N`) to hug organic village borders, hills, rivers, and walls.
  - **Point-in-Polygon (Jordan Curve) Containment**: Evaluates interior territory using ray-casting algorithms on the horizontal X/Z plane with 1-block perimeter edge tolerance.
- **Ping-Pong Open Chain Drafting & Real-Time Ghost Tether ("Ping-Pong Ghost View")**:
  - **Open Polyline Drafting**: Perimeters remain as an open sequential path ($1 \rightarrow 2 \rightarrow 3\dots$) while building, eliminating awkward premature lines cutting across courtyards.
  - **Real-Time Crosshair Ghost Tether**: A golden vector line tethers dynamically from the last placed node to the player's crosshair, previewing the next perimeter wall before clicking.
  - **Snap-to-Close Loop Closure**: Aiming at or left-clicking **Point #1** (or intermediate points) snaps the circuit closed with a vibrant pulsing emerald beacon (`§a✦ [ Left-Click to Close & Seal ] ✦`) and immediately pops up the Seal Modal.
  - **Independent Barrier Creation**: Placing points on fresh ground always creates a brand-new independent barrier without being swallowed by nearby existing barriers.
  - **Explicit Extension**: Left-clicking an existing sealed barrier waypoint starts or resumes a barrier extension directly from that node.
- **Generous 256-Block Vertical Protection by Default**:
  - Default vertical expansion (`DEFAULT_VERTICAL_EXPANSION_UP = 256`, `DEFAULT_VERTICAL_EXPANSION_DOWN = 64`), giving full coverage from deep underground/bedrock up to 256 blocks high automatically.
- **Uncluttered Clean Interior (Perimeter-Only Boundary Visualization)**:
  - **100% Clean Interior**: Removed all center monuments, middle laser lines, ground boxes, center titles, and sky crests from inside sealed barriers. Village courtyards and base interiors remain completely unobstructed and pristine.
  - **Perimeter-Only Wireframe**: Confirmed barriers render strictly via their outer wireframe boundaries (`RenderLayer.getLines()`) and hovering billboard tags above boundary waypoints (`[ Barrier 1 #1 ]`, `[ Barrier 1 #2 ]`, etc.).
  - **Vertex Buffer Flushing**: Explicit `immediate.draw()` at the end of `WorldRenderEvents.AFTER_TRANSLUCENT` flushes all waypoint tags and boundary wireframes cleanly to the GPU without fog or clipping artifacts.
  - **Persistent Perimeter Visibility**: Confirmed sealed barrier borders render cleanly whenever holding the Command Scepter in any mode.
- **Unified Barrier Mode Controls**:
  - **Left-Click (Primary Action)**:
    - Left-clicking solid ground/blocks adds a perimeter waypoint to the active draft.
    - Left-clicking an existing confirmed waypoint starts or resumes a barrier extension directly anchored at that node.
    - Left-clicking any waypoint (Point 1 or intermediate) while drafting snaps the circuit closed and immediately opens the Seal Modal.
    - Shift + Left-Click resets and clears all unsealed drafting points.
  - **Right-Click (Removal & Modal Action)**:
    - Right-clicking on a targeted barrier waypoint (pending or confirmed) removes **only that specific clicked node**.
    - Right-clicking on **anything else** (regular ground terrain, interior land, solid blocks, or open air) opens the **Confirm Sovereign Barrier Modal** **strictly when at least 2 waypoints have been placed down**. If 0 or 1 waypoints exist, clicking non-waypoints does nothing until the second waypoint is placed down.
    - Shift + Right-Click opens the Command Hub GUI.
- **Defunct Barrier Automatic Cleanup in World & GUI**:
  - Automatically deletes any sealed barrier zone from `BarrierManager` and `ClientBarrierTracker` when its waypoint count drops below 2 after removing nodes.
  - Filtered `getConfirmedZones()`, `loadFromPersistentState()`, and `CommandScepterScreen.updateBarrierButtons()` so defunct 1-node barrier ghosts never show up in the Sovereign Barrier list in the GUI.
- **Custom Barrier Naming & Command Scepter Barrier Dashboard**:
  - **Name Input Field in Seal Modal**: Added interactive `TextFieldWidget` in `BarrierConfirmModalScreen` allowing commanders to name each barrier upon sealing (e.g. `Main Base`, `Oak Farm`, `Castle Courtyard`).
  - **Barrier Dashboard in Scepter GUI**: In `BUILD (BARRIER)` mode, displays a paginated list of all sealed barriers with name, waypoint count, and height specs.
  - **One-Click Deletion (`[ ✕ ]`)**: Permanently removes the entire barrier and all its nodes in one click, without needing to walk around and remove each node individually.
  - **Rename & Inspect Modal (`[ ✎ ]`)**: Added `BarrierEditModalScreen` to rename any existing barrier or inspect its perimeter metrics.
  - **Network Synchronization**: Added `RENAME_ZONE` action to `ModifyBarrierZonePayload` and server handling in `ModNetworking`.
- **Top-of-Block Barrier Placement**:
  - Barrier nodes are computed using `world.getBlockState(clickedPos).isReplaceable() ? clickedPos : clickedPos.offset(side)`, placing nodes directly on top of solid terrain rather than inside blocks.
- **Targeted Single-Node Barrier Removal**:
  - Right-clicking on an individual barrier node/waypoint (or targeting the floating beacon hologram beam) removes **only that specific clicked node** from the barrier perimeter.
  - All other connected barrier waypoints remain intact, preserving the overall perimeter shape.
- **Reliable Modal Opening on Land & High Altitude**:
  - Eliminated broad fuzzy distance tolerances and restricted open-air raycasting strictly to un-targeted clicks within standard interaction reach ($\le 6.0$ blocks) using tight AABB intersection.
  - Right-clicking regular terrain, interior land, or open space from any height (including $100+$ blocks aloft) reliably opens the **Confirm Sovereign Barrier** modal screen without inadvertently deleting pending perimeter nodes.
- **Render Thread Safety for Modal Opening**:
  - Ensured all modal openers in `ExampleModClient` and `CommandScepterItem` dispatch safely on the Minecraft client render thread via `client.execute(...)` and guard with `world.isClient()`, resolving Fabric Screen API `Ticking screen` crashes.
- **World Persistence & Server Lifecycle (`BarrierPersistentState`, `BarrierManager`)**:
  - Barrier zones persist across world restarts and dimension loads via `data/sovereign_barrier_zones.dat`.
  - Automatically loads and synchronizes with clients on player connection.
- **Command Scepter & GUI Controls (`CommandScepterScreen`, `CommandScepterItem`)**:
  - Replaced the right-hand blueprint catalog panel in Barrier mode with barrier metrics, ceiling height, and defensive purpose description.
  - Corrected the Building sub-mode toggle button text from `"Blue"` to `"Build"` (`§b🏛 Build`).
  - Command Hub GUI features dedicated `§6✦ Seal` and `§c⌫ Reset` buttons, dynamic banner status, and updated tooltips.
  - Full support for setting and sealing barrier nodes from the sky using the Tactical Build Camera.

#### 📦 Chests or Inventory Only Rule & Structure-Protected World Harvesting (128m Village Range)

- **Chests or Inventory Only for Non-World Resources (`MinionHarvestingHelper.isNaturallyOccurringWorldResource`, `MinionBuildGoal`)**:
  - Enforced the rule that **any block or non-block that cannot be found naturally in the world** (e.g. wood planks, stairs, slabs, fences, gates, doors, trapdoors, stone bricks, polished/smooth stone, cut sandstone, terracotta, concrete, glass, wool, carpets, lanterns, torches, workstations, beds, metal ingots) is strictly **Chests or Inventory Only**.
  - If a required non-world block or non-block is not in the minion's inventory, cannot be found in nearby containers, and cannot be crafted from base ingredients already in containers or inventory, minions will **never** attempt to break blocks from the world or dismantle builds; they cleanly trigger `handleResourceDeficiency`.
- **Protected Autonomous Harvesting for Naturally Occurring World Resources**:
  - For blocks and resources that **can be found naturally in the world** (natural logs from trees, stone/cobblestone quarried from natural stone rock, deepslate, dirt, sand, red sand, gravel, clay, and water), minions can autonomously harvest them from the environment if missing from inventory and village containers.
  - **Zero Build Damage Guarantee**:
    - **No Cobblestone Mining**: Minions quarrying for cobblestone or cobbled deepslate strictly mine natural underground `Blocks.STONE` or `Blocks.DEEPSLATE`. They **never** break `Blocks.COBBLESTONE` or `Blocks.COBBLED_DEEPSLATE` in the world, ensuring all player cobblestone walls, houses, and towers are 100% immune from destruction.
    - **Permanent Historical Structure Protection**: `ConstructionManager` now permanently remembers all historically completed and active blueprint bounding boxes (`constructedStructureBounds`), barring minions from ever quarrying within any player-constructed build.
    - **Structural Proximity Perimeter**: Any block within 6 blocks of any active/completed structure, cobblestone, or processed building block (planks, stairs, slabs, glass, doors, etc.) is strictly protected.
    - **Natural Terrain Solidity**: Natural stone quarrying checks surrounding solidity—blocks with 2 or more exposed horizontal air faces (walls, pillars) are classified as architecture and rejected.
    - **Wild Tree Validation**: Natural log felling requires natural soil roots (dirt/grass/mud) and canopy foliage, and rejects any log column near buildings or touching processed blocks.
- **Village-Scale Storage Radius (`MinionHarvestingHelper.VILLAGE_CONTAINER_SEARCH_RADIUS = 128`, `VILLAGE_CONTAINER_VERTICAL_RADIUS = 64`)**:
  - Expanded container detection radius from 32m to **128 blocks horizontally and 64 blocks vertically**, allowing minions to construct entire villages from a single central chest or storage hub.
  - **Dual-Center Search**: Scans loaded chunks around both the minion's current position and the construction session's anchor position, ensuring supply chests remain accessible anywhere in the village.
  - **High-Performance Chunk-Based Lookup**: Queries loaded chunk block entities directly for sub-millisecond container discovery across vast village volumes.
  - **Batch Restocking**: When scavenging a required block from a container, minions withdraw the placement block and restock up to 15 additional blocks into their 9-slot backpack if space permits, eliminating repeated chunk scans on subsequent placements.
  - **BOM & Modal Alignment**: Updated Bill of Materials calculations in `ModNetworking`, `CommandScepterItem`, and `BlueprintResourceEstimatorModalScreen` to scan across the full 128m village range.

#### 💀 Minion Skull & Mob Head Interaction, Inventory Access & 3D Rendering

- **Client Prediction & Direct UUID Ownership Validation (`MinionEntity.isOwner`)**:
  - Fixed an issue where players were unable to check a minion's inventory or interact with tamed minions wearing a wither skeleton skull, helmet, or armor in singleplayer or offline sessions.
  - Added direct UUID equality checks (`ownerUuid.equals(entity.getUuid())`) that evaluate reliably across both client and server, preventing reference resolution failures in `ClientWorld`.
  - Added client-side prediction allowing tamed minion interaction checks to pass on the client so that `PlayerInteractEntityC2SPacket` is dispatched to the authoritative server rather than silently aborting with `ActionResult.PASS`.
- **Seamless Right-Click Inventory Access (`MinionEntity.interactMob`)**:
  - Right-clicking an owned minion while holding items (weapons, armor, tools, blocks, skulls, or food when minion is full HP) now directly opens the `MinionScreen` inventory GUI rather than doing nothing (`ActionResult.PASS`).
  - Shift + Right-Click remains available at all times to open the inventory GUI regardless of items held.
- **Skull, Mob Head, and Carved Pumpkin Equipment Support (`MinionScreenHandler`, `MinionEntity.autoEquipFromInventory`)**:
  - Added `MinionScreenHandler.isHeadEquippable(ItemStack)` to validate mob heads (wither skeleton skulls, skeleton skulls, zombie heads, creeper heads, piglin heads, player heads, dragon heads), carved pumpkins, and helmets.
  - Head items can now be smoothly inserted, extracted, and shift-clicked (`quickMove`) into the helmet equipment slot (slot 0).
  - Builder, Sentinel, and Warrior minions can now auto-equip skulls and head items from their internal backpack into their head slot when vacant.
- **In-World & GUI 3D Head Feature Rendering (`MinionEntityRenderer`)**:
  - Registered `HeadFeatureRenderer` on `MinionEntityRenderer` using the model loader and held item renderer.
  - Minions equipped with wither skeleton skulls, mob heads, or carved pumpkins now visually render the head in full 3D in both the in-game world and the central 3D preview of `MinionScreen`.

#### 🧱 Connectable Block Alignment & Clipping Resolution (`Block.postProcessState`, `MinionBuildGoal`)

- Resolved an issue where fences, glass panes, and walls placed by builder minions appeared with open gaps and did not visually connect/clip to adjacent solid blocks.
- Added `updateConnectableNeighbors` invoking `Block.postProcessState` to immediately recalculate connection properties (`north`, `south`, `east`, `west`) whenever connectable blocks or their adjacent neighbors are placed in the world.

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
- **Smart Player-Facing Defaults (`BlockOrientationHelper`, `BuildAreaConfirmModalScreen`)**:
  - When placing directional blocks (stairs, chests, furnaces, glazed terracotta, observers, pistons, dispensers, trapdoors, slabs, logs), Area Fill automatically defaults the placement orientation to the player's horizontal gaze (`mc.player.getHorizontalFacing()`).
  - Stairs immediately ascend directly toward the player upon placement, ensuring steps are instantly walkable from the front rather than appearing as turned-around solid walls.
  - Non-directional blocks (dirt, stone bricks, cobblestone, glass) automatically hide orientation controls, keeping the modal interface clean and focused.
- **Interactive Rotation & Half Modal Controls (`BuildAreaConfirmModalScreen`)**:
  - Added interactive **`[ ⟳ Facing: North ⬆ / East ➡ / South ⬇ / West ⬅ ]`** button cycling block facing 90° clockwise with directional arrow indicators.
  - Added vertical half button **`[ ⬍ Half: Bottom (Step) / Top (Roof) ]`** for stairs, **`[ ⬍ Half: Bottom Slab / Top Slab ]`** for slabs, and **`[ ⬍ Axis: Vertical / Horizontal ]`** for pillars and logs.
  - Seamlessly updates and re-evaluates button visibility and labels whenever a new block is picked via the Block Picker.
- **In-World Real-Time Rotation Hotkeys (`ExampleModClient`)**:
  - While holding the Command Scepter in `BUILD (AREA)` mode:
    - Pressing **`R`** rotates the block facing 90° clockwise with audio feedback and action bar confirmation (`§3✦ Area Fill Facing: East ➡`).
    - Pressing **`Shift + R`** toggles vertical half / axis (`§3✦ Area Fill Half: Top / Inverted (Roof)` vs `§3✦ Area Fill Half: Bottom / Normal (Step)`).
  - Outside of `BUILD` mode, `R` and `Shift + R` seamlessly fall through to minion retreat and emergency citadel recall.
- **Authoritative Network Synchronization (`StartBuildAreaPayload`, `ModNetworking`)**:
  - `StartBuildAreaPayload` streams `facing` (`Direction`) and `invertedHalf` (`boolean`) across the network using `PacketCodec.tuple`.
  - Authoritative server session applies `BlockOrientationHelper.applyOrientation` to ensure every block placed in the volume matches the exact chosen orientation across all builder minions.
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

### Changed & Fixed

#### 🎯 Barrier Mode Control Scheme: Left-Click to Place/Extend/Seal, Right-Click to Remove/Modal (`CommandScepterItem`, `ExampleMod`, `ExampleModClient`, `BarrierHologramRenderer`)

- **Full Control Assignment for Barrier Mode**:
  - **Left-Click (Primary Action)**: Sets sequential boundary points (`Point 1, 2, 3... N`) on targeted ground blocks; left-clicking an existing confirmed waypoint initiates a new barrier extension anchored directly at that waypoint; left-clicking Point 1 or any waypoint snaps the perimeter loop closed and opens `BarrierConfirmModalScreen`.
  - **Right-Click (Removal & Modal Trigger)**: Right-clicking any existing or pending waypoint instantly deletes only that specific node; right-clicking regular terrain, solid blocks, or open air opens the Confirm modal strictly when $\ge 2$ waypoints exist.
- **Dynamic Holographic HUD & Action Bar Feedback**:
  - Hovering over any valid close target displays: `§a✦ [ Left-Click to Close & Seal ] ✦`.
  - Hovering over existing confirmed waypoints while idle highlights the node in amber and displays: `§6✦ [ Left-Click: Extend | Right-Click: Remove ] ✦`.
  - Action bar messages updated: `§6✦ Barrier Point #N set at [x, y, z] §7(Left-click any waypoint to Seal | Right-click to Remove)`.
- **Start Extension from Any Specific Clicked Waypoint**:
  - Left-clicking any waypoint (Waypoint 1, 2, or 3) of an existing confirmed barrier while idle initiates a new barrier extension directly anchored at that specific clicked waypoint (`ClientBarrierTracker.startExtensionFrom`).
  - Begins Point 1 at the clicked waypoint with the interactive golden ghost tether attached.
- **Universal Loop Closure on Created or Existing Waypoints**:
  - Commanders can close and seal their new extension barrier using **any barrier point created in the draft** (Point 1 or intermediate points) or **any waypoint that already existed** in the world across any confirmed barrier (`ClientBarrierTracker.findCloseTarget`).
- **Fixed Waypoint Closure Bug**:
  - Replaced ambiguous branching with a unified `BARRIER_CLOSER` workflow: left-clicking any valid close target seamlessly completes the circuit polygon and immediately opens the `BarrierConfirmModalScreen` with all drafted nodes intact.
  - When closing onto an existing confirmed waypoint from another barrier, that waypoint is automatically appended to complete the continuous perimeter.

#### 🛡️ Uncluttered Barrier Interior & Clean Territory Rendering (Zero Center Monuments/Pillars)

- Completely removed all center monuments, middle laser lines, ground boxes, center titles, and sky crests from inside sealed barriers (`BarrierHologramRenderer`). Village courtyards and base interiors remain 100% clean and unobstructed.
- Boundary visualization is strictly perimeter-based using outer wireframes and hovering waypoint badges.

#### 🛡️ Barrier Mode Ceiling Options Removal (`BarrierConfirmModalScreen`, `MouseMixin`, `ExampleModClient`)

- Removed ceiling adjustment buttons (`[-5]`, `[-1]`, `[+1]`, `[+5]`), ceiling row labels, and scroll/hotkey adjustments exclusively from **Barrier Mode**, as barriers already automatically cover a 256-block vertical protection span from bedrock into the high sky by default.
- Reduced the `BarrierConfirmModalScreen` window height from 168 to 160 for a clean, streamlined confirmation dialog focused on custom barrier naming and perimeter metrics.
- Vertical height adjustments and keyboard/scroll shortcuts (`Ctrl` + Scroll, `[` / `]`, `PageUp` / `PageDown`) remain 100% active and unaffected for **Design Mode** (`CommandMode.DESIGN`), **Area Mining** (`MiningMode.AREA`), and **Area Fill Construction** (`BuildingMode.AREA`).

#### 🛡️ Barrier Hologram Two-Pass Sequential Rendering Fix (`BarrierHologramRenderer`)

- Fixed client crash (`IllegalStateException: Not building!`) when placing barrier waypoints or previewing ghost tether lines with the Command Scepter in `BUILD (BARRIER)` mode.
- Interleaving `textRenderer.draw(...)` calls for waypoint badges between `WorldRenderer.drawBox` and `drawVectorLine` prematurely terminated the active `RenderLayer.getLines()` `BufferBuilder`.
- Decoupled `BarrierHologramRenderer.render` into two strict sequential passes:
  - **Pass 1 (Lines & Wireframes)**: Renders all sealed wireframe polygons, pending open polylines, emerald snap indicator boxes, and real-time crosshair ping-pong ghost tether lines in a single continuous `RenderLayer.getLines()` buffer before executing a clean flush.
  - **Pass 2 (Text & Billboards)**: Renders all floating corner badges (`[ Point 1 • Start ]`), emerald snap prompts (`[ Left-Click to Close & Seal ]`), and waypoint billboard tags cleanly via `TextRenderer`.

#### 📐 Barrier Confirm Modal Layout & Text Fit Fix (`BarrierConfirmModalScreen`)

- Expanded modal card width to 340px and formatted perimeter metrics and bulleted defensive purpose text into concise, cleanly wrapped lines.
- Completely resolved text clipping and run-off on the right border, providing comfortable margins and symmetrically aligned bottom action buttons (`[✦ Seal]`, `[✕ Cancel]`, `[⌫ Remove]`).

#### 💀 Survival Resource Origin Contract & Free Item Conjuration Fix (`MinionBuildGoal`, `MinionEntity`)

- Fixed an issue where builder minions in Survival mode could build blocks (such as skeleton skulls, mob heads, or rare blocks) without possessing the required materials.
- In Creative mode, minions equip visual block previews in their main hand; in Survival mode, `previewStack` is strictly bypassed and main hands retain held weapons, preventing role-enforcement loops from disarming preview items into backpack slots.
- Protected active builder minions from inventory stashing (`!this.isActivelyBuilding()`).
- Added explicit material item resolutions for wall-variant blocks in `BlueprintBlock.getRequiredItem`: `SKELETON_WALL_SKULL`, `WITHER_SKELETON_WALL_SKULL`, `PLAYER_WALL_HEAD`, `ZOMBIE_WALL_HEAD`, `CREEPER_WALL_HEAD`, `PIGLIN_WALL_HEAD`, `DRAGON_WALL_HEAD`, wall torches, wall hanging signs, wall signs, wall banners, and wall coral fans to prevent falling back to `Items.AIR`.
- Cleaned up Area Fill blueprint generation to resolve exact block items via `BlueprintBlock.getRequiredItem()`, preventing unmapped blocks from defaulting to cobblestone.

#### 🔇 Audio De-Spam & Deficiency Notification Throttling (`MinionBuildGoal.handleResourceDeficiency`)

- **Removed Annoying Villager Grunt Loop**: Removed `SoundEvents.ENTITY_VILLAGER_NO` from the resource deficiency trigger.
- **Global Dispenser Fail Sound Throttling**: Softened `SoundEvents.BLOCK_DISPENSER_FAIL` volume (0.5F) and throttled it to play at most once every 5 seconds (100 ticks) globally across the entire village (`DEFICIENCY_SOUND_COOLDOWN_TICKS = 100L`), eliminating repetitive click sounds.
- **Throttled Action Bar Alerts**: Deficiency alerts in the action bar are now throttled to once every 5 seconds per minion.
- **Increased Failure Retry Backoff**: Raised task failure retry cooldown from 10 ticks (0.5s) to 60 ticks (3.0s), allowing builder minions to rest smoothly rather than spamming pathfinder queries when waiting for materials.

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

### Fixed

#### 🪵 Connectable Block Seamless Clipping & Bidirectional Neighbor Updates (`MinionBuildGoal`, `ConstructionSession`)

- **Seamless Fence, Pane, Wall, and Stair Snapping**:
  - Fixed an issue where fences, glass panes, and iron bars placed by builder minions did not clip to adjacent solid blocks or neighboring connectable blocks, leaving an unnatural visual air gap.
  - Builder minions now execute `Block.postProcessState(targetState, serverWorld, targetPos)` prior to placing any connectable block (`HorizontalConnectingBlock` including fences, glass panes, iron bars; `WallBlock`; `StairsBlock`; `FenceGateBlock`; `ConnectingBlock`; `RedstoneWireBlock`; `TripwireBlock`). Connectable blocks immediately query their 6-directional neighbors and connect seamlessly upon world placement.
- **Bidirectional Neighbor Shape Updates (`MinionBuildGoal.updateConnectableNeighbors`)**:
  - Placing ANY block in the world (such as a solid stone or wooden plank wall next to an existing fence or glass pane) immediately updates all 6 adjacent connectable neighbors via `updateConnectableNeighbors`, adapting their connection flags (`north`, `south`, `east`, `west`, wall shapes, or stair inner/outer corner turns) so pre-placed fences and glass panes clip directly to the newly placed solid blocks.
  - Symmetrically updates adjacent connectable neighbors when blocks are dismantled in `executeDismantleWork`, preventing orphaned connection posts.
- **Dynamic Connection Invariant Matching (`ConstructionSession.isConnectableMatching`, `alreadyPlaced`)**:
  - Updated `alreadyPlaced` in `MinionBuildGoal` and `ConstructionSession.claimNextTask` so that connectable blocks in the world with active connection flags (`north=true`, etc.) match unplaced blueprint states (`north=false`), preventing minions from endlessly re-placing or getting stuck attempting to replace connected fences and glass panes.
  - Strictly preserves orientation properties (`FACING` and `HALF` for stairs, `FACING` for fence gates) while dynamically adapting connection shapes.

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
