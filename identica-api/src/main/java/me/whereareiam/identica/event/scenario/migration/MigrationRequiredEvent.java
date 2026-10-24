package me.whereareiam.identica.event.scenario.migration;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.base.SynchronousEvent;
import me.whereareiam.identica.event.scenario.ScenarioRequiredEvent;
import me.whereareiam.identica.model.migration.MigrationContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when a migration scenario becomes a held pending requirement for
 * a connection.
 */
@Getter
@ToString
@RequiredArgsConstructor
public class MigrationRequiredEvent implements Event, SynchronousEvent, ScenarioRequiredEvent {
	private final @NotNull UUID connectionUniqueId;
	private final @Nullable UUID accountUniqueId;
	private final @NotNull MigrationContext context;
	private final boolean resumed;
	private final long expiresAt;

	public MigrationRequiredEvent(
			@NotNull MigrationContext context,
			boolean resumed,
			long expiresAt
	) {
		this(
				requireConnectionUniqueId(context),
				context.getAccountUniqueId(),
				context,
				resumed,
				expiresAt
		);
	}

	private static @NotNull UUID requireConnectionUniqueId(@NotNull MigrationContext context) {
		UUID connectionUniqueId = context.getConnectionUniqueId();
		if (connectionUniqueId == null)
			throw new IllegalArgumentException("Migration context is missing connectionUniqueId");
		return connectionUniqueId;
	}
}
