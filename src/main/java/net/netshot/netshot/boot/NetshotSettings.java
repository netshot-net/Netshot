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
package net.netshot.netshot.boot;

import java.util.List;

import net.netshot.netshot.Netshot;
import net.netshot.netshot.aaa.auth.Oidc;
import net.netshot.netshot.aaa.auth.Radius;
import net.netshot.netshot.aaa.auth.Tacacs;
import net.netshot.netshot.aaa.user.PasswordPolicy;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.access.Ssh;
import net.netshot.netshot.access.Telnet;
import net.netshot.netshot.access.collector.Collector;
import net.netshot.netshot.access.collector.SnmpTrapReceiver;
import net.netshot.netshot.access.collector.SshServer;
import net.netshot.netshot.access.collector.SyslogServer;
import net.netshot.netshot.api.rest.LoggerFilter;
import net.netshot.netshot.api.rest.RestServer;
import net.netshot.netshot.cluster.ClusterManager;
import net.netshot.netshot.compliance.check.JsRuleRuntime;
import net.netshot.netshot.compliance.check.PyRuleRuntime;
import net.netshot.netshot.devicescript.SnapshotDeviceScript;
import net.netshot.netshot.devicescript.helper.PythonFileSystem;
import net.netshot.netshot.driver.DriverLoader;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.platform.database.ConnectionPools;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.http.HttpServerAddress;
import net.netshot.netshot.platform.tls.BouncyCastleLoader;
import net.netshot.netshot.task.AutoSnapshotScheduler;
import net.netshot.netshot.task.engine.ChildTaskOrchestrator;
import net.netshot.netshot.task.engine.TaskJob;
import net.netshot.netshot.task.engine.TaskManager;
import net.netshot.netshot.vault.VaultManager;
import net.netshot.netshot.vault.VaultTokenRefresher;
import net.netshot.netshot.platform.config.SettingCatalog;

/**
 * The catalog of all the configuration settings: the classes declaring {@code Setting} fields
 * (with their nested classes). A class declaring settings must be listed here, so that the
 * configuration is checked against its settings (see {@code NetshotSettingsTest}).
 */
public final class NetshotSettings {

	private NetshotSettings() {
	}

	/**
	 * Gets the classes declaring the settings.
	 *
	 * @return the classes
	 */
	public static List<Class<?>> declaringClasses() {
		return List.of(
			Netshot.class,
			Oidc.class,
			Radius.class,
			Tacacs.class,
			PasswordPolicy.class,
			UiUser.class,
			Ssh.class,
			Telnet.class,
			Collector.class,
			SnmpTrapReceiver.class,
			SshServer.class,
			SyslogServer.class,
			LoggerFilter.class,
			RestServer.class,
			LoggingSetup.class,
			ClusterManager.class,
			JsRuleRuntime.class,
			PyRuleRuntime.class,
			SnapshotDeviceScript.class,
			PythonFileSystem.class,
			DriverLoader.class,
			ConfigBinaryFileAttribute.class,
			ConnectionPools.class,
			Database.class,
			HttpServerAddress.class,
			BouncyCastleLoader.class,
			AutoSnapshotScheduler.class,
			ChildTaskOrchestrator.class,
			TaskJob.class,
			TaskManager.class,
			VaultManager.class,
			VaultTokenRefresher.class);
	}

	/**
	 * Builds the catalog of the settings.
	 *
	 * @return the catalog
	 */
	public static SettingCatalog catalog() {
		return SettingCatalog.fromClasses(declaringClasses());
	}
}
