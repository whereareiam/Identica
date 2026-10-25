package me.whereareiam.identica.feature.verification.model.process;

import lombok.*;
import me.whereareiam.identica.model.message.PlayerMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Display payload returned by verification processes.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class VerificationProcessDisplay {
	/**
	 * What the player is shown, such as an authenticator app's secret, or {@code null} for nothing.
	 */
	private @Nullable PlayerMessage message;
	@Builder.Default
	private @NotNull Map<String, String> placeholders = new HashMap<>();
}
