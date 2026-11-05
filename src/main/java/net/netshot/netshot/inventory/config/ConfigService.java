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
package net.netshot.netshot.inventory.config;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.exception.DataAccessException;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.driver.AttributeDefinition;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.driver.MissingDeviceDriverException;
import net.netshot.netshot.inventory.config.ConfigItemException.Reason;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * The configuration use cases: list the configurations, download an item of one, and compare two.
 *
 * <p>The configurations it returns are records loaded as documented on the repository method
 * which found them.</p>
 */
@Slf4j
public class ConfigService {

	private final Transactions transactions;
	private final ConfigRepository repository;
	private final DriverRegistry driverRegistry;
	private final DeviceRepository deviceRepository = new DeviceRepository();

	/**
	 * Instantiates the service.
	 *
	 * @param transactions the transaction support
	 * @param repository the configuration repository
	 * @param driverRegistry the loaded device drivers
	 */
	public ConfigService(Transactions transactions, ConfigRepository repository, DriverRegistry driverRegistry) {
		this.transactions = transactions;
		this.repository = repository;
		this.driverRegistry = driverRegistry;
	}

	/**
	 * Lists the configurations of a device, with their attributes (see {@link ConfigRepository#findByDevice}
	 * for what is loaded).
	 *
	 * @param deviceId the device ID
	 * @param page the window on the result
	 * @return the configurations, the most recent first (none if there is no such device)
	 * @throws NetshotException if the configurations can't be read
	 */
	public List<Config> listByDevice(long deviceId, Page page) {
		try {
			return this.transactions.read(dsl -> this.repository.findByDevice(dsl, deviceId, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the configs.", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the configs");
		}
	}

	/**
	 * Lists the configuration changes matching some criteria.
	 *
	 * @param filter the criteria
	 * @param page the window on the result
	 * @return the configurations in their summary form
	 * @throws NetshotException if the configurations can't be read
	 */
	public List<ConfigSummary> listChanges(ConfigFilter filter, Page page) {
		try {
			return this.transactions.read(dsl -> this.repository.findSummaries(dsl, filter, page));
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the configs", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the configs");
		}
	}

	/**
	 * Gets the content of an item (a long text or a binary file) of a configuration.
	 *
	 * @param id the configuration ID
	 * @param itemName the name of the item
	 * @return the content, with the name to give to the downloaded file
	 * @throws ConfigItemException if the configuration doesn't exist, or has no such item
	 *         (or not one which can be downloaded or whose content is not available), or can't be read
	 */
	public ConfigItem getItem(long id, String itemName) {
		try {
			return this.transactions.read(dsl -> {
				Config config = this.repository.find(dsl, id, true);
				if (config == null) {
					log.warn("Unable to find the config object.");
					throw new ConfigItemException(Reason.CONFIG_NOT_FOUND, "Unable to find the configuration set");
				}
				DeviceSummary device = this.deviceRepository.findSummary(dsl, config.deviceId());
				for (ConfigAttribute attribute : config.attributes()) {
					if (attribute.name().equals(itemName)) {
						if (attribute instanceof ConfigLongTextAttribute longText) {
							return textItem(config, device, longText);
						}
						else if (attribute instanceof ConfigBinaryFileAttribute fileAttribute) {
							return fileItem(config, device, fileAttribute);
						}
					}
				}
				throw new ConfigItemException(Reason.INVALID_ITEM, "Invalid configuration item '%s'".formatted(itemName));
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to get the configuration", e);
			throw new ConfigItemException(Reason.DATABASE_ERROR, "Unable to get the configuration");
		}
	}

	private static ConfigItem textItem(Config config, DeviceSummary device, ConfigLongTextAttribute attribute) {
		String text = attribute.text();
		if (text == null) {
			throw new ConfigItemException(Reason.ITEM_NOT_AVAILABLE, "Configuration item not available");
		}
		String fileName = downloadFileName(config, device, "%s.cfg".formatted(attribute.name()));
		byte[] content = text.getBytes(StandardCharsets.UTF_8);
		return new ConfigItem(fileName, content, null, content.length);
	}

	private static ConfigItem fileItem(Config config, DeviceSummary device, ConfigBinaryFileAttribute attribute) {
		String fileName = attribute.originalName();
		if (fileName == null) {
			fileName = "%s.dat".formatted(attribute.name());
		}
		else {
			fileName = "%s_%s".formatted(attribute.name(), fileName);
		}
		fileName = downloadFileName(config, device, fileName);
		File file = attribute.getFilePath(config.deviceId(), config.id()).toFile();
		return new ConfigItem(fileName, null, file, file.length());
	}

	/**
	 * Prefixes the name of a downloaded file with the device name and the date of the configuration (if known).
	 *
	 * @param config the configuration
	 * @param device the device of the configuration
	 * @param fileName the name of the file
	 * @return the full name of the file
	 */
	private static String downloadFileName(Config config, DeviceSummary device, String fileName) {
		try {
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
			return String.format("%s_%s_%s", device.name(),
				formatter.format(Date.from(config.changeDate())), fileName);
		}
		catch (Exception e) {
			log.trace("Unable to build the full name of the file, keeping '{}'.", fileName, e);
			return fileName;
		}
	}

	/**
	 * Compares two configurations of devices of the same kind.
	 *
	 * @param id1 the ID of the original configuration, or 0 to take the configuration which precedes
	 *        the second one on the same device (an empty configuration if there is none)
	 * @param id2 the ID of the revised configuration
	 * @param includeDeltas whether to compute the differences of the comparable items of the driver
	 * @param includeConfigs whether to return the configurations, with their attributes
	 * @return the comparison
	 * @throws NetshotException if a configuration doesn't exist, the driver is missing or the drivers of
	 *         the configurations are different, or the configurations can't be read
	 */
	public ConfigDiff diff(long id1, long id2, boolean includeDeltas, boolean includeConfigs) {
		try {
			return this.transactions.read(dsl -> {
				Config config2 = this.repository.find(dsl, id2, true);
				Config config1;
				if (config2 != null && id1 == 0) {
					config1 = this.repository.findPrevious(dsl, config2, true);
					if (config1 == null) {
						config1 = Config.empty(config2.deviceId());
					}
				}
				else {
					config1 = this.repository.find(dsl, id1, true);
				}
				if (config1 == null || config2 == null) {
					log.warn("Non existing config, {} or {}.", id1, id2);
					throw new NetshotException(ErrorCode.NETSHOT_CONFIG_NOT_FOUND, "Config not found");
				}
				DeviceDriver driver1;
				DeviceDriver driver2;
				try {
					DeviceSummary device1 = this.deviceRepository.findSummary(dsl, config1.deviceId());
					DeviceSummary device2 = this.deviceRepository.findSummary(dsl, config2.deviceId());
					driver1 = this.driverRegistry.forDevice(device1.id(), device1.driver());
					driver2 = this.driverRegistry.forDevice(device2.id(), device2.driver());
				}
				catch (MissingDeviceDriverException e) {
					log.error("Missing driver.");
					throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Missing driver");
				}
				if (!driver1.equals(driver2)) {
					log.warn("Incompatible configurations, {} and {} (different drivers).", id1, id2);
					throw new NetshotException(ErrorCode.NETSHOT_INCOMPATIBLE_CONFIGS, "Incompatible configurations");
				}

				Map<String, List<ConfigDelta>> deltas = null;
				if (includeDeltas) {
					deltas = new HashMap<>();
					computeDeltas(driver1, config1, config2, deltas);
				}
				return new ConfigDiff(date(config1.changeDate()), date(config2.changeDate()), config1.id(), config2.id(),
					includeConfigs ? config1 : null, includeConfigs ? config2 : null, deltas);
			});
		}
		catch (DataAccessException e) {
			log.error("Unable to fetch the configs", e);
			throw new NetshotException(ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR, "Unable to fetch the configs");
		}
	}

	private static Date date(Instant instant) {
		return instant == null ? null : Date.from(instant);
	}

	private static void computeDeltas(DeviceDriver driver, Config config1, Config config2, Map<String, List<ConfigDelta>> deltas) {
		Map<String, ConfigAttribute> attributes1 = config1.getAttributeMap();
		Map<String, ConfigAttribute> attributes2 = config2.getAttributeMap();
		for (AttributeDefinition definition : driver.getAttributes()) {
			if (definition.isComparable()) {
				ConfigAttribute attribute1 = attributes1.get(definition.getName());
				ConfigAttribute attribute2 = attributes2.get(definition.getName());
				String text1 = attribute1 == null ? "" : attribute1.getAsText();
				String text2 = attribute2 == null ? "" : attribute2.getAsText();
				List<String> lines1 = Arrays.asList(text1.replace("\r", "").split("\n"));
				int[] lineParents1 = Config.getLineParents(lines1);
				List<String> lines2 = Arrays.asList(text2.replace("\r", "").split("\n"));
				Patch<String> patch = DiffUtils.diff(lines1, lines2);
				for (AbstractDelta<String> delta : patch.getDeltas()) {
					deltas.computeIfAbsent(definition.getTitle(), _ -> new ArrayList<>())
						.add(ConfigDelta.of(delta, lines1, lineParents1));
				}
			}
		}
	}
}
