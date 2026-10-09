package me.whereareiam.identica.trait.authoritative.username.database.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.trait.authoritative.username.database.AccountUsernameStatePersistenceService;
import me.whereareiam.identica.trait.authoritative.username.database.mapper.AuthoritativeUsernameStateMapper;
import me.whereareiam.identica.trait.authoritative.username.database.repository.AuthoritativeUsernameStateRepository;
import me.whereareiam.identica.trait.authoritative.username.model.account.AccountUsernameState;
import me.whereareiam.identica.trait.authoritative.username.type.AccountUsernameSource;
import me.whereareiam.identica.type.event.EventOrder;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultAccountUsernameStatePersistenceService implements AccountUsernameStatePersistenceService, EventListener {
	private final AuthoritativeUsernameStateRepository repository;


	@Override
	public @NotNull Optional<AccountUsernameState> find(@NotNull UUID uniqueId) {
		return repository.findByUniqueId(uniqueId).map(AuthoritativeUsernameStateMapper::toModel);
	}

	@Override
	public void save(@NotNull UUID uniqueId, @NotNull AccountUsernameSource source) {
		long now = System.currentTimeMillis();
		if (repository.findByUniqueId(uniqueId).isPresent()) {
			repository.update(uniqueId, source.getId(), now);
			return;
		}

		repository.insert(uniqueId, source.getId(), now);
	}

	@Override
	public void delete(@NotNull UUID uniqueId) {
		repository.delete(uniqueId);
	}

	@IdenticEvent(EventOrder.HIGH)
	public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
		UUID uniqueId = event.getIdentity().getAccountUniqueId();
		if (uniqueId == null) return;

		delete(uniqueId);
	}
}
