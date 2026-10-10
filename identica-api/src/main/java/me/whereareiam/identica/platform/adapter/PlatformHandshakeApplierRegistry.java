package me.whereareiam.identica.platform.adapter;

import me.whereareiam.identica.handshake.HandshakeApplierRegistry;
import me.whereareiam.identica.handshake.HandshakeContext;

/**
 * Platform registry that applies platform-specific handshake instructions.
 *
 * @param <C> handshake context type
 */
public interface PlatformHandshakeApplierRegistry<C extends HandshakeContext> extends HandshakeApplierRegistry<C> {
}
