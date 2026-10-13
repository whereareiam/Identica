package me.whereareiam.identica.common.identity.account;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.common.util.UniqueIdResolutionSupport;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.AccountReservationPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.account.AccountClearEvent;
import me.whereareiam.identica.event.account.AccountDeleteEvent;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.identity.ReservationCache;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.SessionCloseRequest;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.username.UsernameWriteResult;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.identica.model.identity.AccountOperationRequest;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultAccountService implements AccountService {
	private final AccountPersistenceService accountPersistenceService;
	private final AccountReservationPersistenceService reservationPersistenceService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final ReservationCache reservationCache;
	private final SessionService sessionService;
	private final EventManager eventManager;
	private final Provider<Settings> settingsProvider;

	@Override
	public @NotNull Optional<Account> find(@NotNull UUID uniqueId) {
		return accountPersistenceService.findByUniqueId(uniqueId);
	}

	@Override
	public @NotNull List<Account> find(@NotNull String username) {
		return accountPersistenceService.findByUsername(username);
	}

	@Override
	public @NotNull UsernameWriteResult create(
			@NotNull Account account,
			@NotNull AccountUsernameSource source,
			@Nullable String providerId
	) {
		return accountPersistenceService.create(account, source, providerId);
	}

	@Override
	public void clear(@NotNull AccountOperationRequest request) {
		reserveUsername(request.getAccount());
		execute(request, new AccountClearEvent(identity(request.getAccount())));
	}

	@Override
	public void delete(@NotNull AccountOperationRequest request) {
		reservationPersistenceService.deleteByUniqueId(request.getAccount().getUniqueId());
		execute(request, new AccountDeleteEvent(identity(request.getAccount())));
	}

	private void execute(
			@NotNull AccountOperationRequest request,
			@NotNull AccountLifecycleEvent event
	) {
		Account account = request.getAccount();
		closeSession(account, request.isDisconnect(), request.getDisconnectMessage());
		forgetPendingReservations(account);
		eventManager.call(event);
	}

	/**
	 * Forgets the UUIDs reserved for the account's provider subjects while they registered. The lifecycle event
	 * removes the provider links, so a reservation left behind would hand the next registration of a subject the
	 * UUID of the account that is gone, ahead of the UUID mode and of a reservation of the username.
	 */
	private void forgetPendingReservations(@NotNull Account account) {
		for (AccountProviderLink link : providerLinkPersistenceService.findByUniqueId(account.getUniqueId())) {
			String subjectKey = UniqueIdResolutionSupport.buildSubjectKey(link.getProviderId(), link.getProviderSubject());
			if (subjectKey != null) reservationCache.invalidate(subjectKey).join();
		}
	}

	private void closeSession(@NotNull Account account, boolean disconnect, @NotNull String disconnectMessage) {
		sessionService.close(SessionCloseRequest.builder()
				.uniqueId(account.getUniqueId())
				.disconnect(disconnect)
				.disconnectMessage(disconnectMessage)
				.build()).join();
	}

	private @NotNull ConnectionIdentity identity(@NotNull Account account) {
		return new ConnectionIdentity(
				account.getUniqueId(),
				account.getUsername(),
				null
		);
	}

	private void reserveUsername(@NotNull Account account) {
		String reservationKey = UniqueIdResolutionSupport.buildUsernameKey(account.getUsername());
		if (reservationKey == null) return;

		long now = System.currentTimeMillis();
		reservationPersistenceService.reserve(
				reservationKey,
				account.getUniqueId(),
				now,
				now + reservationTtl().toMillis()
		);
	}

	private @NotNull Duration reservationTtl() {
		Duration configured = settingsProvider.get().getIdentity().getReservationTtl();
		if (configured.isZero() || configured.isNegative()) {
			throw new IllegalStateException("settings.identity.reservationTtl must be positive");
		}

		return configured;
	}
}
