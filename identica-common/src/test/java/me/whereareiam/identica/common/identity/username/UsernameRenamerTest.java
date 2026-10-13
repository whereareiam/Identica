package me.whereareiam.identica.common.identity.username;

import me.whereareiam.identica.model.identity.username.AccountUsername;
import me.whereareiam.identica.model.identity.username.UsernameRename;
import me.whereareiam.identica.persistence.username.AccountUsernamePersistenceService;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.identica.type.identity.username.UsernameRenameTaken;
import me.whereareiam.identica.type.identity.username.UsernameRenameTooLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Username Renamer")
class UsernameRenamerTest {
	private static final UUID ACCOUNT = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private final AccountUsernamePersistenceService usernames = mock(AccountUsernamePersistenceService.class);
	private final Map<String, UUID> holders = new TreeMap<>();
	private final UsernameRenamer renamer = new UsernameRenamer(usernames);
	private final UsernameRename settings = new UsernameRename();

	@BeforeEach
	void holdNames() {
		when(usernames.findHolder(anyString())).thenAnswer(call -> {
			String name = call.getArgument(0, String.class);
			UUID holder = holders.get(name.toLowerCase(Locale.ROOT));
			return Optional.ofNullable(holder).map(uniqueId -> new AccountUsername(uniqueId, name, AccountUsernameSource.PROVIDER, null, 0L));
		});
	}

	@DisplayName("Fills the pattern in with the username and the provider id")
	@Test
	void fillsThePatternIn() {
		assertEquals("Steve_alpha", rename("Steve", "alpha"));
	}

	@DisplayName("Leaves out characters Minecraft does not allow in a username")
	@Test
	void leavesDisallowedCharactersOut() {
		assertEquals("Steve_alphaone", rename("Steve", "alpha-one"));
	}

	@DisplayName("Shortens the username until the renamed one fits in 16 characters")
	@Test
	void shortensTooLongNames() {
		assertEquals("Christophe_alpha", rename("Christopher", "alpha"));
	}

	@DisplayName("Refuses a renamed name longer than 16 characters when shortening is off")
	@Test
	void refusesTooLongNames() {
		settings.setTooLong(UsernameRenameTooLong.REFUSE);

		assertNull(rename("Christopher", "alpha"));
	}

	@DisplayName("Adds a number from 2 to 9 to a held name, and fails once all of them are held")
	@Test
	void numbersHeldNames() {
		holders.put("steve_alpha", OTHER);
		assertEquals("Steve_alpha2", rename("Steve", "alpha"));

		for (int number = 2; number <= 9; number++)
			holders.put("steve_alpha" + number, OTHER);
		assertNull(rename("Steve", "alpha"));
	}

	@DisplayName("Keeps a numbered name within 16 characters")
	@Test
	void shortensNumberedNames() {
		holders.put("christophe_alpha", OTHER);

		assertEquals("Christoph_alpha2", rename("Christopher", "alpha"));
	}

	@DisplayName("Refuses a held name when numbering is off, but not a name the account holds itself")
	@Test
	void refusesHeldNames() {
		settings.setTaken(UsernameRenameTaken.REFUSE);
		holders.put("steve_alpha", ACCOUNT);
		assertEquals("Steve_alpha", rename("Steve", "alpha"));

		holders.put("steve_alpha", OTHER);
		assertNull(rename("Steve", "alpha"));
	}

	@DisplayName("Skips names given to the other side of the same conflict")
	@Test
	void skipsReservedNames() {
		assertEquals("Steve_alpha2", renamer.rename("Steve", "alpha", settings, ACCOUNT, Set.of("STEVE_ALPHA")));
	}

	private String rename(String username, String providerId) {
		return renamer.rename(username, providerId, settings, ACCOUNT, Set.of());
	}
}
