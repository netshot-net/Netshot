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
package net.netshot.netshot.device.script;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;

import org.apache.poi.openxml4j.exceptions.InvalidOperationException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.hibernate.Session;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.database.Database;
import net.netshot.netshot.device.Config;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.Device.MissingDeviceDriverException;
import net.netshot.netshot.device.DeviceDriver;
import net.netshot.netshot.device.access.AccessManager;
import net.netshot.netshot.device.access.InvalidCredentialsException;
import net.netshot.netshot.device.attribute.AttributeDefinition;
import net.netshot.netshot.device.attribute.AttributeDefinition.AttributeLevel;
import net.netshot.netshot.device.attribute.ConfigAttribute;
import net.netshot.netshot.device.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.device.script.helper.JsCliHelper;
import net.netshot.netshot.device.script.helper.JsDeviceScriptOptions;
import net.netshot.netshot.device.script.helper.JsClientFactory;
import net.netshot.netshot.device.script.helper.JsConfigHelper;
import net.netshot.netshot.device.script.helper.JsDeviceHelper;
import net.netshot.netshot.device.script.helper.JsSnmpHelper;
import net.netshot.netshot.device.script.helper.JsUtils;
import net.netshot.netshot.work.TaskContext;

@Slf4j
public final class SnapshotDeviceScript extends DeviceScript {

	public SnapshotDeviceScript(TaskContext logger) {
		super(logger);
	}

	/**
	 * Dump the new configuration into dump folder.
	 * @param device = the device
	 * @param config = the config
	 */
	protected void dumpConfig(Device device, Config config) {
		String path = Netshot.getConfig("netshot.snapshots.dump");
		if (path != null) {
			try {
				BufferedWriter output = new BufferedWriter(
					new FileWriter(Paths.get(path, device.getName()).normalize().toFile()));
				Map<String, ConfigAttribute> newAttributes = config.getAttributeMap();
				for (AttributeDefinition definition : device.getDeviceDriver().getAttributes()) {
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

	@Override
	protected void run(Session session, Device device, AccessManager accessManager)
		throws InvalidCredentialsException, IOException, InvalidOperationException, MissingDeviceDriverException {

		DeviceDriver driver = device.getDeviceDriver();
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
			JsDeviceHelper deviceHelper = new JsDeviceHelper(device, jsCliHelper, session, this.taskContext, false);
			options.setDeviceHelper(deviceHelper);
			if (this.taskContext.isTracing()) {
				this.taskContext.trace("Device {} options: {}", device.getId(), deviceHelper.resolveOptionValues());
			}
			Config config = new Config(device);
			Config lastConfig = Database.unproxy(device.getLastConfig());
			JsConfigHelper configHelper = new JsConfigHelper(device, config, lastConfig, jsCliHelper, this.taskContext);
			options.setConfigHelper(configHelper);
			context.getBindings("js").getMember("_connect")
				.execute("snapshot", options);

			// Check whether the config has actually changed
			boolean different = false;
			if (lastConfig == null) {
				different = true;
			}
			else if (lastConfig.getCustomHash() == null && config.getCustomHash() == null) {
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
				different = !Objects.equals(lastConfig.getCustomHash(), config.getCustomHash());
			}
			if (different) {
				device.setLastConfig(config);
				device.getConfigs().add(config);
			}
			else {
				this.taskContext.info("The configuration hasn't changed. Not storing a new one in the DB.");
				for (ConfigAttribute ca : config.getAttributes()) {
					if (ca instanceof ConfigBinaryFileAttribute cbfa) {
						try {
							log.debug("Removing useless file {}", cbfa.getFilePath());
							Files.deleteIfExists(cbfa.getFilePath());
						}
						catch (IOException e) {
							log.warn("Unable to delete binary file attribute {}", cbfa.getFilePath(), e);
							this.taskContext.warn("Unable to delete binary file attribute {}", cbfa.getFilePath(), e);
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
