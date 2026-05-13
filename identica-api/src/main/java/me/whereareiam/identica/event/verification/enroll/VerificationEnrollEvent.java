package me.whereareiam.identica.event.verification.enroll;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.event.base.CancellableEvent;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.base.SynchronousEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Fired before verification enrollment starts for an identity.
 *
 * <p>Listeners may cancel the event to deny enrollment or rewrite the provider
 * and method ids before the enrollment is created.</p>
 *
 * <pre>{@code
 * @IdenticEvent
 * public void onEnroll(VerificationEnrollEvent event) {
 *     if ("totp".equalsIgnoreCase(event.getMethodId())) {
 *         event.setProviderId("password");
 *     }
 * }
 * }</pre>
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
public class VerificationEnrollEvent implements Event, SynchronousEvent, CancellableEvent {
	private final @NotNull UUID uniqueId;
	private final @NotNull String username;
	private @Nullable String providerId;
	private @NotNull String methodId;
	private boolean cancelled;
}
