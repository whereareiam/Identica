package me.whereareiam.identica.platform.velocity;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.platform.velocity.mapper.CommandSourceMapper;
import me.whereareiam.keystone.Actor;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.velocity.VelocityCommandManager;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class VelocityCommandManagerProvider implements Provider<CommandManager<Actor>> {
	private final PluginContainer plugin;
	private final ProxyServer proxyServer;
	private final CommandSourceMapper mapper;
	private final Provider<Commands> commandsProvider;
	private CommandManager<Actor> manager;

	@Override
	public CommandManager<Actor> get() {
		if (manager != null) return manager;
		manager = new LifecycleVelocityCommandManager<>(
				plugin,
				proxyServer,
				ExecutionCoordinator.asyncCoordinator(),
				mapper,
				registrationMode()
		);

		return manager;
	}

	private VelocityCommandManager.RegistrationMode registrationMode() {
		return commandsProvider.get().getBehavior().isUseBrigadier()
				? VelocityCommandManager.RegistrationMode.BRIGADIER
				: VelocityCommandManager.RegistrationMode.RAW;
	}
}
