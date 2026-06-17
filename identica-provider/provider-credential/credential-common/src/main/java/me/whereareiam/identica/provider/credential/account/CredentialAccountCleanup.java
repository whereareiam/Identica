package me.whereareiam.identica.provider.credential.account;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.provider.credential.CredentialConstants;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;
import me.whereareiam.identica.type.event.EventOrder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CredentialAccountCleanup implements AccountLifecycleParticipant {
	private final CredentialAccountService credentialService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;

	@Override
	public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
		deleteByUniqueId(event.getIdentity().getAccountUniqueId());
	}

	@Override
	public @NotNull EventOrder order() {
		return EventOrder.LOW;
	}

	private void deleteByUniqueId(@Nullable UUID uniqueId) {
		if (uniqueId == null) return;

		for (var link : providerLinkPersistenceService.findByUniqueId(uniqueId)) {
			if (link == null) continue;
			if (!CredentialConstants.PROVIDER_ID.equalsIgnoreCase(link.getProviderId()))
				continue;

			String subject = link.getProviderSubject();
			if (subject.isBlank()) continue;

			credentialService.delete(subject);
		}
	}
}
