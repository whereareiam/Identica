package me.whereareiam.identica.platform.adapter;

import me.whereareiam.identica.handshake.HandshakeApplier;
import me.whereareiam.identica.handshake.HandshakeContext;
import me.whereareiam.identica.model.auth.handshake.HandshakeInstruction;
import org.jetbrains.annotations.NotNull;

/**
 * Contributes platform-specific handshake instruction handling behind the
 * platform's handshake applier registry.
 *
 * @param <C> handshake context type
 */
public interface PlatformHandshakeApplierContributor<C extends HandshakeContext> extends HandshakeApplier<C> {
	/**
	 * Determines whether this contributor should currently apply the given instruction.
	 *
	 * @param instruction handshake instruction being applied
	 * @return {@code true} when the contributor should run
	 */
	default boolean supports(@NotNull HandshakeInstruction instruction) {
		return true;
	}
}
