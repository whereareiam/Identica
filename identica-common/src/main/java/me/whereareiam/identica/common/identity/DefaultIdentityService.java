package me.whereareiam.identica.common.identity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.identity.IdentityAttachEvent;
import me.whereareiam.identica.event.identity.IdentityAttachedEvent;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.event.session.SessionOpenedEvent;
import me.whereareiam.identica.identity.IdentityAttachment;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.util.EventUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps the identities attached to this proxy's connections, found by connection and by account. Both lookups are
 * changed together under one lock, so they never disagree; reads take no lock. When an account is attached to a
 * second connection, the lookup by account finds the newest attachment.
 */
@Singleton
public class DefaultIdentityService implements IdentityService, EventListener {
	private final Map<UUID, IdentityAttachment> attachmentsByConnection = new ConcurrentHashMap<>();
	private final Map<UUID, IdentityAttachment> attachmentsByAccount = new ConcurrentHashMap<>();
	/** Held while both lookups change, so they change together. */
	private final Lock attachmentLock = new ReentrantLock();

	@Inject
	public DefaultIdentityService(@NotNull EventManager eventManager) {
		eventManager.register(this);
	}

	@Override
	public void attach(
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId,
			@NotNull Identity identity
	) {
		IdentityAttachEvent attachEvent = new IdentityAttachEvent(identity);
		EventUtil.callEvent(attachEvent);
		if (attachEvent.isCancelled())
			return;

		Identity resolved = attachEvent.getIdentity();
		resolved.setConnectionUniqueId(connectionUniqueId);
		resolved.setAccountUniqueId(accountUniqueId);

		IdentityAttachment attachment = IdentityAttachment.builder()
				.connectionUniqueId(connectionUniqueId)
				.accountUniqueId(accountUniqueId)
				.username(resolved.getUsername())
				.identity(resolved)
				.build();
		store(attachment);

		EventUtil.callEvent(new IdentityAttachedEvent(resolved));
	}

	@Override
	public void detach(@NotNull UUID connectionUniqueId) {
		attachmentLock.lock();
		try {
			forgetAccount(attachmentsByConnection.remove(connectionUniqueId));
		} finally {
			attachmentLock.unlock();
		}

		EventUtil.callEvent(new IdentityDetachedEvent(connectionUniqueId));
	}

	@Override
	public @NotNull Optional<IdentityAttachment> findAttachmentByConnectionUniqueId(@NotNull UUID connectionUniqueId) {
		return Optional.ofNullable(attachmentsByConnection.get(connectionUniqueId));
	}

	@Override
	public @NotNull Optional<IdentityAttachment> findAttachmentByAccountUniqueId(@NotNull UUID accountUniqueId) {
		return Optional.ofNullable(attachmentsByAccount.get(accountUniqueId));
	}

	@Override
	public @NotNull Optional<Identity> findByConnectionUniqueId(@NotNull UUID connectionUniqueId) {
		return findAttachmentByConnectionUniqueId(connectionUniqueId)
				.map(IdentityAttachment::getIdentity);
	}

	@Override
	public @NotNull Optional<Identity> findByAccountUniqueId(@NotNull UUID accountUniqueId) {
		return findAttachmentByAccountUniqueId(accountUniqueId)
				.map(IdentityAttachment::getIdentity);
	}

	@Override
	public @NotNull Optional<Identity> find(@NotNull String username) {
		if (username.isBlank()) return Optional.empty();
		String target = username.trim();

		return attachmentsByConnection.values()
				.stream()
				.map(IdentityAttachment::getIdentity)
				.filter(identity -> identity.getUsername().equalsIgnoreCase(target))
				.findFirst();
	}

	@Override
	public @NotNull Collection<Identity> list() {
		return attachmentsByConnection.values()
				.stream()
				.map(IdentityAttachment::getIdentity)
				.toList();
	}

	@IdenticEvent
	public void onSessionOpened(@NotNull SessionOpenedEvent event) {
		IdentityAttachment attachment = attachmentsByConnection.get(event.getConnectionUniqueId());
		if (attachment == null) return;

		UUID accountUniqueId = event.getSession().getUniqueId();
		if (accountUniqueId.equals(attachment.getAccountUniqueId())) return;

		attach(event.getConnectionUniqueId(), accountUniqueId, attachment.getIdentity());
	}

	private void store(@NotNull IdentityAttachment attachment) {
		attachmentLock.lock();
		try {
			forgetAccount(attachmentsByConnection.put(attachment.getConnectionUniqueId(), attachment));
			if (attachment.getAccountUniqueId() != null)
				attachmentsByAccount.put(attachment.getAccountUniqueId(), attachment);
		} finally {
			attachmentLock.unlock();
		}
	}

	/**
	 * Removes a replaced or detached attachment from the lookup by account, unless a newer connection of the
	 * account has taken its place there. Called holding {@link #attachmentLock}.
	 */
	private void forgetAccount(@Nullable IdentityAttachment replaced) {
		if (replaced == null || replaced.getAccountUniqueId() == null) return;

		attachmentsByAccount.remove(replaced.getAccountUniqueId(), replaced);
	}
}
