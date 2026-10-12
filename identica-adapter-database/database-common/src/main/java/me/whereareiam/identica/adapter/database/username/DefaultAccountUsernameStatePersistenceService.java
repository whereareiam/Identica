package me.whereareiam.identica.adapter.database.username;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.persistence.username.AccountUsernameStatePersistenceService;
import me.whereareiam.identica.adapter.database.mapper.username.UsernameStateMapper;
import me.whereareiam.identica.adapter.database.entity.username.UsernameStateEntity;
import me.whereareiam.identica.model.identity.username.AccountUsernameState;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.identica.type.event.EventOrder;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultAccountUsernameStatePersistenceService implements AccountUsernameStatePersistenceService, EventListener {
	private final Provider<EntityAccess<UsernameStateEntity>> states;
	private final EventManager eventManager;

	@Inject
	void registerListeners() {
		eventManager.register(this);
	}

	@Override
	public @NotNull Optional<AccountUsernameState> find(@NotNull UUID uniqueId) {
		return states.get().find(uniqueId).map(UsernameStateMapper::toModel);
	}

	@Override
	public void save(@NotNull UUID uniqueId, @NotNull AccountUsernameSource source) {
		UsernameStateEntity state = new UsernameStateEntity(uniqueId, source.getId(), System.currentTimeMillis());
		if (states.get().find(uniqueId).isPresent()) {
			states.get().update(state);
			return;
		}

		states.get().insert(state);
	}

	@Override
	public void delete(@NotNull UUID uniqueId) {
		states.get().delete(uniqueId);
	}

	@IdenticEvent(EventOrder.HIGH)
	public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
		UUID uniqueId = event.getIdentity().getAccountUniqueId();
		if (uniqueId == null) return;

		delete(uniqueId);
	}
}
