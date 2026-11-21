/**
 * Copyright 2013-present Netshot
 * 
 * This file is part of Netshot project.
 * 
 * Netshot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Netshot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Netshot.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.netshot.netshot.vault;

/**
 * A cached Vault client token, obtained via AppRole or JWT login.
 * @param clientToken the Vault client token to present as X-Vault-Token
 * @param obtainedAt the epoch millis at which the token was obtained
 * @param expiresAt the epoch millis at which this token should be considered expired ({@link #NEVER} if it doesn't expire)
 */
public record VaultToken(String clientToken, long obtainedAt, long expiresAt) {

	/** Expiry of a token without lease (lease duration 0). */
	public static final long NEVER = Long.MAX_VALUE;

	/**
	 * Creates a token from the lease Vault gave at the login.
	 * @param clientToken the Vault client token
	 * @param leaseSeconds the lease duration (s), 0 for a token which doesn't expire
	 * @return the token
	 */
	public static VaultToken of(String clientToken, long leaseSeconds) {
		long now = System.currentTimeMillis();
		return new VaultToken(clientToken, now, leaseSeconds <= 0 ? NEVER : now + leaseSeconds * 1000);
	}

	/**
	 * Whether the token can still be used, i.e. isn't within the renew margin of its expiry.
	 * The margin is capped to half the lease, so that a lease shorter than the margin is still used.
	 * @param renewMarginMs the margin (ms) before the expiry at which the token is renewed
	 * @return true if the token can be used
	 */
	public boolean isValid(long renewMarginMs) {
		if (this.expiresAt == NEVER) {
			return true;
		}
		long margin = Math.min(renewMarginMs, (this.expiresAt - this.obtainedAt) / 2);
		return System.currentTimeMillis() < this.expiresAt - margin;
	}
}
