package com.example.barrier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;

/**
 * Represents a dynamic spatial barrier / Sovereign Ward defined by multi-point boundary nodes (Pos 1, 2, 3.. N).
 * <p>
 * Supports:
 * <ul>
 *   <li>Point-in-polygon containment on the horizontal X/Z plane (Jordan curve theorem) with boundary edge tolerance.</li>
 *   <li>Vertical height boundaries [minY, maxY] with in-game height adjustments.</li>
 *   <li>Incremental node expansion so players can expand bases without starting over.</li>
 *   <li>NBT serialization for world persistence and PacketCodec for client holographic synchronization.</li>
 * </ul>
 */
public class BarrierZone {

	public static final int DEFAULT_VERTICAL_EXPANSION_DOWN = 64;
	public static final int DEFAULT_VERTICAL_EXPANSION_UP = 256;

	private final int id;
	private final UUID ownerUuid;
	private String name;
	private final List<BlockPos> nodes = new ArrayList<>();
	private int minY;
	private int maxY;
	private boolean sealed;
	private int resumedFromZoneId = 0;

	public static final PacketCodec<RegistryByteBuf, BarrierZone> PACKET_CODEC = new PacketCodec<>() {
		@Override
		public void encode(RegistryByteBuf buf, BarrierZone zone) {
			PacketCodecs.VAR_INT.encode(buf, zone.id);
			Uuids.PACKET_CODEC.encode(buf, zone.ownerUuid);
			PacketCodecs.STRING.encode(buf, zone.name != null ? zone.name : "");
			PacketCodecs.VAR_INT.encode(buf, zone.minY);
			PacketCodecs.VAR_INT.encode(buf, zone.maxY);
			PacketCodecs.BOOL.encode(buf, zone.sealed);
			PacketCodecs.VAR_INT.encode(buf, zone.nodes.size());
			for (BlockPos pos : zone.nodes) {
				BlockPos.PACKET_CODEC.encode(buf, pos);
			}
		}

		@Override
		public BarrierZone decode(RegistryByteBuf buf) {
			int id = PacketCodecs.VAR_INT.decode(buf);
			UUID ownerUuid = Uuids.PACKET_CODEC.decode(buf);
			String name = PacketCodecs.STRING.decode(buf);
			int minY = PacketCodecs.VAR_INT.decode(buf);
			int maxY = PacketCodecs.VAR_INT.decode(buf);
			boolean sealed = PacketCodecs.BOOL.decode(buf);
			int count = PacketCodecs.VAR_INT.decode(buf);
			List<BlockPos> nodes = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				nodes.add(BlockPos.PACKET_CODEC.decode(buf));
			}
			BarrierZone zone = new BarrierZone(id, ownerUuid, name, nodes, minY, maxY, sealed);
			return zone;
		}
	};

	public BarrierZone(int id, UUID ownerUuid, String name) {
		this(id, ownerUuid, name, Collections.emptyList(), -64, 320, false);
	}

	public BarrierZone(int id, UUID ownerUuid, String name, List<BlockPos> initialNodes, int minY, int maxY, boolean sealed) {
		this.id = id;
		this.ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid cannot be null");
		this.name = (name != null && !name.isBlank()) ? name : ("Sovereign Barrier #" + id);
		if (initialNodes != null) {
			for (BlockPos p : initialNodes) {
				if (p != null) {
					this.nodes.add(p.toImmutable());
				}
			}
		}
		this.minY = minY;
		this.maxY = maxY;
		this.sealed = sealed;
		if (this.nodes.size() > 0 && (minY == -64 && maxY == 320)) {
			recalculateVerticalBounds();
		}
	}

	public int getId() {
		return this.id;
	}

	public UUID getOwnerUuid() {
		return this.ownerUuid;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<BlockPos> getNodes() {
		return Collections.unmodifiableList(this.nodes);
	}

	public int getNodeCount() {
		return this.nodes.size();
	}

	public int getMinY() {
		return this.minY;
	}

	public void setMinY(int minY) {
		this.minY = minY;
	}

	public int getMaxY() {
		return this.maxY;
	}

	public void setMaxY(int maxY) {
		this.maxY = maxY;
	}

	public boolean isSealed() {
		return this.sealed;
	}

	public void setSealed(boolean sealed) {
		this.sealed = sealed;
	}

	public int getResumedFromZoneId() {
		return this.resumedFromZoneId;
	}

	public void setResumedFromZoneId(int resumedFromZoneId) {
		this.resumedFromZoneId = resumedFromZoneId;
	}

	/**
	 * Recalculates default vertical bounds based on existing node elevations.
	 */
	public void recalculateVerticalBounds() {
		if (this.nodes.isEmpty()) {
			this.minY = -64;
			this.maxY = 320;
			return;
		}
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (BlockPos p : this.nodes) {
			if (p.getY() < min) min = p.getY();
			if (p.getY() > max) max = p.getY();
		}
		this.minY = Math.max(-64, min - DEFAULT_VERTICAL_EXPANSION_DOWN);
		this.maxY = Math.min(320, max + DEFAULT_VERTICAL_EXPANSION_UP);
	}

	/**
	 * Appends a boundary point to this barrier zone.
	 *
	 * @param pos The block position to append.
	 */
	public void addNode(BlockPos pos) {
		if (pos == null) return;
		BlockPos imm = pos.toImmutable();
		if (!this.nodes.isEmpty() && this.nodes.get(this.nodes.size() - 1).equals(imm)) {
			return; // Ignore duplicate consecutive clicks on the same block
		}
		this.nodes.add(imm);
		if (this.nodes.size() == 1) {
			this.minY = Math.max(-64, imm.getY() - DEFAULT_VERTICAL_EXPANSION_DOWN);
			this.maxY = Math.min(320, imm.getY() + DEFAULT_VERTICAL_EXPANSION_UP);
		} else {
			if (imm.getY() - DEFAULT_VERTICAL_EXPANSION_DOWN < this.minY) {
				this.minY = Math.max(-64, imm.getY() - DEFAULT_VERTICAL_EXPANSION_DOWN);
			}
			if (imm.getY() + DEFAULT_VERTICAL_EXPANSION_UP > this.maxY) {
				this.maxY = Math.min(320, imm.getY() + DEFAULT_VERTICAL_EXPANSION_UP);
			}
		}
	}

	/**
	 * Inserts a boundary node at a specific index and recalculates vertical bounds.
	 */
	public void insertNode(int index, BlockPos pos) {
		if (pos == null) return;
		BlockPos imm = pos.toImmutable();
		if (index < 0 || index >= this.nodes.size()) {
			addNode(imm);
		} else {
			this.nodes.add(index, imm);
			recalculateVerticalBounds();
		}
	}

	/**
	 * Calculates the optimal polygon insertion index to connect a new point to its nearest neighboring node.
	 */
	public static int findBestInsertionIndex(List<BlockPos> nodes, BlockPos newPos) {
		if (nodes == null || nodes.size() < 3 || newPos == null) {
			return nodes != null ? nodes.size() : 0;
		}
		int n = nodes.size();
		int nearestIdx = 0;
		double minDistSq = nodes.get(0).getSquaredDistance(newPos);
		for (int i = 1; i < n; i++) {
			double d = nodes.get(i).getSquaredDistance(newPos);
			if (d < minDistSq) {
				minDistSq = d;
				nearestIdx = i;
			}
		}

		int prevIdx = (nearestIdx - 1 + n) % n;
		int nextIdx = (nearestIdx + 1) % n;
		double distToPrev = nodes.get(prevIdx).getSquaredDistance(newPos);
		double distToNext = nodes.get(nextIdx).getSquaredDistance(newPos);

		return distToNext < distToPrev ? (nearestIdx + 1) : nearestIdx;
	}

	/**
	 * Removes the most recently added boundary node.
	 *
	 * @return The removed BlockPos, or null if empty.
	 */
	public BlockPos removeLastNode() {
		if (this.nodes.isEmpty()) return null;
		BlockPos removed = this.nodes.remove(this.nodes.size() - 1);
		if (this.nodes.isEmpty()) {
			this.sealed = false;
		} else {
			recalculateVerticalBounds();
		}
		return removed;
	}

	/**
	 * Removes a specific node from this barrier zone matching the target position.
	 *
	 * @param pos The position of the node to remove.
	 * @return True if a matching node was removed, false otherwise.
	 */
	public boolean removeNode(BlockPos pos) {
		if (pos == null || this.nodes.isEmpty()) return false;
		boolean removed = this.nodes.removeIf(p -> p.equals(pos) || p.getSquaredDistance(pos) <= 2.25);
		if (removed) {
			if (this.nodes.isEmpty()) {
				this.sealed = false;
			} else {
				recalculateVerticalBounds();
			}
		}
		return removed;
	}

	/**
	 * Clears all nodes from this barrier zone.
	 */
	public void clearNodes() {
		this.nodes.clear();
		this.sealed = false;
	}

	/**
	 * Adjusts the upper vertical ceiling by the given step offset.
	 *
	 * @param step Number of blocks to adjust (+/-).
	 * @return The new height (maxY - minY + 1).
	 */
	public int adjustHeight(int step) {
		this.maxY = Math.max(this.minY + 1, Math.min(320, this.maxY + step));
		return this.maxY - this.minY + 1;
	}

	/**
	 * Checks whether the given block coordinate lies inside this protected barrier zone.
	 *
	 * @param pos Block position to test.
	 * @return True if pos is contained within the horizontal perimeter and vertical height range.
	 */
	public boolean contains(BlockPos pos) {
		if (pos == null || this.nodes.isEmpty()) {
			return false;
		}

		// Vertical clamp check
		int actualMinY = Math.min(this.minY, this.maxY);
		int actualMaxY = Math.max(this.minY, this.maxY);
		if (pos.getY() < actualMinY || pos.getY() > actualMaxY) {
			return false;
		}

		int n = this.nodes.size();

		// Single node: Protect immediate 2-block radius
		if (n == 1) {
			BlockPos p1 = this.nodes.get(0);
			return Math.abs(pos.getX() - p1.getX()) <= 2 && Math.abs(pos.getZ() - p1.getZ()) <= 2;
		}

		// Two nodes: Bounding rectangle with 1-block perimeter buffer
		if (n == 2) {
			BlockPos p1 = this.nodes.get(0);
			BlockPos p2 = this.nodes.get(1);
			int minX = Math.min(p1.getX(), p2.getX());
			int maxX = Math.max(p1.getX(), p2.getX());
			int minZ = Math.min(p1.getZ(), p2.getZ());
			int maxZ = Math.max(p1.getZ(), p2.getZ());
			return pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ;
		}

		// 3 or more nodes: 2D Ray-Casting algorithm on horizontal X-Z plane
		// Fast bounding box reject (with 1.5-block margin for edge tolerance)
		int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
		for (BlockPos p : this.nodes) {
			if (p.getX() < minX) minX = p.getX();
			if (p.getX() > maxX) maxX = p.getX();
			if (p.getZ() < minZ) minZ = p.getZ();
			if (p.getZ() > maxZ) maxZ = p.getZ();
		}
		if (pos.getX() < minX - 2 || pos.getX() > maxX + 2 || pos.getZ() < minZ - 2 || pos.getZ() > maxZ + 2) {
			return false;
		}

		double px = pos.getX() + 0.5D;
		double pz = pos.getZ() + 0.5D;
		boolean inside = false;

		for (int i = 0, j = n - 1; i < n; j = i++) {
			BlockPos pi = this.nodes.get(i);
			BlockPos pj = this.nodes.get(j);

			double xi = pi.getX() + 0.5D;
			double zi = pi.getZ() + 0.5D;
			double xj = pj.getX() + 0.5D;
			double zj = pj.getZ() + 0.5D;

			if (((zi > pz) != (zj > pz)) && (px < (xj - xi) * (pz - zi) / (zj - zi) + xi)) {
				inside = !inside;
			}
		}

		if (inside) {
			return true;
		}

		// Boundary edge proximity tolerance (if block is directly on or within 1.25 blocks of any polygon line segment)
		for (int i = 0, j = n - 1; i < n; j = i++) {
			BlockPos pi = this.nodes.get(i);
			BlockPos pj = this.nodes.get(j);
			if (distanceToSegmentSquared(px, pz, pi.getX() + 0.5D, pi.getZ() + 0.5D, pj.getX() + 0.5D, pj.getZ() + 0.5D) <= 1.25D) {
				return true;
			}
		}

		return false;
	}

	private static double distanceToSegmentSquared(double px, double pz, double x1, double z1, double x2, double z2) {
		double dx = x2 - x1;
		double dz = z2 - z1;
		double l2 = dx * dx + dz * dz;
		if (l2 == 0.0D) {
			double ex = px - x1;
			double ez = pz - z1;
			return ex * ex + ez * ez;
		}
		double t = Math.max(0.0D, Math.min(1.0D, ((px - x1) * dx + (pz - z1) * dz) / l2));
		double projX = x1 + t * dx;
		double projZ = z1 + t * dz;
		double distEx = px - projX;
		double distEz = pz - projZ;
		return distEx * distEx + distEz * distEz;
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putInt("Id", this.id);
		nbt.putUuid("OwnerUuid", this.ownerUuid);
		nbt.putString("Name", this.name != null ? this.name : "");
		nbt.putInt("MinY", this.minY);
		nbt.putInt("MaxY", this.maxY);
		nbt.putBoolean("Sealed", this.sealed);
		if (this.resumedFromZoneId > 0) {
			nbt.putInt("ResumedFromZoneId", this.resumedFromZoneId);
		}

		NbtList list = new NbtList();
		for (BlockPos pos : this.nodes) {
			NbtCompound pTag = new NbtCompound();
			pTag.putInt("X", pos.getX());
			pTag.putInt("Y", pos.getY());
			pTag.putInt("Z", pos.getZ());
			list.add(pTag);
		}
		nbt.put("Nodes", list);
		return nbt;
	}

	public static BarrierZone fromNbt(NbtCompound nbt) {
		int id = nbt.getInt("Id");
		UUID ownerUuid = nbt.containsUuid("OwnerUuid") ? nbt.getUuid("OwnerUuid") : UUID.randomUUID();
		String name = nbt.getString("Name");
		int minY = nbt.contains("MinY") ? nbt.getInt("MinY") : -64;
		int maxY = nbt.contains("MaxY") ? nbt.getInt("MaxY") : 320;
		boolean sealed = nbt.getBoolean("Sealed");

		List<BlockPos> nodes = new ArrayList<>();
		if (nbt.contains("Nodes", NbtElement.LIST_TYPE)) {
			NbtList list = nbt.getList("Nodes", NbtElement.COMPOUND_TYPE);
			for (int i = 0; i < list.size(); i++) {
				NbtCompound pTag = list.getCompound(i);
				nodes.add(new BlockPos(pTag.getInt("X"), pTag.getInt("Y"), pTag.getInt("Z")));
			}
		}

		BarrierZone zone = new BarrierZone(id, ownerUuid, name, nodes, minY, maxY, sealed);
		if (nbt.contains("ResumedFromZoneId")) {
			zone.setResumedFromZoneId(nbt.getInt("ResumedFromZoneId"));
		}
		return zone;
	}
}
