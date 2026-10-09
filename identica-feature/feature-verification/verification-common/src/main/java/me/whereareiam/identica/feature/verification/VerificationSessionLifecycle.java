package me.whereareiam.identica.feature.verification;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.identity.session.SessionClosedEvent;
import org.jetbrains.annotations.NotNull;

/** Clears local enrollment state using the account ID carried by session events. */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class VerificationSessionLifecycle implements EventListener {
	private final VerificationService verificationService;

	@IdenticEvent
	public void onSessionClosed(@NotNull SessionClosedEvent event) {
		verificationService.cancelPendingEnrollment(event.getUniqueId());
	}
}
