/**
 * Copyright 2013-2025 Netshot
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
package net.netshot.netshot.device.access;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.hibernate.Session;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.DeviceDriver.AccessDefinition;
import net.netshot.netshot.device.NetworkAddress;
import net.netshot.netshot.device.credentials.DeviceCredentialSet;
import net.netshot.netshot.utils.HttpsCaTrustMode;
import net.netshot.netshot.work.TaskContext;

/**
 * Generalizes the credential-set fallback logic that used to be triplicated
 * (once each for SSH, Telnet, SNMP) in {@code DeviceScript.connectRun}. One
 * {@code AccessManager} is built per task attempt and shared by every client
 * a driver creates during that attempt (the default legacy CLI/SNMP client,
 * plus anything created via {@code client.create(...)}).
 * <p>
 * Resolution is lazy and per-access: connecting (and iterating candidate
 * credential sets) only happens the first time a given client is actually
 * used, not before the driver script starts running.
 */
@Slf4j
public class AccessManager {

	/**
	 * Builds a {@link Client} for a given access/credential-set pair. Supplied
	 * by the caller (JsCliHelper/JsSnmpHelper/JsHttpHelper), since only they
	 * know how to turn an {@link AccessDefinition} + {@link DeviceCredentialSet}
	 * into a concrete {@code Ssh}/{@code Telnet}/{@code Snmp}/{@code Http}.
	 */
	@FunctionalInterface
	public interface ClientFactory {
		Client build(AccessDefinition accessDef, DeviceCredentialSet credentialSet) throws IOException;
	}

	/** Outcome of a single candidate attempt. */
	public enum AttemptOutcome {
		SUCCESS,
		AUTH_FAILED,
		EXHAUSTED
	}

	private static final class Candidate {
		private final AccessDefinition accessDef;
		private final DeviceCredentialSet credentialSet;
		/** True when this candidate comes from the domain's auto-try pool (no pin configured for this access). */
		private final boolean fromAutoPool;

		Candidate(AccessDefinition accessDef, DeviceCredentialSet credentialSet, boolean fromAutoPool) {
			this.accessDef = accessDef;
			this.credentialSet = credentialSet;
			this.fromAutoPool = fromAutoPool;
		}
	}

	private final Session session;
	@Getter
	private final Device device;
	@Getter
	private final NetworkAddress address;
	@Getter
	private final TaskContext taskContext;
	private final java.util.Set<DeviceCredentialSet> oneTimeCredentialSets;

	/**
	 * Every {@link Resolution} created from this {@code AccessManager} (the default
	 * legacy CLI/SNMP one, plus anything created via {@code client.create(...)}),
	 * so {@link #disconnectAll()} can close whichever of them ended up actually
	 * connecting, once the task attempt is done with them.
	 */
	private final List<Resolution> resolutions = new ArrayList<>();

	/**
	 * Instantiates a new access manager for one task attempt.
	 * @param session the Hibernate session (may be null, e.g. for ad-hoc/test runs)
	 * @param device the device being accessed
	 * @param address the resolved default network address (the device's management
	 *        address) to connect to, used by any access without its own address
	 *        override; may be null if it failed to resolve (see {@link #resolveAddress})
	 * @param taskContext the current task context
	 * @param oneTimeCredentialSets one-time credential sets to try first (may be null/empty)
	 */
	public AccessManager(Session session, Device device, NetworkAddress address, TaskContext taskContext,
			java.util.Set<DeviceCredentialSet> oneTimeCredentialSets) {
		this.session = session;
		this.device = device;
		this.address = address;
		this.taskContext = taskContext;
		this.oneTimeCredentialSets = oneTimeCredentialSets;
	}

	/**
	 * Builds a new access manager for one task attempt against a device, resolving
	 * (and caching, via {@link Device#refreshCachedIpAddress()}) its default
	 * management address along the way - the one entry point production code
	 * should use, so this resolve-and-cache step lives in one place common to
	 * every caller instead of being repeated ad hoc.
	 * @param session the Hibernate session (may be null, e.g. for ad-hoc/test runs)
	 * @param device the device being accessed
	 * @param taskContext the current task context
	 * @param oneTimeCredentialSets one-time credential sets to try first (may be null/empty)
	 * @return the new access manager
	 */
	public static AccessManager forDevice(Session session, Device device, TaskContext taskContext,
			java.util.Set<DeviceCredentialSet> oneTimeCredentialSets) {
		device.refreshCachedIpAddress();
		NetworkAddress address = null;
		InetAddress cachedIp = device.getCachedIpAddress();
		if (cachedIp == null) {
			log.warn("Unable to resolve management address '{}' of device {}.",
				device.getMgmtAddress(), device.getId());
			taskContext.warn("Unable to resolve management address '{}'.", device.getMgmtAddress());
		}
		else {
			try {
				address = NetworkAddress.getNetworkAddress(cachedIp);
			}
			catch (UnknownHostException e) {
				// Unreachable in practice: cachedIp is already a resolved Inet4Address/
				// Inet6Address, which NetworkAddress.getNetworkAddress always accepts.
				log.warn("Unexpected failure wrapping the already-resolved management address '{}' of device {}.",
					device.getMgmtAddress(), device.getId(), e);
			}
		}
		return new AccessManager(session, device, address, taskContext, oneTimeCredentialSets);
	}

	/**
	 * Resolves the effective network address to connect to for a given access:
	 * its own per-access override address if configured (see
	 * {@link Device#getDeviceAccess(String)}), otherwise the device's default
	 * (management) address.
	 * @param accessDef the access to resolve the address for
	 * @return the effective address
	 * @throws IOException if the access has no override and the default address
	 *         itself failed to resolve, or if the override address is malformed
	 */
	public NetworkAddress resolveAddress(AccessDefinition accessDef) throws IOException {
		DeviceAccess access = this.device.getDeviceAccess(accessDef.getName());
		if (access != null && access.getAddress() != null && !access.getAddress().isEmpty()) {
			return NetworkAddress.getNetworkAddress(InetAddress.getByName(access.getAddress()));
		}
		if (this.address == null) {
			throw new UnknownHostException(
				"Unable to resolve an address to connect to for access '" + accessDef.getName() + "'.");
		}
		return this.address;
	}

	/**
	 * Resolves the effective host to connect to for a given access, as
	 * originally configured (IPv4/IPv6 literal or FQDN, not yet DNS-resolved):
	 * its own per-access override address if configured, otherwise the
	 * device's default management address. This is the value transport
	 * clients (SSH/Telnet/HTTP) should connect with, letting the underlying
	 * client library perform DNS resolution itself - unlike {@link #resolveAddress},
	 * this preserves a configured hostname as-is, which also matters for TLS
	 * hostname/certificate validation (a device's certificate is typically
	 * issued for its DNS name, not its resolved IP).
	 * @param accessDef the access to resolve the host for
	 * @return the effective host string
	 * @throws IOException if the access has no override and the device has no
	 *         configured management address
	 */
	public String resolveHost(AccessDefinition accessDef) throws IOException {
		DeviceAccess access = this.device.getDeviceAccess(accessDef.getName());
		if (access != null && access.getAddress() != null && !access.getAddress().isEmpty()) {
			return access.getAddress();
		}
		String mgmtAddress = this.device.getMgmtAddress();
		if (mgmtAddress == null || mgmtAddress.isEmpty()) {
			throw new UnknownHostException(
				"Unable to resolve a host to connect to for access '" + accessDef.getName() + "'.");
		}
		return mgmtAddress;
	}

	/**
	 * Resolves the effective TCP/UDP port to use for a given access: its own
	 * per-access override port if configured, otherwise the access's own
	 * driver-declared default port.
	 * @param accessDef the access to resolve the port for
	 * @return the effective port
	 */
	public int resolvePort(AccessDefinition accessDef) {
		DeviceAccess access = this.device.getDeviceAccess(accessDef.getName());
		if (access != null && access.getPort() != null) {
			return access.getPort();
		}
		return accessDef.getDefaultPort();
	}

	/** Test-only: when set, every {@link Resolution} instantly "resolves" to this client. */
	private Client forcedClientForTest;
	/** Test-only: the credential set reported alongside {@link #forcedClientForTest}. */
	private DeviceCredentialSet forcedCredentialSetForTest;

	/**
	 * Test-only hook: makes every {@link Resolution} created from this
	 * {@code AccessManager} instantly "resolve" to the given pre-built client,
	 * bypassing real connect/credential-fallback logic entirely. Used by unit
	 * tests that supply a fake {@code Cli} implementation.
	 * @param client the pre-built (fake) client
	 * @param credentialSet the credential set to report alongside it
	 */
	public void forceClientForTest(Client client, DeviceCredentialSet credentialSet) {
		this.forcedClientForTest = client;
		this.forcedCredentialSetForTest = credentialSet;
	}

	/**
	 * Disconnects every client that ended up connecting through this {@code AccessManager}
	 * (the default legacy CLI/SNMP one, plus anything created via {@code client.create(...)}
	 * in the driver's JS code) - meant to be called once, by {@code DeviceScript.connectRun},
	 * after the driver script is entirely done with them, regardless of whether it succeeded
	 * or failed. Without this, a successfully-resolved connection is otherwise never
	 * explicitly closed and lingers until the underlying transport's own idle timeout
	 * (e.g. SSH's, ~10mn by default) eventually cleans it up.
	 */
	public void disconnectAll() {
		for (Resolution resolution : this.resolutions) {
			resolution.close();
		}
	}

	/**
	 * Checks whether an error message matches the historical "Authentication
	 * failed" convention (driver-declared CLI {@code fail} mode strings, e.g.
	 * "Authentication failed - Wrong enable password.").
	 * @param message the message to check
	 * @return true if it looks like an in-band authentication failure
	 */
	public static boolean isAuthenticationFailureMessage(String message) {
		return message != null && message.contains("Authentication failed");
	}

	private void waitBetweenAttempts() {
		try {
			Thread.sleep(1000);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Builds the SSH host key verifier for a given access, reflecting its configured
	 * {@link DeviceAccess.SshHostKeyVerification} mode and currently trusted keys (or the
	 * default mode/no trusted keys yet, if the access has no {@link DeviceAccess} row).
	 * @param accessDef the access to build the verifier for
	 * @return the verifier, ready to pass to a new {@link Ssh} instance
	 */
	public DeviceHostKeyVerifier resolveSshHostKeyVerifier(AccessDefinition accessDef) {
		DeviceAccess access = this.device.getDeviceAccess(accessDef.getName());
		DeviceAccess.SshHostKeyVerification mode = access == null ? null : access.getSshHostKeyVerification();
		String storedKeys = access == null ? null : access.getSshTrustedHostKeys();
		return new DeviceHostKeyVerifier(mode, storedKeys, this.taskContext);
	}

	/**
	 * Persists a SSH host key learned via TOFU (see {@link Ssh#getLearnedSshHostKeys()}) back
	 * onto the access's {@link DeviceAccess} row, so later connections check against it. Called
	 * once a connection genuinely succeeds (see {@link Resolution#tryNext()}) - purely an
	 * in-memory entity mutation relying on the ambient Hibernate session/transaction, same as
	 * {@link #pinSuccessfulCredential}.
	 * @param accessDef the access whose host key was learned
	 * @param updatedKeys the new (learned-key-appended) trusted keys block
	 */
	private void recordLearnedSshHostKey(AccessDefinition accessDef, String updatedKeys) {
		try {
			this.device.recordLearnedSshHostKeys(accessDef.getName(), updatedKeys, new java.util.Date());
			this.taskContext.info(
				"New SSH host key accepted and saved for access '{}' - it will be verified on next connections.",
				accessDef.getName());
		}
		catch (Exception e) {
			log.warn("Unable to persist the learned SSH host key for access '{}' on device {}.",
				accessDef.getName(), this.device.getId(), e);
		}
	}

	/**
	 * Applies the access's configured HTTPS TLS trust policy onto a newly built {@link Http}
	 * client. No-op for a non-TLS access (the fields are simply unused by {@link Http} then).
	 * @param accessDef the access
	 * @param http the newly built client to configure
	 */
	public void applyHttpsTrustPolicy(AccessDefinition accessDef, Http http) {
		DeviceAccess access = this.device.getDeviceAccess(accessDef.getName());
		HttpsCaTrustMode mode = access == null ? null : access.getHttpsCaTrustMode();
		String customCa = access == null ? null : access.getHttpsCustomCaCertificate();
		http.applyTrustPolicy(mode, customCa);
	}

	/**
	 * Persists a credential set that successfully authenticated via the
	 * domain's auto-try pool as this access's pinned global credential set,
	 * so subsequent connections use it directly instead of trying the whole
	 * pool again. Called (via {@link Resolution#confirmCredentialWorks()})
	 * once a caller is certain the credentials genuinely worked - not just
	 * that a transport-level connection succeeded (see the protocol-specific
	 * confirmation points in JsCliHelper/JsSnmpHelper/JsHttpHelper).
	 * @param accessDef the access whose credential worked
	 * @param credentialSet the credential set that worked
	 */
	private void pinSuccessfulCredential(AccessDefinition accessDef, DeviceCredentialSet credentialSet) {
		try {
			this.device.pinGlobalCredentialSet(accessDef.getName(), credentialSet);
			this.taskContext.info(
				"Credential set '{}' worked for access '{}' and has been saved as the account to use for this access.",
				credentialSet.getName(), accessDef.getName());
		}
		catch (Exception e) {
			log.warn("Unable to pin the successful credential set '{}' for access '{}' on device {}.",
				credentialSet.getName(), accessDef.getName(), this.device.getId(), e);
		}
	}

	/**
	 * Once an access is known to work, every other access declared in the
	 * same group (e.g. "telnet" once "ssh" succeeded) is no longer needed -
	 * their {@code DeviceAccess} rows (if any) are removed so they won't be
	 * considered/tried anymore. Called (via {@link Resolution#confirmCredentialWorks()})
	 * every time a connection is confirmed to genuinely work, regardless of
	 * whether the winning access was pinned or freshly auto-tried.
	 * @param accessDef the access that is now known to work
	 */
	private void removeSiblingAccesses(AccessDefinition accessDef) {
		try {
			List<AccessDefinition> siblings = this.device.getDeviceDriver().getAccessDefinitionsByGroup(accessDef.getGroup());
			for (AccessDefinition sibling : siblings) {
				if (sibling.getName().equals(accessDef.getName())) {
					continue;
				}
				if (this.device.removeDeviceAccess(sibling.getName())) {
					this.taskContext.info(
						"Access '{}' is no longer needed now that '{}' is known to work, and has been removed.",
						sibling.getName(), accessDef.getName());
				}
			}
		}
		catch (Exception e) {
			log.warn("Unable to remove sibling accesses of '{}' on device {}.", accessDef.getName(), this.device.getId(), e);
		}
	}

	/**
	 * Builds the ordered candidate list for the given accesses. One-time
	 * credential sets (if any were supplied to this whole {@code AccessManager}
	 * - an explicit ad-hoc choice, e.g. a connectivity test) win over
	 * everything else and are tried against every given access regardless of
	 * its configured state - they bypass the "has a row" check entirely,
	 * since testing a credential is independent of whatever is (or isn't)
	 * currently configured. Otherwise, priority per access: no
	 * {@code DeviceAccess} row at all for this access yields zero candidates -
	 * an access is only ever used if it has been explicitly configured (see
	 * {@code Device.getDeviceAccess}) &gt; a per-access credential pin
	 * ({@code DeviceAccess.specificCredentialSet}/{@code globalCredentialSet}
	 * - exactly one candidate, no fallback if it fails) &gt; "auto" (no pin
	 * configured for this access): every credential set in the domain's
	 * auto-try pool ({@link Device#getAutoCredentialSetList}) compatible with
	 * this access's credential family. There is no device-wide fallback layer
	 * - resolution is entirely per access.
	 * @param accessDefs the accesses to build candidates for
	 * @return the ordered list of candidates
	 */
	private List<Candidate> buildCandidates(List<AccessDefinition> accessDefs) {
		List<Candidate> candidates = new ArrayList<>();
		if (this.oneTimeCredentialSets != null && !this.oneTimeCredentialSets.isEmpty()) {
			for (AccessDefinition accessDef : accessDefs) {
				Class<? extends DeviceCredentialSet> credentialClass = accessDef.getCredentialClass();
				for (DeviceCredentialSet cs : this.oneTimeCredentialSets) {
					if (credentialClass.isInstance(cs)) {
						candidates.add(new Candidate(accessDef, cs, false));
					}
				}
			}
			return candidates;
		}
		List<AccessDefinition> enabledAccessDefs = new ArrayList<>();
		for (AccessDefinition accessDef : accessDefs) {
			DeviceAccess access = this.device.getDeviceAccess(accessDef.getName());
			if (access != null) {
				enabledAccessDefs.add(accessDef);
			}
		}
		List<DeviceCredentialSet> autoPool = null;
		for (AccessDefinition accessDef : enabledAccessDefs) {
			Class<? extends DeviceCredentialSet> credentialClass = accessDef.getCredentialClass();
			DeviceAccess access = this.device.getDeviceAccess(accessDef.getName());
			DeviceCredentialSet pinned = null;
			if (access != null) {
				if (access.getSpecificCredentialSet() != null) {
					pinned = access.getSpecificCredentialSet();
				}
				else if (access.getGlobalCredentialSet() != null) {
					pinned = access.getGlobalCredentialSet();
				}
			}
			if (pinned != null) {
				candidates.add(new Candidate(accessDef, pinned, false));
				continue;
			}
			if (autoPool == null) {
				try {
					autoPool = this.session == null ? Collections.emptyList()
						: this.device.getAutoCredentialSetList(this.session);
				}
				catch (Exception e) {
					log.warn("Unable to retrieve the auto-try credential set pool.", e);
					autoPool = Collections.emptyList();
				}
			}
			for (DeviceCredentialSet cs : autoPool) {
				if (credentialClass.isInstance(cs)) {
					candidates.add(new Candidate(accessDef, cs, true));
				}
			}
		}
		return candidates;
	}

	/**
	 * Starts a new resolution process for the given ordered list of accesses.
	 * Tracked in {@link #resolutions} so {@link #disconnectAll()} can close
	 * whatever it ends up connecting to, once the task attempt is done.
	 * @param accessDefs the accesses to try, in order (e.g. [ssh, telnet])
	 * @param factory builds the concrete client for a given access/credential-set pair
	 * @return a fresh, not-yet-resolved {@link Resolution}
	 */
	public Resolution newResolution(List<AccessDefinition> accessDefs, ClientFactory factory) {
		Resolution resolution = new Resolution(accessDefs, factory);
		this.resolutions.add(resolution);
		return resolution;
	}

	/**
	 * Tracks one client's connection/credential-fallback state over the
	 * lifetime of a task attempt. Resolution is lazy: nothing is attempted
	 * until {@link #ensureResolved(boolean)} is first called.
	 */
	public final class Resolution {
		private final List<AccessDefinition> accessDefs;
		private final ClientFactory factory;
		private final List<Candidate> candidates;
		private final boolean hadCandidatesInitially;
		private boolean anyAuthFailureSeen = false;

		private Client currentClient;
		private DeviceCredentialSet currentCredentialSet;
		private AccessDefinition currentAccessDef;
		private boolean currentFromAutoPool;
		/** Guards {@link #confirmCredentialWorks()} so a candidate is only ever pinned once per resolution. */
		private boolean pinned;

		private Resolution(List<AccessDefinition> accessDefs, ClientFactory factory) {
			this.accessDefs = accessDefs;
			this.factory = factory;
			this.candidates = AccessManager.this.buildCandidates(accessDefs);
			this.hadCandidatesInitially = !this.candidates.isEmpty();
		}

		/** @return the currently connected client, or null if not resolved yet. */
		public Client getCurrentClient() {
			return this.currentClient;
		}

		/** @return the credential set that successfully connected, or null if not resolved yet. */
		public DeviceCredentialSet getCurrentCredentialSet() {
			return this.currentCredentialSet;
		}

		/** @return the access definition that successfully connected, or null if not resolved yet. */
		public AccessDefinition getCurrentAccessDef() {
			return this.currentAccessDef;
		}

		/**
		 * Disconnects the currently connected client, if any - called once the task
		 * attempt is entirely done with this resolution (see {@link AccessManager#disconnectAll()}).
		 * A no-op if nothing was ever resolved, or it was already disconnected.
		 */
		private void close() {
			if (this.currentClient == null) {
				return;
			}
			try {
				this.currentClient.disconnect();
			}
			catch (Exception e) {
				log.warn("Error while disconnecting the client for access '{}' on device {}.",
					this.currentAccessDef == null ? "?" : this.currentAccessDef.getName(),
					AccessManager.this.device.getId(), e);
			}
			this.currentClient = null;
		}

		/**
		 * Attempts exactly one candidate (the next one in the list).
		 * @return the outcome of that single attempt
		 * @throws IOException propagated when nothing more can reasonably be tried
		 */
		public AttemptOutcome tryNext() throws IOException {
			if (AccessManager.this.forcedClientForTest != null) {
				this.currentClient = AccessManager.this.forcedClientForTest;
				this.currentCredentialSet = AccessManager.this.forcedCredentialSetForTest;
				this.currentAccessDef = this.accessDefs.isEmpty() ? null : this.accessDefs.get(0);
				this.currentFromAutoPool = false;
				return AttemptOutcome.SUCCESS;
			}
			if (this.candidates.isEmpty()) {
				return AttemptOutcome.EXHAUSTED;
			}
			Candidate candidate = this.candidates.remove(0);
			Client client = null;
			try {
				client = this.factory.build(candidate.accessDef, candidate.credentialSet);
				client.connect();
				this.currentClient = client;
				this.currentCredentialSet = candidate.credentialSet;
				this.currentAccessDef = candidate.accessDef;
				this.currentFromAutoPool = candidate.fromAutoPool;
				if (client instanceof Ssh ssh) {
					String learnedKeys = ssh.getLearnedSshHostKeys();
					if (learnedKeys != null) {
						AccessManager.this.recordLearnedSshHostKey(candidate.accessDef, learnedKeys);
					}
				}
				return AttemptOutcome.SUCCESS;
			}
			catch (InvalidCredentialsException e) {
				this.anyAuthFailureSeen = true;
				AccessManager.this.taskContext.warn("Authentication failed for access '{}' using credentials '{}'.",
					candidate.accessDef.getName(), candidate.credentialSet.getName());
				AccessManager.this.waitBetweenAttempts();
				return AttemptOutcome.AUTH_FAILED;
			}
			catch (IOException e) {
				if (e.getCause() instanceof InvalidCredentialsException) {
					this.anyAuthFailureSeen = true;
					AccessManager.this.taskContext.warn("Authentication failed for access '{}' using credentials '{}'.",
						candidate.accessDef.getName(), candidate.credentialSet.getName());
					AccessManager.this.waitBetweenAttempts();
					return AttemptOutcome.AUTH_FAILED;
				}
				log.warn("Unable to connect to access '{}' on {}.", candidate.accessDef.getName(),
					AccessManager.this.address == null ? "?" : AccessManager.this.address.getIp(), e);
				AccessManager.this.taskContext.warn("Unable to connect to access '{}': {}",
					candidate.accessDef.getName(), e.getMessage());
				// Protocol-level failure (not an auth rejection): abort remaining
				// candidates for this same access, but still allow trying a
				// different declared access (e.g. Telnet after SSH).
				this.candidates.removeIf(c -> c.accessDef == candidate.accessDef);
				return this.tryNext();
			}
			finally {
				if (client != null && this.currentClient != client) {
					client.disconnect();
				}
			}
		}

		/** @return true if there is at least one more candidate to try. */
		public boolean hasNext() {
			return !this.candidates.isEmpty();
		}

		/**
		 * Ensures a client is connected, resolving lazily on first call.
		 * @param autoTryCredentials if true, transparently loops through all
		 *        candidates until one works (today's historical behavior,
		 *        always used for the implicit default/legacy client); if
		 *        false, only the current candidate is tried once, and an
		 *        {@link AccessAuthenticationException} is thrown on failure so
		 *        the driver can catch it and call {@link #tryNextCredentials()}
		 * @return the connected client
		 * @throws IOException if resolution fails
		 */
		public Client ensureResolved(boolean autoTryCredentials) throws IOException {
			if (this.currentClient != null) {
				return this.currentClient;
			}
			if (autoTryCredentials) {
				while (true) {
					AttemptOutcome outcome = this.tryNext();
					if (outcome == AttemptOutcome.SUCCESS) {
						return this.currentClient;
					}
					if (outcome == AttemptOutcome.EXHAUSTED) {
						if (!this.hadCandidatesInitially) {
							throw new InvalidCredentialsException("No credentials configured for this access.");
						}
						if (this.anyAuthFailureSeen) {
							throw new InvalidCredentialsException("Couldn't find valid credentials.");
						}
						throw new IOException("Unable to connect: no reachable access.");
					}
					// AUTH_FAILED: loop transparently to the next candidate.
				}
			}
			AttemptOutcome outcome = this.tryNext();
			if (outcome == AttemptOutcome.SUCCESS) {
				return this.currentClient;
			}
			if (outcome == AttemptOutcome.AUTH_FAILED) {
				throw new AccessAuthenticationException("Authentication failed for this access.");
			}
			throw new InvalidCredentialsException("No credentials available for this access.");
		}

		/**
		 * Manually advances to the next candidate credential set and tries it,
		 * looping through any further candidates transparently until one works
		 * (mirroring auto-try, but starting from wherever the cursor currently
		 * is). Meant to be called from JS after catching an authentication
		 * failure with {@code autoTryCredentials} disabled.
		 * @return true if a new client is now connected and ready to use, false if exhausted
		 */
		public boolean tryNextCredentials() {
			if (this.currentClient != null) {
				this.currentClient.disconnect();
				this.currentClient = null;
				this.currentCredentialSet = null;
				this.currentAccessDef = null;
				this.currentFromAutoPool = false;
			}
			try {
				while (true) {
					if (!this.hasNext()) {
						return false;
					}
					AttemptOutcome outcome = this.tryNext();
					if (outcome == AttemptOutcome.SUCCESS) {
						return true;
					}
					if (outcome == AttemptOutcome.EXHAUSTED) {
						return false;
					}
					// AUTH_FAILED: keep trying the next one.
				}
			}
			catch (IOException e) {
				log.warn("Error while trying the next credentials.", e);
				return false;
			}
		}

		/**
		 * Resets the "resolved" pointer (disconnecting the current client
		 * without touching the remaining candidate list), so that a
		 * subsequent {@link #ensureResolved(boolean)} call resumes from where
		 * the cursor is. Used when a first-use, in-band authentication
		 * failure is detected by the driver's own CLI mode graph (typically
		 * Telnet, where the actual login happens through mode transitions
		 * rather than at the transport layer).
		 */
		public void resetForInBandAuthFailure() {
			if (this.currentClient != null) {
				this.currentClient.disconnect();
			}
			this.anyAuthFailureSeen = true;
			this.currentClient = null;
			this.currentCredentialSet = null;
			this.currentAccessDef = null;
			this.currentFromAutoPool = false;
		}

		/**
		 * Confirms that the currently connected candidate has been fully
		 * authenticated - not just that a transport-level connection succeeded,
		 * but that the credentials are genuinely valid (e.g. after a Telnet
		 * in-band login completes, or after the first HTTP request comes back
		 * with a non-401/403 status). If the current candidate came from the
		 * domain's auto-try pool (i.e. this access has no pin configured), it
		 * is persisted as the access's pinned global credential set so future
		 * connections use it directly. Either way, every other access
		 * declared in the same group is removed, since it's now redundant
		 * (see {@link AccessManager#removeSiblingAccesses}). No-op if nothing
		 * is currently resolved, or a candidate has already been confirmed
		 * during this resolution.
		 */
		public void confirmCredentialWorks() {
			if (this.pinned || this.currentCredentialSet == null || this.currentAccessDef == null) {
				return;
			}
			this.pinned = true;
			if (this.currentFromAutoPool) {
				AccessManager.this.pinSuccessfulCredential(this.currentAccessDef, this.currentCredentialSet);
			}
			AccessManager.this.removeSiblingAccesses(this.currentAccessDef);
		}
	}

}
