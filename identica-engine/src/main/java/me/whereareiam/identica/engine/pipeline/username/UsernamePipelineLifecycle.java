package me.whereareiam.identica.engine.pipeline.username;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import org.jetbrains.annotations.NotNull;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;

/**
 * Registers the username phases with the pipelines and removes them on shutdown.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class UsernamePipelineLifecycle implements RuntimeLifecycle {
	private final PipelineExtensionRegistry pipelines;
	private final UsernamePipelineExtension extension;
	private boolean registered;

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.RUNTIME;
	}

	@Override
	public void initialize() {
		registered = true;
		pipelines.register(extension);
	}

	@Override
	public void shutdown() {
		if (!registered) return;
		registered = false;
		pipelines.unregister(extension.id());
	}
}
