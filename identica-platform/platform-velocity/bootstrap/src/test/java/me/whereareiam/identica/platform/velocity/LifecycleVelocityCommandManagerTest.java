package me.whereareiam.identica.platform.velocity;

import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import org.incendo.cloud.CloudCapability;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LifecycleVelocityCommandManagerTest {
	private com.velocitypowered.api.command.CommandManager platform;
	private ProxyServer proxy;
	private final Map<String, CommandMeta> metadata = new HashMap<>();
	private final Map<String, BrigadierCommand> commands = new HashMap<>();

	@BeforeEach
	void setUp() {
		platform = mock(com.velocitypowered.api.command.CommandManager.class);
		proxy = mock(ProxyServer.class);
		when(proxy.getCommandManager()).thenReturn(platform);
		when(proxy.getEventManager()).thenReturn(mock(EventManager.class));
		when(platform.metaBuilder(any(BrigadierCommand.class))).thenAnswer(invocation -> {
			BrigadierCommand command = invocation.getArgument(0);
			Set<String> aliases = new LinkedHashSet<>();
			aliases.add(command.getNode().getName());
			CommandMeta.Builder builder = mock(CommandMeta.Builder.class);
			when(builder.aliases(any(String[].class))).thenAnswer(call -> {
				aliases.addAll(Arrays.asList((String[]) call.getRawArguments()[0]));
				return builder;
			});
			CommandMeta meta = mock(CommandMeta.class);
			when(meta.getAliases()).thenReturn(aliases);
			when(builder.build()).thenReturn(meta);
			return builder;
		});
		doAnswer(invocation -> {
			CommandMeta meta = invocation.getArgument(0);
			BrigadierCommand command = invocation.getArgument(1);
			for (String alias : meta.getAliases()) {
				metadata.put(alias, meta);
				commands.put(alias, command);
			}
			return null;
		}).when(platform).register(any(CommandMeta.class), any(com.velocitypowered.api.command.Command.class));
		when(platform.getCommandMeta(anyString())).thenAnswer(invocation -> metadata.get(invocation.getArgument(0)));
		doAnswer(invocation -> {
			String alias = invocation.getArgument(0);
			metadata.remove(alias);
			commands.remove(alias);
			return null;
		}).when(platform).unregister(anyString());
		doAnswer(invocation -> {
			CommandMeta meta = invocation.getArgument(0);
			for (String alias : meta.getAliases())
				if (metadata.remove(alias, meta)) commands.remove(alias);
			return null;
		}).when(platform).unregister(any(CommandMeta.class));
	}

	@ParameterizedTest
	@EnumSource(VelocityCommandManager.RegistrationMode.class)
	void deletesNativeAliasesAndRebuildsOnlyRetainedBranches(VelocityCommandManager.RegistrationMode mode) {
		var manager = manager(mode);
		manager.command(manager.commandBuilder("identica", "id").literal("feature").handler(context -> {}));
		var retained = manager.commandBuilder("identica", "id").literal("login").handler(context -> {}).build();
		manager.command(retained);
		manager.command(manager.commandBuilder("other").handler(context -> {}));
		assertTrue(manager.hasCapability(CloudCapability.StandardCapabilities.ROOT_COMMAND_DELETION));
		assertNotNull(manager.brigadierManager());
		assertTrue(commands.containsKey("id"));

		manager.deleteRootCommand("ID");

		assertEquals(Set.of("other"), commands.keySet());
		assertNull(manager.commandTree().getNamedNode("identica"));
		manager.command(retained);
		assertNotNull(commands.get("id").getNode().getChild("login"));
		assertNull(commands.get("id").getNode().getChild("feature"));
		manager.deleteRootCommand("identica");
		manager.deleteRootCommand("identica");
		assertEquals(Set.of("other"), commands.keySet());
	}

	@ParameterizedTest
	@EnumSource(VelocityCommandManager.RegistrationMode.class)
	void removesEveryRootAliasAndPreservesAnExternalReplacement(VelocityCommandManager.RegistrationMode mode) {
		var manager = manager(mode);
		manager.command(manager.commandBuilder("verify", "v", "verification").handler(context -> {}));
		CommandMeta external = mock(CommandMeta.class);
		metadata.put("v", external);
		BrigadierCommand externalCommand = mock(BrigadierCommand.class);
		commands.put("v", externalCommand);

		manager.deleteRootCommand("verify");

		assertEquals(Map.of("v", externalCommand), commands);
		assertEquals(Map.of("v", external), metadata);
		assertTrue(manager.commands().isEmpty());
	}

	@ParameterizedTest
	@EnumSource(VelocityCommandManager.RegistrationMode.class)
	void doesNotClaimUnchangedForeignMetadataDuringRegistration(VelocityCommandManager.RegistrationMode mode) {
		CommandMeta external = mock(CommandMeta.class);
		// A platform lookup is not proof of ownership. Model a foreign alias that
		// reports the same metadata before and after Cloud registers its root.
		when(platform.getCommandMeta("v")).thenReturn(external);
		var manager = manager(mode);
		manager.command(manager.commandBuilder("verify", "v").handler(context -> {}));

		manager.deleteRootCommand("verify");

		verify(platform, never()).unregister(external);
		assertTrue(manager.commands().isEmpty());
	}

	private LifecycleVelocityCommandManager<CommandSource> manager(VelocityCommandManager.RegistrationMode mode) {
		return new LifecycleVelocityCommandManager<>(
				mock(PluginContainer.class),
				proxy,
				ExecutionCoordinator.simpleCoordinator(),
				SenderMapper.identity(),
				mode
		);
	}
}
