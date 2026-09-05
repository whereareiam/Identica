package me.whereareiam.identica.feature.recognition.model;

import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Ephemeral recognition snapshot used to evaluate whether a reconnecting
 * player can skip provider-specific authentication.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class SessionRecognitionSnapshot {
	private @NotNull String providerId;
	private @NotNull String providerSubject;
	private @Nullable String providerUsername;
	private @Nullable String lastIp;
	private @Nullable String lastVirtualHost;
	private @Nullable Integer lastVirtualPort;
	private long capturedAt;
}
