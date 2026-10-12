package me.whereareiam.identica.addon.dialogs;

import me.whereareiam.identica.addon.dialogs.config.DialogsFiles;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.addon.dialogs.presentation.DialogPresenter;
import me.whereareiam.identica.addon.dialogs.trigger.DialogTrigger;
import me.whereareiam.identica.addon.dialogs.trigger.TriggerRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The running addon behind {@link Dialogs}: the files' dialogs and rules, the triggers and the presenter.
 */
final class DialogsService implements Dialogs {
	private final DialogsFiles files;
	private final DialogPresenter presenter;
	private final TriggerRegistry triggers;

	DialogsService(@NotNull DialogsFiles files, @NotNull DialogPresenter presenter, @NotNull TriggerRegistry triggers) {
		this.files = files;
		this.presenter = presenter;
		this.triggers = triggers;
	}

	@Override
	public @NotNull Optional<Dialog> dialog(@NotNull String id) {
		return Optional.ofNullable(files.dialogs().get(id));
	}

	@Override
	public @NotNull List<DialogRule> rules() {
		return files.rules();
	}

	@Override
	public void show(
			@NotNull UUID connectionUniqueId,
			@NotNull String dialogId,
			@NotNull Map<String, String> placeholders
	) {
		presenter.show(connectionUniqueId, dialogId, placeholders);
	}

	@Override
	public void clear(@NotNull UUID connectionUniqueId) {
		presenter.clear(connectionUniqueId);
	}

	@Override
	public void register(@NotNull DialogTrigger trigger) {
		triggers.register(trigger);
	}

	@Override
	public void unregister(@NotNull DialogTrigger trigger) {
		triggers.unregister(trigger);
	}
}
