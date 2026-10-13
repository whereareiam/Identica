package me.whereareiam.identica.common.config.provider;

import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.common.conflict.DefaultConflictService;
import me.whereareiam.identica.conflict.ConflictOutcome;
import me.whereareiam.identica.model.config.UsernameConflicts;
import me.whereareiam.identica.model.conflict.ConflictKind;
import me.whereareiam.identica.model.identity.username.UsernameConflict;
import me.whereareiam.identica.model.identity.username.UsernameConflictDecision;
import me.whereareiam.identica.model.identity.username.UsernameConflictRule;
import me.whereareiam.identica.model.identity.username.UsernameRename;
import me.whereareiam.identica.type.identity.username.UsernameConflictTrigger;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import me.whereareiam.identica.type.provider.ProviderGuarantee;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("Username Conflicts Provider")
class UsernameConflictsProviderTest {
	@TempDir
	Path directory;

	@DisplayName("Writes the defaults on a new installation, with each rule naming only its outcome, and reads them back")
	@Test
	void writesAndReadsTheDefaults() throws IOException {
		UsernameConflicts written = provider().get();
		String document = Files.readString(directory.resolve("conflicts.yml"));

		UsernameConflicts read = provider().get();

		assertEquals(4, written.getRules().size());
		assertEquals(written.toString(), read.toString());
		assertTrue(document.contains("outcome: \"rename-holder\""), document);
		assertTrue(document.contains("holderOnline: \"KEEP_PLAYING\""), document);
		assertEquals(1, occurrences(document, "holderOnline"), document);
		assertEquals(2, occurrences(document, "pattern"), document);
		assertNull(read.getRules().get(3).getArriving().getGuarantees());
		assertEquals(Set.of(), read.getRules().get(2).getArriving().getGuarantees());
	}

	@DisplayName("Reads rules an administrator wrote, with settings a rule and a provider change")
	@Test
	void readsWrittenRules() throws IOException {
		Files.writeString(directory.resolve("conflicts.yml"), """
				providers:
				  alpha:
				    rename-holder:
				      pattern: "{username}_offline"
				rules:
				  - arriving:
				      providers: ["alpha"]
				      guarantees: ["OWNED_USERNAME"]
				    holder:
				      guarantees: []
				    trigger: FIRST_JOIN
				    arrivedVia: HOSTNAME
				    outcome:
				      id: rename-holder
				      with:
				        holderOnline: KICK_HOLDER
				""");

		UsernameConflicts conflicts = provider().get();
		UsernameConflictRule rule = conflicts.getRules().getFirst();

		assertEquals(List.of("alpha"), rule.getArriving().getProviders());
		assertEquals(Set.of(ProviderGuarantee.OWNED_USERNAME), rule.getArriving().getGuarantees());
		assertEquals(Set.of(), rule.getHolder().getGuarantees());
		assertEquals(UsernameConflictTrigger.FIRST_JOIN, rule.getTrigger());
		assertEquals(ProviderClaimOrigin.HOSTNAME, rule.getArrivedVia());
		assertEquals("rename-holder", rule.getOutcome().getId());
		assertEquals("KICK_HOLDER", rule.getOutcome().getWith().path("holderOnline").asText());
		assertEquals("{username}_offline", conflicts.getProviders().get("alpha").get("rename-holder").path("pattern").asText());
		assertFalse(Files.readString(directory.resolve("conflicts.yml")).contains("tooLong: \"SHORTEN\"\n        holderOnline"));
	}

	@DisplayName("Writes the defaults of an outcome registered later, and keeps settings already written")
	@Test
	void includesRegisteredOutcomes() throws IOException {
		UsernameConflictsProvider provider = provider();
		provider.get();

		provider.include(new Contributed());
		provider.include(new Contributed());

		String document = Files.readString(directory.resolve("conflicts.yml"));
		assertTrue(document.contains("longest-playtime:"), document);
		assertEquals("{username}_long", provider().get().getOutcomes().get("longest-playtime").path("pattern").asText());
	}

	private static int occurrences(String text, String part) {
		return text.split(part, -1).length - 1;
	}

	@SuppressWarnings("unchecked")
	private UsernameConflictsProvider provider() {
		return new UsernameConflictsProvider(directory, mock(Registry.class), () -> new DefaultConflictService(Set::of));
	}

	private static final class Contributed implements ConflictOutcome<UsernameConflict, UsernameConflictDecision, UsernameRename> {
		@Override
		public @NotNull ConflictKind<UsernameConflict, UsernameConflictDecision> kind() {
			return UsernameConflict.KIND;
		}

		@Override
		public @NotNull String id() {
			return "longest-playtime";
		}

		@Override
		public @NotNull Class<UsernameRename> settingsType() {
			return UsernameRename.class;
		}

		@Override
		public @NotNull UsernameRename defaults() {
			UsernameRename rename = new UsernameRename();
			rename.setPattern("{username}_long");
			return rename;
		}

		@Override
		public @NotNull UsernameConflictDecision decide(@NotNull UsernameConflict conflict, @NotNull UsernameRename settings) {
			return UsernameConflictDecision.renameArriving(settings);
		}
	}
}
