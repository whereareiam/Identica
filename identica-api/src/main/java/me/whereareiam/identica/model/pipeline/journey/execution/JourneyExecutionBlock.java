package me.whereareiam.identica.model.pipeline.journey.execution;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.type.pipeline.journey.JourneyExecutionPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Getter
@ToString
@EqualsAndHashCode
public class JourneyExecutionBlock {
	private final @NotNull String groupId;
	private final @NotNull JourneyExecutionPolicy policy;
	private final @Nullable String providerId;
	/**
	 * Name of the provider method the block runs, or {@code null} for a block of no method.
	 */
	private final @Nullable String method;
	private final @NotNull List<JourneyExecutionStage> stages;

	/**
	 * Creates a block that runs no provider method.
	 *
	 * @param groupId group of the block
	 * @param policy how the block runs with its neighbours
	 * @param providerId provider the block runs, or {@code null}
	 * @param stages stages of the block
	 */
	public JourneyExecutionBlock(
			@NotNull String groupId,
			@NotNull JourneyExecutionPolicy policy,
			@Nullable String providerId,
			@NotNull List<JourneyExecutionStage> stages
	) {
		this(groupId, policy, providerId, null, stages);
	}

	/**
	 * Creates a block that runs one option: a provider and, when it has methods, one of them.
	 *
	 * @param groupId group of the block
	 * @param policy how the block runs with its neighbours
	 * @param providerId provider the block runs, or {@code null}
	 * @param method name of the provider method the block runs, or {@code null}
	 * @param stages stages of the block
	 */
	public JourneyExecutionBlock(
			@NotNull String groupId,
			@NotNull JourneyExecutionPolicy policy,
			@Nullable String providerId,
			@Nullable String method,
			@NotNull List<JourneyExecutionStage> stages
	) {
		this.groupId = groupId;
		this.policy = policy;
		this.providerId = providerId;
		this.method = method;
		this.stages = List.copyOf(stages);
	}

	public @NotNull String groupId() {
		return groupId;
	}

	public @NotNull JourneyExecutionPolicy policy() {
		return policy;
	}

	public @Nullable String providerId() {
		return providerId;
	}

	public @Nullable String method() {
		return method;
	}

	public @NotNull List<JourneyExecutionStage> stages() {
		return stages;
	}
}
