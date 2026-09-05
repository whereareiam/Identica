package me.whereareiam.identica.trait.authoritative.username;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.conflict.ConflictService;
import me.whereareiam.identica.database.schema.SchemaBootstrap;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.trait.authoritative.username.config.provider.AuthoritativeUsernameMessagesProvider;
import me.whereareiam.identica.trait.authoritative.username.config.provider.UsernameConflictsProvider;
import me.whereareiam.identica.trait.authoritative.username.conflict.UsernameConflictType;
import me.whereareiam.identica.trait.authoritative.username.database.AuthoritativeUsernameDatabaseModule;
import me.whereareiam.identica.trait.authoritative.username.database.AuthoritativeUsernameSchemaContributor;
import me.whereareiam.identica.trait.authoritative.username.database.service.DefaultAccountUsernameStatePersistenceService;
import me.whereareiam.identica.trait.authoritative.username.database.service.DefaultAccountUsernameHistoryPersistenceService;
import me.whereareiam.identica.trait.authoritative.username.pipeline.AuthoritativeUsernamePipelineExtension;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Trait-driven username runtime with explicitly owned registrations.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class UsernameLifecycle implements RuntimeLifecycle {
	private final Injector injector;
	private final Deque<Runnable> cleanup = new ArrayDeque<>();

	@Override
	public void initialize() {
		Registry<Reloadable> reloadables = injector.getInstance(Key.get(new TypeLiteral<Registry<Reloadable>>() {}));
		cleanup.addFirst(() -> reloadables.unregister(injector.getInstance(UsernameConflictsProvider.class)));
		cleanup.addFirst(() -> reloadables.unregister(injector.getInstance(AuthoritativeUsernameMessagesProvider.class)));
		injector.getInstance(UsernameConflictsProvider.class).get();
		injector.getInstance(SchemaBootstrap.class).apply(injector.getInstance(AuthoritativeUsernameSchemaContributor.class));
		EventManager events = injector.getInstance(EventManager.class);
		DefaultAccountUsernameStatePersistenceService state = injector.getInstance(DefaultAccountUsernameStatePersistenceService.class);
		cleanup.addFirst(() -> events.unregister(state));
		events.register(state);
		DefaultAccountUsernameHistoryPersistenceService history = injector.getInstance(DefaultAccountUsernameHistoryPersistenceService.class);
		cleanup.addFirst(() -> events.unregister(history));
		events.register(history);
		ConflictService conflicts = injector.getInstance(ConflictService.class);
		UsernameConflictType conflict = injector.getInstance(UsernameConflictType.class);
		cleanup.addFirst(() -> conflicts.unregister(conflict));
		conflicts.register(conflict);
		PipelineExtensionRegistry pipelines = injector.getInstance(PipelineExtensionRegistry.class);
		AuthoritativeUsernamePipelineExtension extension = injector.getInstance(AuthoritativeUsernamePipelineExtension.class);
		cleanup.addFirst(() -> pipelines.unregister(extension.id()));
		pipelines.register(extension);
	}

	@Override
	public void shutdown() {
		RuntimeException failure = null;
		while (!cleanup.isEmpty()) {
			try {
				cleanup.removeFirst().run();
			} catch (RuntimeException exception) {
				if (failure == null) failure = exception;
				else failure.addSuppressed(exception);
			}
		}
		if (failure != null) throw failure;
	}
}
