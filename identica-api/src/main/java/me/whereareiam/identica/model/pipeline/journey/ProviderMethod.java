package me.whereareiam.identica.model.pipeline.journey;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * One way a provider offers to get a scenario done, such as signing a player in with a password or migrating an
 * account without asking anything. Identica does not know what a method does: it offers the methods of every
 * eligible provider as options, picks one or lets the player pick (see {@code engine.yml}'s {@code choice}), and runs
 * its steps. Whether a method asks the player for anything shows only in what its steps return.
 *
 * <p>A provider lists its methods in the order it prefers them; an admin may reorder or leave out methods by name in
 * the provider's entry of {@code providers/providers}. When a method cannot get the scenario done, a step of it
 * fails and the journey continues with the next option.</p>
 *
 * <pre>{@code
 * builder.registerMethod(ProviderMethod.builder()
 *         .providerId("website")
 *         .name("quick")
 *         .scenario(PipelineType.MIGRATION)
 *         .step(lookupStep)
 *         .build());
 * builder.registerMethod(ProviderMethod.builder()
 *         .providerId("website")
 *         .name("guided")
 *         .scenario(PipelineType.MIGRATION)
 *         .label(() -> messages.get().getGuidedLabel())
 *         .step(lookupStep)
 *         .step(setupStep)
 *         .build());
 * }</pre>
 *
 * <p>Steps a provider registers without a method run in each of its methods, ordered with the method's steps by
 * their order. A provider that declares no method for a scenario runs all its steps as one option.</p>
 */
@Getter
@ToString
@Builder(toBuilder = true)
public class ProviderMethod {
	/**
	 * ID of the provider offering the method.
	 */
	private final @NotNull String providerId;
	/**
	 * Name of the method, unique within its provider and scenario, such as {@code password}.
	 */
	private final @NotNull String name;
	/**
	 * Scenario the method gets done.
	 */
	private final @NotNull PipelineType scenario;
	/**
	 * Steps of the method, in no particular order: they run by their order together with the provider's other steps.
	 */
	@Singular
	private final @NotNull List<Step> steps;
	/**
	 * Whether the method can be offered for a connection.
	 */
	@Builder.Default
	private final @NotNull Availability availability = Availability.ALWAYS;
	/**
	 * Text naming the method where the player picks an option, or {@code null} to name it by its provider only.
	 */
	private final @Nullable Supplier<String> label;

	/**
	 * Returns the ID of the method, {@code <provider>:<name>}, such as {@code credential:password}.
	 *
	 * @return method ID, in lower case
	 */
	public @NotNull String id() {
		return (providerId + ":" + name).toLowerCase(Locale.ROOT);
	}

	/**
	 * Decides whether a method can be offered for a connection, such as only to a player whose name a website knows.
	 * An unavailable method is neither offered to the player nor tried.
	 */
	@FunctionalInterface
	public interface Availability {
		/**
		 * A method that can always be offered.
		 */
		Availability ALWAYS = context -> true;

		/**
		 * Tells whether the method can be offered.
		 *
		 * @param context scenario context of the connection
		 * @return {@code true} when the method can be offered
		 */
		boolean isAvailable(@NotNull ScenarioContext context);
	}
}
