package me.whereareiam.identica.model.provider.selection;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.provider.selection.ProviderClaimSource;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.type.PrepareStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What a {@link ProviderClaimSource} knows about a connection while it is prepared.
 */
@Getter
@ToString
@Builder
public class ProviderClaimRequest {
	/**
	 * Identity of the connection.
	 */
	private final @NotNull ConnectionIdentity identity;
	/**
	 * Preparation stage the claims are collected for. Proof, such as an online login, is only known once the
	 * profile is.
	 */
	private final @NotNull PrepareStage stage;
	/**
	 * Key of the connection, which also finds its pending pipeline, if any.
	 */
	private final @Nullable String connectionKey;
	/**
	 * Subjects the enabled providers resolve for the connection, the highest-priority provider first.
	 */
	@Builder.Default
	private final @NotNull List<SubjectResolution> subjects = List.of();
	/**
	 * Selection of the connection so far, including the claims of the earlier stage.
	 */
	@Builder.Default
	private final @NotNull ProviderSelection selection = ProviderSelection.empty();
}
