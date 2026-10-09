package me.whereareiam.identica.platform.bungeecord;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.platform.bungeecord.mapper.CommandSourceMapper;
import me.whereareiam.keystone.Actor;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.execution.ExecutionCoordinator;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class BungeeCordCommandManagerProvider implements Provider<CommandManager<Actor>> {
	private final BungeeCordIdentica plugin;
	private final CommandSourceMapper mapper;
	private final Provider<Commands> commandsProvider;
	private CommandManager<Actor> manager;

	@Override
	public CommandManager<Actor> get() {
		if (manager != null) return manager;
		manager = new LifecycleBungeeCommandManager<>(
				plugin,
				ExecutionCoordinator.asyncCoordinator(),
				mapper
		);

		return manager;
	}
}
