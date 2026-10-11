package com.example.targeting;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.util.Identifier;

/**
 * Immutable snapshot representing a named preset of allowed hostile mob type identifiers.
 *
 * @param title      User-visible title of the preset (e.g. "Creeper Safe", "Night Patrol").
 * @param allowedIds Mob type identifiers that minions are permitted to engage on sight.
 */
public record TargetFilterPreset(String title, Set<Identifier> allowedIds) {

	public TargetFilterPreset(String title, Set<Identifier> allowedIds) {
		this.title = title != null ? title.trim() : "Custom Preset";
		this.allowedIds = allowedIds != null ? Collections.unmodifiableSet(new LinkedHashSet<>(allowedIds)) : Collections.emptySet();
	}

	public boolean contains(Identifier id) {
		return this.allowedIds.contains(id);
	}

	public int size() {
		return this.allowedIds.size();
	}
}
