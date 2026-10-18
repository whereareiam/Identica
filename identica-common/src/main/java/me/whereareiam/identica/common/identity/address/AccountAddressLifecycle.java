package me.whereareiam.identica.common.identity.address;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.common.config.provider.AccountAddressesProvider;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.session.SessionOpenedEvent;
import me.whereareiam.identica.identity.address.AccountAddressService;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.scheduler.JobKey;
import me.whereareiam.identica.model.scheduler.Origin;
import me.whereareiam.identica.model.scheduler.PeriodicalRunnableTask;
import me.whereareiam.identica.model.scheduler.Purpose;
import me.whereareiam.identica.service.Scheduler;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Records the address of every login and cleans up the stored addresses when Identica starts and once a day.
 * Registrations record their address in the registration pipeline, as soon as the account exists.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AccountAddressLifecycle implements RuntimeLifecycle, EventListener {
	private static final JobKey MAINTENANCE = JobKey.of(Origin.core(AccountAddressLifecycle.class), Purpose.of("address-maintenance"));
	private static final long MAINTENANCE_PERIOD = Duration.ofDays(1).toMillis();

	private final Registry<Reloadable> reloadables;
	private final AccountAddressesProvider documentProvider;
	private final AccountAddressService addresses;
	private final AccountAddressMaintenance maintenance;
	private final EventManager events;
	private final Scheduler scheduler;

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.RUNTIME;
	}

	@Override
	public void initialize() {
		documentProvider.get();
		events.register(this);
		scheduler.schedule(PeriodicalRunnableTask.builder()
				.key(MAINTENANCE)
				.delay(0)
				.period(MAINTENANCE_PERIOD)
				.runnable(maintenance::run)
				.build(), true);
	}

	@Override
	public void shutdown() {
		scheduler.cancel(MAINTENANCE);
		events.unregister(this);
		reloadables.unregister(documentProvider);
	}

	/**
	 * Records the address of every session that opens. After a registration it only updates when the registration
	 * address was last used.
	 */
	@IdenticEvent
	public void onSessionOpened(@NotNull SessionOpenedEvent event) {
		Session session = event.getSession();
		addresses.recordConnection(session.getUniqueId(), session.getIp());
	}
}
