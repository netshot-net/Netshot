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
package net.netshot.netshot.device.script.helper;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.graalvm.polyglot.HostAccess.Export;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.DeviceDriver.AccessDefinition;
import net.netshot.netshot.device.DeviceDriver.DriverProtocol;
import net.netshot.netshot.device.access.AccessManager;
import net.netshot.netshot.device.access.AccessManager.Resolution;
import net.netshot.netshot.device.access.Cli;
import net.netshot.netshot.device.access.Cli.WithBufferIOException;
import net.netshot.netshot.device.access.Client;
import net.netshot.netshot.device.access.Ssh;
import net.netshot.netshot.device.access.Ssh.SshConfig;
import net.netshot.netshot.device.access.Telnet;
import net.netshot.netshot.device.access.Telnet.TelnetConfig;
import net.netshot.netshot.device.credentials.DeviceCliAccount;
import net.netshot.netshot.device.credentials.DeviceCredentialSet;
import net.netshot.netshot.device.credentials.DeviceSshAccount;
import net.netshot.netshot.device.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.vault.VaultException;
import net.netshot.netshot.vault.VaultManager;
import net.netshot.netshot.vault.VaultableSecret;
import net.netshot.netshot.work.TaskContext;

/**
 * This class is used to pass CLI control to JavaScript.
 * <p>
 * Connection (and credential-set fallback) is lazy: nothing happens until the
 * first {@code send}/{@code macro}/{@code command} call actually needs it -
 * see {@link #ensureResolved()}.
 */
@Slf4j
public class JsCliHelper {

	private final AccessManager accessManager;
	private final Resolution resolution;
	private final boolean autoTryCredentials;
	private final TaskContext taskContext;
	/** {@code client.create(...)} "ssh" advanced option (a delta, see {@link DeviceDriver#parseSshConfig}), or null. */
	private final SshConfig sshConfigOverride;
	/** {@code client.create(...)} "telnet" advanced option (a delta, see {@link DeviceDriver#parseTelnetConfig}), or null. */
	private final TelnetConfig telnetConfigOverride;

	/** The resolved CLI object, once connected. */
	private Cli cli;
	/** The resolved CLI account (SSH/Telnet), once connected. */
	private DeviceCliAccount account;
	/** An error was raised. */
	private boolean errored;
	/** The last output from a send command. */
	private Cli.CommandOutput lastOutput;

	/**
	 * Instantiate a new JsCliHelper object.
	 * @param accessManager the access manager (shared across all clients of this task attempt)
	 * @param accessDefs the ordered list of CLI accesses to try (e.g. [ssh, telnet])
	 * @param autoTryCredentials whether to transparently loop through all candidate
	 *        credential sets (true, historical behavior) or stop at the first
	 *        failure and let the driver call {@code tryNextCredentials()} (false)
	 * @param taskContext The task context
	 */
	public JsCliHelper(AccessManager accessManager, List<AccessDefinition> accessDefs,
			boolean autoTryCredentials, TaskContext taskContext) {
		this(accessManager, accessDefs, autoTryCredentials, taskContext, null, null);
	}

	/**
	 * Instantiate a new JsCliHelper object, with {@code client.create(...)} advanced-option overrides.
	 * @param accessManager the access manager (shared across all clients of this task attempt)
	 * @param accessDefs the ordered list of CLI accesses to try (e.g. [ssh, telnet])
	 * @param autoTryCredentials whether to transparently loop through all candidate
	 *        credential sets (true, historical behavior) or stop at the first
	 *        failure and let the driver call {@code tryNextCredentials()} (false)
	 * @param taskContext The task context
	 * @param sshConfigOverride the {@code ssh} advanced option (already parsed), or null
	 * @param telnetConfigOverride the {@code telnet} advanced option (already parsed), or null
	 */
	public JsCliHelper(AccessManager accessManager, List<AccessDefinition> accessDefs,
			boolean autoTryCredentials, TaskContext taskContext,
			SshConfig sshConfigOverride, TelnetConfig telnetConfigOverride) {
		this.accessManager = accessManager;
		this.autoTryCredentials = autoTryCredentials;
		this.taskContext = taskContext;
		this.sshConfigOverride = sshConfigOverride;
		this.telnetConfigOverride = telnetConfigOverride;
		this.resolution = accessManager.newResolution(accessDefs, this::buildClient);
	}

	private Client buildClient(AccessDefinition accessDef, DeviceCredentialSet credentialSet) throws IOException {
		String host = this.accessManager.resolveHost(accessDef);
		int port = this.accessManager.resolvePort(accessDef);
		this.taskContext.debug("Trying access '{}' ({}) using credentials '{}', at {}:{}.",
			accessDef.getName(), accessDef.getProtocol(), credentialSet.getName(), host, port);
		if (accessDef.getProtocol() == DriverProtocol.SSH) {
			Ssh ssh;
			if (credentialSet instanceof DeviceSshKeyAccount sshKeyAccount) {
				ssh = new Ssh(host, port, resolveSecret(sshKeyAccount.getUsernameSecret()),
					resolveSecret(sshKeyAccount.getPrivateKeySecret()),
					resolveSecret(sshKeyAccount.getPasswordSecret()), this.taskContext);
			}
			else {
				DeviceSshAccount sshAccount = (DeviceSshAccount) credentialSet;
				ssh = new Ssh(host, port, resolveSecret(sshAccount.getUsernameSecret()),
					resolveSecret(sshAccount.getPasswordSecret()), this.taskContext);
			}
			ssh.applySshConfig(accessDef.getSshConfig());
			if (this.sshConfigOverride != null) {
				ssh.applySshConfig(this.sshConfigOverride);
			}
			ssh.setHostKeyVerifier(this.accessManager.resolveSshHostKeyVerifier(accessDef));
			return ssh;
		}
		Telnet telnet = new Telnet(host, port, this.taskContext);
		telnet.applyTelnetConfig(accessDef.getTelnetConfig());
		if (this.telnetConfigOverride != null) {
			telnet.applyTelnetConfig(this.telnetConfigOverride);
		}
		return telnet;
	}

	/**
	 * Resolves a credential field's actual value (local, or from Vault),
	 * wrapping a Vault failure as an {@link IOException} so it's handled the
	 * same way as any other connection/resolution failure by callers.
	 * @param secret the vaultable secret to resolve
	 * @return the resolved value
	 */
	private static String resolveSecret(VaultableSecret secret) throws IOException {
		try {
			return VaultManager.resolve(secret);
		}
		catch (VaultException e) {
			throw new IOException("Unable to resolve a Vault-backed credential: " + e.getMessage(), e);
		}
	}

	/**
	 * Ensures the underlying CLI connection is resolved (lazy connect on first use).
	 * @throws IOException if resolution fails (see {@link Resolution#ensureResolved(boolean)})
	 */
	private void ensureResolved() throws IOException {
		if (this.cli != null) {
			return;
		}
		Client client = this.resolution.ensureResolved(this.autoTryCredentials);
		this.cli = (Cli) client;
		this.account = (DeviceCliAccount) this.resolution.getCurrentCredentialSet();
	}

	/**
	 * Explicitly triggers lazy connection (called from JS before the first
	 * macro/command, and again after {@link #resetForInBandAuthFailure()}).
	 * @throws IOException if resolution fails
	 */
	@Export
	public void reconnect() throws IOException {
		this.ensureResolved();
	}

	/**
	 * Triggers lazy connection if needed, and returns the name of the access
	 * that actually resolved (e.g. "ssh", "telnet", "alternateSsh") - used by
	 * the JS CLI mode-graph to seed its initial mode, since which access ends
	 * up connecting is no longer known ahead of time (unlike before, when
	 * Java always connected before invoking the driver).
	 * @return the resolved access name
	 * @throws IOException if resolution fails
	 */
	@Export
	public String getResolvedAccessName() throws IOException {
		this.ensureResolved();
		return this.resolution.getCurrentAccessDef().getName();
	}

	/**
	 * Called from JS after a first-use {@code macro}/{@code command} call
	 * completes without hitting an in-band authentication failure. For SSH
	 * this merely confirms what Java already knows (auth happens
	 * synchronously in {@code connect()}); for Telnet, whose login only
	 * happens through CLI mode transitions, this is the only reliable
	 * "credentials genuinely work" signal - see
	 * {@link Resolution#confirmCredentialWorks()}.
	 */
	@Export
	public void confirmFirstUseSuccess() {
		this.resolution.confirmCredentialWorks();
	}

	/**
	 * Java-internal accessor (not exposed to JS - not annotated with
	 * {@code @Export}) used by {@code JsDeviceHelper}/{@code JsConfigHelper}
	 * for SFTP/SCP file transfers over an existing SSH session. Forces lazy
	 * resolution if this client hasn't been used yet.
	 * @return the underlying, connected {@link Cli}
	 * @throws IOException if resolution fails
	 */
	public Cli getUnderlyingCli() throws IOException {
		this.ensureResolved();
		return this.cli;
	}

	/**
	 * Called by JS when the driver's own CLI mode graph detects an in-band
	 * authentication failure (typically Telnet, whose real login only happens
	 * through mode transitions, not at the transport level). Disconnects the
	 * current client and clears the "resolved" pointer so a subsequent
	 * {@link #reconnect()} resumes with the next candidate credential set.
	 */
	@Export
	public void resetForInBandAuthFailure() {
		this.resolution.resetForInBandAuthFailure();
		this.cli = null;
		this.account = null;
	}

	/**
	 * Manually advances to the next candidate credential set (only relevant
	 * when {@code autoTryCredentials} is disabled for this client).
	 * @return true if a new client is now connected, false if exhausted
	 */
	@Export
	public boolean tryNextCredentials() {
		boolean success = this.resolution.tryNextCredentials();
		if (success) {
			this.cli = (Cli) this.resolution.getCurrentClient();
			this.account = (DeviceCliAccount) this.resolution.getCurrentCredentialSet();
		}
		else {
			this.cli = null;
			this.account = null;
		}
		return success;
	}

	/**
	 * Remove the echoed command from a CLI output.
	 * @param text The output
	 * @param command The original command
	 * @return the text without echoed command
	 */
	@Export
	public String removeEcho(String text, String command) {
		String output = text;
		// Remove the echo of the command
		String headCommand = command;
		headCommand = headCommand.replaceFirst("[\\r\\n]+$", "");
		if (output.startsWith(headCommand)) {
			output = output.substring(headCommand.length());
			output = output.replaceFirst("^ *[\\r\\n]+", "");
			return output;
		}
		return output;
	}

	/**
	 * Send a command to the device and wait for a result.
	 * @param command The command to send
	 * @param expects The list of possible outputs to match
	 * @param timeout The maximum time to wait for the reply
	 * @param cleanUpActions The cleanup actions to apply (array of CleanUpAction names, null for default)
	 * @param discoverWaitTime Minimum time to wait after match to discover additional output (0 for no wait)
	 * @return the output from the device, result of the passed command
	 */
	@Export
	public String send(String command, String[] expects, int timeout, String[] cleanUpActions, int discoverWaitTime) {
		this.errored = false;
		try {
			this.ensureResolved();
		}
		catch (IOException e) {
			log.error("Unable to resolve the CLI access.", e);
			this.taskContext.error("Unable to connect: " + e.getMessage());
			this.errored = true;
			return null;
		}
		if (command == null) {
			command = "";
		}
		try {
			command = command.replaceAll(Pattern.quote(DeviceDriver.PLACEHOLDER_USERNAME),
				Matcher.quoteReplacement(resolveSecret(account.getUsernameSecret())));
			if (this.taskContext.isTracing()) {
				// Log before injecting secrets
				this.taskContext.trace("About to send the following (secrets not inserted):");
				this.taskContext.trace(command);
				this.taskContext.hexTrace(command);
			}
			log.debug("Command to be sent (secrets not inserted): '{}'.", command);
			command = command.replaceAll(Pattern.quote(DeviceDriver.PLACEHOLDER_PASSWORD),
				Matcher.quoteReplacement(StringUtils.defaultString(resolveSecret(account.getPasswordSecret()))));
			command = command.replaceAll(Pattern.quote(DeviceDriver.PLACEHOLDER_SUPERPASSWORD),
				Matcher.quoteReplacement(StringUtils.defaultString(resolveSecret(account.getSuperPasswordSecret()))));
		}
		catch (IOException e) {
			log.error("Unable to resolve a Vault-backed credential.", e);
			this.taskContext.error("Unable to connect: " + e.getMessage());
			this.errored = true;
			return null;
		}

		// Prepare CommandInput
		Cli.CommandInput input = new Cli.CommandInput(command, expects);

		if (timeout > 0) {
			input.setCommandTimeout(timeout);
		}

		if (cleanUpActions != null) {
			EnumSet<Cli.CleanUpAction> actions = EnumSet.noneOf(Cli.CleanUpAction.class);
			for (String action : cleanUpActions) {
				try {
					actions.add(Cli.CleanUpAction.valueOf(action));
				}
				catch (IllegalArgumentException e) {
					log.warn("Invalid cleanup action '{}', ignoring.", action);
				}
			}
			input.setCleanUpActions(actions);
		}

		if (discoverWaitTime > 0) {
			input.setDiscoverWaitTime(discoverWaitTime);
		}

		try {
			if (this.taskContext.isTracing()) {
				this.taskContext.trace("Expecting one of the following {} pattern(s) within {}ms:",
					expects.length, input.getCommandTimeout() == null ? cli.getCommandTimeout() : input.getCommandTimeout());
				for (String expect : expects) {
					this.taskContext.trace(expect);
				}
			}
			this.lastOutput = cli.send(input);
			if (this.taskContext.isTracing()) {
				this.taskContext.trace("Received the following output:");
				this.taskContext.trace(this.lastOutput.getFullOutput());
				this.taskContext.hexTrace(this.lastOutput.getFullOutput());
				this.taskContext.trace("The following pattern matched:");
				this.taskContext.trace(this.getLastExpectMatchPattern());
			}
			return this.lastOutput.getOutput();
		}
		catch (IOException e) {
			log.error("CLI I/O error.", e);
			this.taskContext.error("I/O error: " + e.getMessage());
			if (this.taskContext.isTracing()) {
				this.taskContext.trace("I/O exception: {}", e.getMessage());
			}
			if (e instanceof WithBufferIOException wioException) {
				String buffer = wioException.getReceivedBuffer().toString();
				if (this.taskContext.isTracing()) {
					this.taskContext.trace("The receive buffer is:");
					this.taskContext.trace(buffer);
					this.taskContext.hexTrace(buffer);
				}
			}
			this.errored = true;
		}
		return null;
	}

	/**
	 * Send a command to the device and wait for a result.
	 * @param command The command to send
	 * @param expects The list of possible outputs to match
	 * @param timeout The maximum time to wait for the reply
	 * @return the output from the device, result of the passed command
	 */
	@Export
	public String send(String command, String[] expects, int timeout) {
		return send(command, expects, timeout, null, 0);
	}

	/**
	 * Add a trace message to the debug log.
	 * @param message The message to add
	 */
	@Export
	public void trace(String message) {
		this.taskContext.trace(message);
	}

	/**
	 * Send a command to the device and wait for a result, with default timeout value.
	 * @param command The command to send
	 * @param expects The list of possible outputs to match
	 * @return the output from the device, result of the passed command
	 */
	@Export
	public String send(String command, String[] expects) throws IOException {
		return send(command, expects, -1);
	}

	/**
	 * Wait for a reply from the device CLI without sending anything.
	 * @param expects = The patterns to wait for
	 * @return the output from the device, result of the passed command
	 */
	@Export
	public String send(String[] expects) throws IOException {
		return send(null, expects, -1);
	}

	/**
	 * Get the last sent command.
	 * @return the last sent command
	 */
	@Export
	public String getLastCommand() {
		if (this.lastOutput == null) {
			return null;
		}
		return this.lastOutput.getCommand();
	}

	/**
	 * Get the last expect match.
	 * @return the last matched expect
	 */
	@Export
	public String getLastExpectMatch() {
		if (this.lastOutput == null || this.lastOutput.getExpectMatch() == null) {
			return null;
		}
		return this.lastOutput.getExpectMatch().group();
	}

	/**
	 * Get the last expect specific match.
	 * @param group The specific match to tget
	 * @return the matched group
	 */
	@Export
	public String getLastExpectMatchGroup(int group) {
		try {
			return this.lastOutput.getExpectMatch().group(group);
		}
		catch (Exception e) {
			return null;
		}
	}

	/**
	 * Get the last matched pattern.
	 * @return the last matched pattern
	 */
	@Export
	public String getLastExpectMatchPattern() {
		if (this.lastOutput == null) {
			return null;
		}
		return this.lastOutput.getExpectMatchPattern();
	}

	/**
	 * Get the last match index.
	 * @return the last matched index
	 */
	@Export
	public int getLastExpectMatchIndex() {
		if (this.lastOutput == null) {
			return -1;
		}
		return this.lastOutput.getExpectMatchIndex();
	}

	/**
	 * Get the last full output of the device (after a command was sent).
	 * @return the last full output
	 */
	@Export
	public String getLastFullOutput() {
		if (this.lastOutput == null) {
			return null;
		}
		return this.lastOutput.getFullOutput();
	}

	/**
	 * Check whether there was an error after the last command.
	 * @return true if there was an error
	 */
	@Export
	public boolean isErrored() {
		return errored;
	}

	/**
	 * Pause the thread for the given number of milliseconds.
	 * @param millis The number of milliseconds to wait for
	 */
	@Export
	public void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException e) {
		}
	}

}
