package me.whereareiam.identica.feature.recognition.eligibility;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Runtime registry of recognition eligibility rules.
 *
 * <p>Addons may register additional rules after Identica is initialized.</p>
 *
 * <pre>{@code
 * RecognitionEligibilityRegistry registry =
 *         IdenticaAPI.getService(RecognitionEligibilityRegistry.class);
 * registry.register(new CorporateRule());
 * }</pre>
 */
public interface RecognitionEligibilityRegistry {
	/**
	 * Registers or replaces a rule by id.
	 *
	 * @param rule rule to register
	 */
	void register(@NotNull RecognitionEligibilityRule rule);

	/**
	 * Unregisters a rule by id.
	 *
	 * @param ruleId rule id
	 * @return {@code true} when a rule was removed
	 */
	boolean unregister(@NotNull String ruleId);

	/**
	 * Resolves the currently registered rules.
	 *
	 * @return immutable list of registered rules
	 */
	@NotNull List<RecognitionEligibilityRule> resolve();
}
