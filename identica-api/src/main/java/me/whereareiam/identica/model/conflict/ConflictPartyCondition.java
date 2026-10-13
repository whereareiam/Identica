package me.whereareiam.identica.model.conflict;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.type.provider.ProviderGuarantee;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * What a rule asks of one side of a conflict. An absent condition accepts any side.
 */
@Getter
@Setter
@ToString
public class ConflictPartyCondition {
	/**
	 * Exactly the guarantees the side's provider must declare: an empty set asks for a provider that guarantees
	 * nothing, and {@code null} accepts any.
	 */
	private @Nullable Set<ProviderGuarantee> guarantees;

	/**
	 * Ids of the providers the side may stand on, without regard to case, or {@code null} for any provider.
	 */
	private @Nullable List<String> providers;
}
