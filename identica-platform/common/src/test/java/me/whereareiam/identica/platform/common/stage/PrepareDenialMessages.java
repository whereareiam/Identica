package me.whereareiam.identica.platform.common.stage;

import me.whereareiam.identica.model.config.Messages;

import java.util.List;

/**
 * Messages whose only content is the prepare denial message, the fallback for a denial without text.
 */
public final class PrepareDenialMessages {
	private PrepareDenialMessages() {
	}

	/**
	 * Returns messages whose prepare denial message is {@code refused}, then {@code contact us}.
	 */
	public static Messages messages() {
		Messages.Engine.Prepare prepare = new Messages.Engine.Prepare();
		prepare.setHandshakeDenied(List.of("refused", "contact us"));
		Messages.Engine engine = new Messages.Engine();
		engine.setPrepare(prepare);
		Messages messages = new Messages();
		messages.setEngine(engine);
		return messages;
	}
}
