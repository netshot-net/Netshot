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
package net.netshot.netshot.driver;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.codec.binary.Hex;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;
import net.netshot.netshot.platform.config.Setting;

/**
 * Reads the device drivers from their sources (the user drivers folder, then the drivers bundled
 * with the server, on the file system or in the jar), and hashes them.
 */
@Slf4j
public class DriverLoader {

	/** Folder of the additional drivers (read on each load of the drivers). */
	public static final Setting<String> DRIVERS_PATH = Setting.text("netshot.drivers.path").live();

	/**
	 * The drivers read by one load.
	 *
	 * @param drivers the drivers, by name
	 * @param hash the global hash of the drivers (hex-encoded string)
	 */
	public record LoadedDrivers(Map<String, DeviceDriver> drivers, String hash) {
	}

	/**
	 * Reads all the drivers from their sources.
	 *
	 * @return the drivers, and their global hash
	 * @throws Exception
	 *                       something bad
	 */
	public LoadedDrivers load() throws Exception {
		Map<String, DeviceDriver> newDrivers = new HashMap<>();

		final String addPath = Netshot.config().current().get(DRIVERS_PATH);
		if (addPath != null) {
			final File folder = new File(addPath);
			if (folder.isDirectory()) {
				File[] files = folder.listFiles(new FileFilter() {
					@Override
					public boolean accept(File pathname) {
						return pathname.isFile() && pathname.getName().endsWith(".js");
					}
				});
				for (File file : files) {
					log.info("Found user device driver file {}.", file);
					Reader reader = null;
					try {
						InputStream stream = new FileInputStream(file);
						reader = new InputStreamReader(stream);
						DeviceDriver driver = new DeviceDriver(reader, file.getName(),
							new DeviceDriver.Location(DeviceDriver.LocationType.FILE, file.getAbsolutePath()));
						if (newDrivers.containsKey(driver.getName())) {
							log.warn(
								"Skipping user device driver file {}, because a similar driver {} is already loaded.",
								file, driver.getName());
						}
						else {
							newDrivers.put(driver.getName(), driver);
						}
					}
					catch (Exception e) {
						log.error("Error while loading user device driver {}.", file, e);
					}
					finally {
						if (reader != null) {
							reader.close();
						}
					}
				}
			}
			else {
				log.error("Unable to open {} to find device drivers.", addPath);
			}
		}

		String driverPathName = "drivers/";
		final URL driverUrl = DeviceDriver.class.getResource("/" + driverPathName);
		if (driverUrl != null && "file".equals(driverUrl.getProtocol())) {
			log.debug("Scanning folder {} for device drivers (.js).", driverUrl);
			final File folder = new File(driverUrl.toURI());
			if (folder.isDirectory()) {
				File[] files = folder.listFiles(new FileFilter() {
					@Override
					public boolean accept(File pathname) {
						return pathname.isFile() && pathname.getName().endsWith(".js");
					}
				});
				for (File file : files) {
					log.info("Found Netshot device driver file {}.", file);
					Reader reader = null;
					try {
						InputStream stream = DeviceDriver.class
							.getResourceAsStream("/" + driverPathName + file.getName());
						reader = new InputStreamReader(stream);
						DeviceDriver driver = new DeviceDriver(reader, file.getName(),
							new DeviceDriver.Location(DeviceDriver.LocationType.EMBEDDED, file.getAbsolutePath()));
						if (newDrivers.containsKey(driver.getName())) {
							log.warn(
								"Skipping user device driver file {}, because a similar driver {} is already loaded.",
								file, driver.getName());
						}
						else {
							newDrivers.put(driver.getName(), driver);
						}
					}
					catch (Exception e1) {
						log.error("Error while loading the device driver {}.", file, e1);
					}
					finally {
						if (reader != null) {
							reader.close();
						}
					}
				}
			}
		}
		if (driverUrl != null && "jar".equals(driverUrl.getProtocol())) {
			String path = driverUrl.getFile();
			path = path.substring(0, path.lastIndexOf('!'));
			File file = new File(new URI(path));
			try {
				JarFile jar = new JarFile(file);
				final Enumeration<JarEntry> e = jar.entries();
				while (e.hasMoreElements()) {
					final JarEntry je = e.nextElement();
					if (!je.isDirectory() && je.getName().startsWith(driverPathName)) {
						log.info("Found Netshot device driver file {} (in jar).", je.getName());
						Reader reader = null;
						try {
							InputStream stream = jar.getInputStream(je);
							reader = new InputStreamReader(stream);
							DeviceDriver driver = new DeviceDriver(reader,
								je.getName().replace(driverPathName, ""),
								new DeviceDriver.Location(DeviceDriver.LocationType.EMBEDDED, jar.getName()));
							if (newDrivers.containsKey(driver.getName())) {
								log.warn(
									"Skipping user device driver file {}, because a similar driver {} is already loaded.",
									file, driver.getName());
							}
							else {
								newDrivers.put(driver.getName(), driver);
							}
						}
						catch (Exception e1) {
							log.error("Error while loading the device driver {} from jar file.", file, e1);
						}
						finally {
							if (reader != null) {
								reader.close();
							}
						}
					}
				}
				jar.close();
			}
			catch (Exception e) {
				log.error("While looking for device drivers in {}.", path, e);
			}
		}

		StringBuffer hashBuffer = new StringBuffer();
		for (DeviceDriver driver : newDrivers.values()) {
			hashBuffer.append(driver.getSourceHash());
		}
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] hash = digest.digest(hashBuffer.toString().getBytes());
		return new LoadedDrivers(newDrivers, Hex.encodeHexString(hash));
	}
}
