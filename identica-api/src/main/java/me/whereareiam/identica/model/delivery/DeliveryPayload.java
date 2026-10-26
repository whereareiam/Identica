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
