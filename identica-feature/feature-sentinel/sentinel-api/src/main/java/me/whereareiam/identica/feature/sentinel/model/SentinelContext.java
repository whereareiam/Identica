package me.whereareiam.identica.feature.sentinel.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Shared input context used when evaluating sentinel state.
 */
@Getter
@ToString
@Builder
public class SentinelContext {
	/** Provider selected for this attempt, or null before provider selection. */
	private final @Nullable String providerId;
	private final @Nullable UUID uniqueId;
	private final @Nullable UUID connectionUniqueId;
	private final @Nullable String username;
	private final @Nullable String ip;
}
