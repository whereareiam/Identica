package me.whereareiam.identica.feature.restriction.type;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;

/**
 * Normalized identifier for a restriction signal.
 */
@RequiredArgsConstructor
public final class RestrictionSignal {
	private final @NotNull String id;

	/**
	 * Creates a normalized restriction signal id.
	 *
	 * @param id raw signal id
	 * @return normalized signal id
	 */
	public static @NotNull RestrictionSignal of(@Nullable String id) {
		String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank())
			throw new IllegalArgumentException("Restriction signal id must not be blank");

		return new RestrictionSignal(normalized);
	}

	/**
	 * Returns the normalized signal id.
	 *
	 * @return normalized signal id
	 */
	public @NotNull String getId() {
		return id;
	}

	/**
	 * Returns whether the supplied raw value matches this signal id.
	 *
	 * @param value raw id value
	 * @return {@code true} when the value matches
	 */
	public boolean matches(@Nullable String value) {
		return value != null && id.equals(value.trim().toLowerCase(Locale.ROOT));
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (!(other instanceof RestrictionSignal that)) return false;
		return id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	@Override
	public @NotNull String toString() {
		return id;
	}
}
