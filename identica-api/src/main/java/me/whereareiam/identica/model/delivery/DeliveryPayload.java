package me.whereareiam.identica.model.delivery;

import lombok.*;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class DeliveryPayload {
	/**
	 * Message shown once the player can see it, through {@code MessageService}.
	 */
	private @Nullable PlayerMessage message;
	/**
	 * Whether only the message's chat waits, because its other parts were shown when it was sent, such as chat held
	 * back on the loading screen while a dialog showed the message.
	 */
	private boolean chatOnly;
	private @Nullable CompletionPayload completion;

	public boolean isEmpty() {
		return (message == null || message.isEmpty()) && completion == null;
	}

	@Getter
	@ToString
	@NoArgsConstructor
	@AllArgsConstructor
	@Builder(toBuilder = true)
	public static class CompletionPayload {
		private @Nullable UUID connectionUniqueId;
		private @Nullable UUID accountUniqueId;
		private @Nullable PipelineType pipelineType;
	}
}
