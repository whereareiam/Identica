package me.whereareiam.identica.feature.recognition.model.eligibility;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.feature.recognition.type.RecognitionAttemptKind;
import me.whereareiam.identica.feature.recognition.type.RecognitionTrigger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Recognition attempt context used to decide whether recognition may run.
 */
@Getter
@ToString
@Builder(toBuilder = true)
public class RecognitionEligibilityContext {
	private final @Nullable String providerId;
	private final @Nullable String providerUsername;
	private final @Nullable String clientIp;

	private final @Nullable ProviderContext selectedProvider;
	private final @Nullable ConnectionIdentity.Origin origin;

	private final @NotNull RecognitionAttemptKind attemptKind;
	private final @NotNull RecognitionTrigger trigger;
}
