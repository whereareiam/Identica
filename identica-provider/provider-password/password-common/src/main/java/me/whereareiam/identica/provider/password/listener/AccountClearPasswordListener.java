package me.whereareiam.identica.provider.password.listener;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.provider.password.PasswordConstants;
import me.whereareiam.identica.provider.password.account.PasswordAccountService;
import me.whereareiam.identica.type.event.EventOrder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Singleton
public class AccountClearPasswordListener implements EventListener {
	private final PasswordAccountService accountService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;

	@Inject
	public AccountClearPasswordListener(
			@NotNull PasswordAccountService accountService,
			@NotNull ProviderLinkPersistenceService providerLinkPersistenceService,
			@NotNull EventManager eventManager
	) {
		this.accountService = accountService;
		this.providerLinkPersistenceService = providerLinkPersistenceService;
		eventManager.register(this);
	}

	@IdenticEvent(EventOrder.LOW)
	public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
		deleteByUniqueId(event.getIdentity().getUniqueId());
	}

	private void deleteByUniqueId(@Nullable UUID uniqueId) {
		if (uniqueId == null) return;

		for (var link : providerLinkPersistenceService.findByUniqueId(uniqueId)) {
			if (link == null) continue;
			if (!PasswordConstants.PROVIDER_ID.equalsIgnoreCase(link.getProviderId()))
				continue;

			String subject = link.getProviderSubject();
			if (subject.isBlank()) continue;

			accountService.delete(subject);
		}
	}
}
