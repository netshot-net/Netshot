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
package net.netshot.netshot.devicescript;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;

import org.apache.poi.openxml4j.exceptions.InvalidOperationException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.access.AccessManager;
import net.netshot.netshot.access.Connectors;
import net.netshot.netshot.access.InvalidCredentialsException;
import net.netshot.netshot.access.collector.SshServer;
import net.netshot.netshot.devicescript.helper.JsCliHelper;
import net.netshot.netshot.devicescript.helper.JsClientFactory;
import net.netshot.netshot.devicescript.helper.JsConfigHelper;
import net.netshot.netshot.devicescript.helper.JsDeviceHelper;
import net.netshot.netshot.devicescript.helper.JsDeviceScriptOptions;
import net.netshot.netshot.devicescript.helper.JsSnmpHelper;
import net.netshot.netshot.driver.AttributeDefinition;
import net.netshot.netshot.driver.AttributeDefinition.AttributeLevel;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.driver.MissingDeviceDriverException;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.ConfigDraft;
import net.netshot.netshot.inventory.config.ConfigRepository;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.device.DeviceDraft;
import net.netshot.netshot.platform.config.Setting;
import net.netshot.netshot.platform.log.TaskContext;
import net.netshot.netshot.platform.script.JsUtils;

@Slf4j
public final class SnapshotDeviceScript extends DeviceScript {

	/** Folder to dump the configurations into, after each snapshot. */
	static final Setting<String> DUMP_PATH = Setting.text("netshot.snapshots.dump").live();

	/** The embedded SSH server, where the devices upload files (null if not running). */
	private final SshServer sshServer;

	/**
	 * Creates the snapshot script.
	 *
	 * @param driverRegistry the loaded device drivers
	 * @param connectors the secret resolver and the SSH client of the device accesses
	 * @param sshServer the embedded SSH server, where the devices upload files (null if not running)
	 * @param logger the task logger
	 */
	public SnapshotDeviceScript(DriverRegistry driverRegistry, Connectors connectors, SshServer sshServer, TaskContext logger) {
		super(driverRegistry, connectors, logger);
		this.sshServer = sshServer;
	}

	/**
	 * Dump the new configuration into dump folder.
	 * @param device = the device
	 * @param config = the config
	 */
	protected void dumpConfig(DeviceDraft device, ConfigDraft config) {
		String path = Netshot.config().current().get(DUMP_PATH);
		if (path != null) {
			// The device name is reported by the device itself, it must not be able to escape the dump folder
			Path dumpFolder = Paths.get(path).toAbsolutePath().normalize();
			String fileName = Objects.toString(device.getName(), "").replaceAll("[/\\\\\\x00]", "_");
			Path dumpFile = dumpFolder.resolve(fileName).normalize();
			if (fileName.isEmpty() || !dumpFolder.equals(dumpFile.getParent())) {
				log.warn("Invalid device name '{}' to dump the configuration into the dump folder.", device.getName());
				this.taskContext.warn("Unable to write the configuration to a file: invalid device name.");
				return;
			}
			try {
				BufferedWriter output = new BufferedWriter(new FileWriter(dumpFile.toFile()));
				Map<String, ConfigAttribute> newAttributes = config.getAttributeMap();
				for (AttributeDefinition definition : this.driverRegistry.forDevice(device).getAttributes()) {
					if (!definition.isDump()) {
						continue;
					}
					String preText = definition.getPreDump();
					if (preText != null) {
						preText = preText.replaceAll("%when%",
							Matcher.quoteReplacement(new Date().toString()));
						output.write(preText);
						output.write("\r\n");
					}
					ConfigAttribute newAttribute = newAttributes.get(definition.getName());
					if (newAttribute != null) {
						String text = newAttribute.getAsText();
						if (text != null) {
							if (definition.getPreLineDump() != null || definition.getPostLineDump() != null) {
								String[] lines = text.split("\\r?\\n");
								for (String line : lines) {
									if (definition.getPreLineDump() != null) {
										output.write(definition.getPreLineDump());
									}
									output.write(line);
									if (definition.getPostLineDump() != null) {
										output.write(definition.getPostLineDump());
									}
									output.write("\r\n");
								}
							}
							else {
								output.write(text);
							}
						}
					}
					String postText = definition.getPostDump();
					if (postText != null) {
						postText = postText.replaceAll("%when%",
							Matcher.quoteReplacement(new Date().toString()));
						output.write(postText);
						output.write("\r\n");
					}
				}
				output.close();
				this.taskContext.info("The configuration has been saved as a file in the dump folder.");
			}
			catch (Exception e) {
				log.warn("Couldn't write the configuration into file.", e);
				this.taskContext.warn("Unable to write the configuration to a file.");
			}
		}
	}

	/**
	 * Loads the last configuration of a device (with its long texts), to compare the new one with.
	 *
	 * @param device the device
	 * @return the last configuration, or null if the device has none
	 */
	private Config loadLastConfig(DeviceDraft device) {
		Long lastConfigId = device.getOriginal().lastConfigId();
		if (lastConfigId == null) {
			return null;
		}
		return this.connectors.transactions().read(dsl -> new ConfigRepository().find(dsl, lastConfigId, true));
	}

	@Override
	protected void run(DeviceDraft device, AccessManager accessManager)
		throws InvalidCredentialsException, IOException, InvalidOperationException, MissingDeviceDriverException {

		DeviceDriver driver = this.driverRegistry.forDevice(device);
		JsCliHelper jsCliHelper = new JsCliHelper(accessManager, driver.getDefaultCliAccessDefinitions(), true, this.taskContext);
		JsSnmpHelper jsSnmpHelper = new JsSnmpHelper(accessManager, driver.getDefaultSnmpAccessDefinitions(), true, this.taskContext);
		JsClientFactory clientFactory = new JsClientFactory(accessManager, driver);
		JsDeviceScriptOptions options = new JsDeviceScriptOptions(jsCliHelper, jsSnmpHelper, this.taskContext);
		options.setClientFactory(clientFactory);
		clientFactory.setOptions(options);

		try (Context context = driver.getContext()) {
			this.taskContext.info("Starting snapshot of device {} using driver {} version {}",
				device.getId(), driver.getName(), driver.getVersion());
			driver.loadCode(context);
			JsDeviceHelper deviceHelper = new JsDeviceHelper(this.driverRegistry, this.connectors.transactions(), device, jsCliHelper, this.taskContext, false);
			options.setDeviceHelper(deviceHelper);
			if (this.taskContext.isTracing()) {
				this.taskContext.trace("Device {} options: {}", device.getId(), deviceHelper.resolveOptionValues());
			}
			ConfigDraft config = new ConfigDraft(device.getId());
			Config lastConfig = device.getLastConfig() != null ? device.getLastConfig() : loadLastConfig(device);
			device.setLastConfig(lastConfig);
			JsConfigHelper configHelper = new JsConfigHelper(this.driverRegistry, device, config, lastConfig, jsCliHelper, this.sshServer, this.taskContext);
			options.setConfigHelper(configHelper);
			context.getBindings("js").getMember("_connect")
				.execute("snapshot", options);

			// Check whether the config has actually changed
			boolean different = false;
			if (lastConfig == null) {
				different = true;
			}
			else if (lastConfig.customHash() == null && config.getCustomHash() == null) {
				Map<String, ConfigAttribute> oldAttributes = lastConfig.getAttributeMap();
				Map<String, ConfigAttribute> newAttributes = config.getAttributeMap();
				for (AttributeDefinition definition : driver.getAttributes()) {
					if (definition.getLevel() != AttributeLevel.CONFIG) {
						continue;
					}
					ConfigAttribute oldAttribute = oldAttributes.get(definition.getName());
					ConfigAttribute newAttribute = newAttributes.get(definition.getName());
					if (oldAttribute != null) {
						if (!oldAttribute.valueEquals(newAttribute)) {
							different = true;
							break;
						}
					}
					else if (newAttribute != null) {
						different = true;
						break;
					}
				}
			}
			else {
				different = !Objects.equals(lastConfig.customHash(), config.getCustomHash());
			}
			if (different) {
				device.setNewConfig(config.toConfig());
			}
			else {
				this.taskContext.info("The configuration hasn't changed. Not storing a new one in the DB.");
				for (ConfigAttribute ca : config.getAttributes()) {
					if (ca instanceof ConfigBinaryFileAttribute cbfa) {
						Path pendingPath = cbfa.getPendingFilePath(device.getId());
						try {
							log.debug("Removing useless file {}", pendingPath);
							Files.deleteIfExists(pendingPath);
						}
						catch (IOException e) {
							log.warn("Unable to delete binary file attribute {}", pendingPath, e);
							this.taskContext.warn("Unable to delete binary file attribute {}", pendingPath, e);
						}
					}
				}
			}
			this.dumpConfig(device, config);

		}
		catch (PolyglotException e) {
			log.error("Error while running snapshot using driver {}.", driver.getName(), e);
			this.taskContext.error("Error while running snapshot using driver {}: '{}'.",
				driver.getName(), JsUtils.jsErrorToMessage(e));
			if (e.getMessage() != null && e.getMessage().contains("Authentication failed")) {
				throw new InvalidCredentialsException("Authentication failed");
			}
			else {
				throw e;
			}
		}
		catch (InvalidOperationException e) {
			log.error("No such method 'snapshot' while using driver {}.", driver.getName(), e);
			this.taskContext.error("No such method 'snapshot' while using driver %s to take snapshot: '{}'.",
				driver.getName(), e.getMessage());
			throw e;
		}
		finally {
			if (options.getConfigHelper() != null) {
				options.getConfigHelper().release();
			}
		}
	}

}
