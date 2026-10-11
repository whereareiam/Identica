package me.whereareiam.identica.platform.common.stage.profile;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * What profile preparation decided for a connecting player. A platform handles every outcome, so each one has to
 * say what it does with a refusal or a failed run.
 */
public sealed interface ProfileOutcome {
	/**
	 * The connection continues with the prepared profile.
	 *
	 * @param uniqueId unique ID the connection continues with, or {@code null} to keep the current one
	 * @param username username the connection continues with
	 */
	record Rewritten(
			@Nullable UUID uniqueId,
			@NotNull String username
	) implements ProfileOutcome {
	}

	/**
	 * The connection must not join. The refusal is also stored as the prepared decision, so the login stage
	 * enforces it on platforms whose profile stage cannot refuse.
	 *
	 * @param message reason shown to the player
	 */
	record Refused(@NotNull Component message) implements ProfileOutcome {
	}

	/**
	 * Profile preparation did not reach a decision. Nothing is stored for the connection.
	 *
	 * @param cause why preparation failed
	 */
	record Failed(@NotNull Throwable cause) implements ProfileOutcome {
	}
}
