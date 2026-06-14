package me.whereareiam.identica.common.migration;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.lifecycle.ConnectionDisconnectedEvent;
import me.whereareiam.identica.model.migration.operation.MigrationCancel;
import me.whereareiam.identica.service.MigrationService;
import me.whereareiam.identica.type.migration.MigrationCancelScope;
import org.jetbrains.annotations.NotNull;

@Singleton
public class MigrationConfirmationLifecycle implements EventListener {
	private final MigrationService migrationService;

	@Inject
	public MigrationConfirmationLifecycle(
			@NotNull MigrationService migrationService,
			@NotNull EventManager eventManager
	) {
		this.migrationService = migrationService;
		eventManager.register(this);
	}

	@IdenticEvent
	public void onConnectionDisconnected(@NotNull ConnectionDisconnectedEvent event) {
		migrationService.cancel(MigrationCancel.builder()
				.connectionUniqueId(event.getConnectionUniqueId())
				.scope(MigrationCancelScope.CONFIRMATION)
				.build());
	}
}
