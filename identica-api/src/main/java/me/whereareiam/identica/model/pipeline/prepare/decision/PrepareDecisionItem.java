package me.whereareiam.identica.model.pipeline.prepare.decision;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.whereareiam.identica.model.pipeline.prepare.PrepareContextItem;
import me.whereareiam.identica.pipeline.state.PipelineStateItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PrepareDecisionItem implements PipelineStateItem {
	private @Nullable UUID accountUniqueId;
	private @Nullable String effectiveUsername;

	private @Nullable PrepareContextItem context;
	private @Nullable PrepareDecision.Status status;

	private @Nullable String denialMessage;

	public @Nullable PrepareDecision toDecision() {
		if (status == null) return null;
		return PrepareDecision.builder()
				.status(status)
				.denialMessage(denialMessage)
				.handshake(context != null ? context.getHandshake() : null)
				.accountUniqueId(accountUniqueId)
				.effectiveUsername(effectiveUsername)
				.provider(context != null ? context.getProvider() : null)
				.build();
	}

	public static @NotNull PrepareDecisionItem allow(
			@Nullable PrepareContextItem context,
			@Nullable UUID accountUniqueId,
			@Nullable String effectiveUsername
	) {
		return new PrepareDecisionItem(
				accountUniqueId,
				effectiveUsername,
				context,
				PrepareDecision.Status.ALLOW,
				null
		);
	}

	public static @NotNull PrepareDecisionItem deny(
			@Nullable String denialMessage,
			@Nullable PrepareContextItem context,
			@Nullable UUID accountUniqueId,
			@Nullable String effectiveUsername
	) {
		return new PrepareDecisionItem(
				accountUniqueId,
				effectiveUsername,
				context,
				PrepareDecision.Status.DENY,
				denialMessage
		);
	}
}
