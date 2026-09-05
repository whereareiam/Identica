package me.whereareiam.identica.platform.bungeecord;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.api.plugin.TabExecutor;
import org.incendo.cloud.CloudCapability;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LifecycleBungeeCommandManagerTest {
	private Plugin plugin;
	private PluginManager platform;
	private LifecycleBungeeCommandManager<CommandSender> manager;
	private CommandSender sender;

	@BeforeEach
	void setUp() {
		ProxyServer proxy = mock(ProxyServer.class);
		when(proxy.getLogger()).thenReturn(Logger.getLogger(getClass().getName()));
		platform = new PluginManager(proxy);
		when(proxy.getPluginManager()).thenReturn(platform);
		plugin = mock(Plugin.class);
		when(plugin.getProxy()).thenReturn(proxy);
		manager = new LifecycleBungeeCommandManager<>(plugin, ExecutionCoordinator.simpleCoordinator(), SenderMapper.identity());
		sender = mock(CommandSender.class);
		when(sender.hasPermission(anyString())).thenReturn(true);
	}

	@Test
	void deletesNativeRootAndAliasesThenRebuildsOnlyRetainedBranches() {
		AtomicInteger executions = new AtomicInteger();
		manager.command(manager.commandBuilder("identica", "id").literal("feature").handler(context -> fail("removed feature")));
		var retained = manager.commandBuilder("identica", "id").literal("login")
				.permission("provider.login").handler(context -> executions.incrementAndGet()).build();
		manager.command(retained);
		manager.command(manager.commandBuilder("other").handler(context -> {}));
		assertTrue(manager.hasCapability(CloudCapability.StandardCapabilities.ROOT_COMMAND_DELETION));
		assertTrue(commands().containsKey("id"));

		manager.deleteRootCommand("ID");

		assertEquals(List.of("other"), commands().keySet().stream().toList());
		assertNull(manager.commandTree().getNamedNode("identica"));
		manager.command(retained);
		Command rebuilt = commands().get("id");
		assertNotNull(rebuilt);
		assertTrue(rebuilt.hasPermission(sender));
		rebuilt.execute(sender, new String[]{"login"});
		assertEquals(1, executions.get());
		assertEquals(List.of("login"), ((TabExecutor) rebuilt).onTabComplete(sender, new String[]{""}));
		when(sender.hasPermission("provider.login")).thenReturn(false);
		assertFalse(rebuilt.hasPermission(sender));
	}

	@Test
	void replacesWrapperWhenAnotherBranchAddsRootAliasesAndCleansThemAll() {
		manager.command(manager.commandBuilder("identica", "id").literal("first").handler(context -> {}));
		Command original = commands().get("id");
		manager.command(manager.commandBuilder("identica", "i").literal("second").handler(context -> {}));
		assertNotSame(original, commands().get("id"));
		assertSame(commands().get("identica"), commands().get("i"));

		manager.deleteRootCommand("identica");
		manager.deleteRootCommand("identica");

		assertTrue(commands().isEmpty());
		assertTrue(manager.commands().isEmpty());
	}

	@Test
	void preservesAliasReplacedByAnotherPlugin() {
		manager.command(manager.commandBuilder("verify", "v").handler(context -> {}));
		Command external = mock(Command.class);
		when(external.getName()).thenReturn("v");
		when(external.getAliases()).thenReturn(new String[0]);
		platform.registerCommand(mock(Plugin.class), external);

		manager.deleteRootCommand("verify");

		assertEquals(Map.of("v", external), commands());
	}

	private Map<String, Command> commands() {
		return platform.getCommands().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}
}
