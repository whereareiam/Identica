package me.whereareiam.identica.example.provider;

import com.google.inject.Inject;
import com.google.inject.Module;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.example.provider.command.ExamplePassphraseCommand;
import me.whereareiam.identica.example.provider.pipeline.ExamplePipelineExtension;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.CommandDefinition;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.provider.IdenticaProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The provider of the developer documentation: a player registers and logs in by typing a passphrase the
 * administrator chose. Identica creates the account, links it to this provider and opens the session; the
 * provider only decides whether the player may continue.
 *
 * <p>Identica creates the provider twice: once through the constructor without parameters to read what it
 * declares, then through its injector with the services it uses.</p>
 */
@NoArgsConstructor
@AllArgsConstructor(onConstructor_ = @Inject)
public class ExampleProvider extends IdenticaProvider {
	public static final String ID = "example";
	public static final String PASSPHRASE_COMMAND = "example-passphrase";

	private CommandService commandService;
	private PipelineExtensionRegistry pipelineExtensionRegistry;
	private ExamplePipelineExtension pipelineExtension;
	private ExamplePassphraseCommand passphraseCommand;
	private MessageService messageService;

	private final List<MessageService.Registration> messageRegistrations = new ArrayList<>();

	@Override
	public @NotNull List<Module> modules() {
		return List.of(new ExampleModule());
	}

	@Override
	public void onEnable() {
		messageRegistrations.add(messageService.register(ExampleMessageDefinitions.all()));
		pipelineExtensionRegistry.register(pipelineExtension);
		commandService.registerCommandInstances(Map.of(PASSPHRASE_COMMAND, passphraseDefinition()), passphraseCommand);
	}

	@Override
	public void onDisable() {
		commandService.unregisterCommands(Set.of(PASSPHRASE_COMMAND));
		pipelineExtensionRegistry.unregister(pipelineExtension.id());
		messageRegistrations.forEach(MessageService.Registration::remove);
		messageRegistrations.clear();
	}

	/**
	 * A provider may keep its command definitions in a commands document administrators can edit; this one
	 * keeps its single command in code.
	 */
	private static CommandDefinition passphraseDefinition() {
		return CommandDefinition.builder()
				.aliases(List.of("passphrase"))
				.description("Authenticate with the network's passphrase")
				.usage("{alias} <passphrase>")
				.arguments(Map.of("passphrase", "Passphrase"))
				.hide(true)
				.build();
	}
}
