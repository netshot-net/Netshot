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

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Entity;
import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.access.DeviceAccess;
import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.compliance.Exemption;
import net.netshot.netshot.compliance.HardwareRule;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.Rule;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.credentials.DeviceHttpAccount;
import net.netshot.netshot.credentials.DeviceSnmpv1Community;
import net.netshot.netshot.credentials.DeviceSnmpv2cCommunity;
import net.netshot.netshot.credentials.DeviceSnmpv3Community;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.credentials.DeviceSshKeyAccount;
import net.netshot.netshot.credentials.DeviceTelnetAccount;
import net.netshot.netshot.diagnostic.Diagnostic;
import net.netshot.netshot.diagnostic.DiagnosticBinaryResult;
import net.netshot.netshot.diagnostic.DiagnosticLongTextResult;
import net.netshot.netshot.diagnostic.DiagnosticNumericResult;
import net.netshot.netshot.diagnostic.DiagnosticResult;
import net.netshot.netshot.diagnostic.DiagnosticTextResult;
import net.netshot.netshot.hook.Hook;
import net.netshot.netshot.hook.HookTrigger;
import net.netshot.netshot.hook.WebHook;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigLongTextAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigNumericAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigTextAttribute;
import net.netshot.netshot.inventory.config.attribute.LongTextConfiguration;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.Network4Address;
import net.netshot.netshot.inventory.device.Network6Address;
import net.netshot.netshot.inventory.device.NetworkAddress;
import net.netshot.netshot.inventory.device.NetworkInterface;
import net.netshot.netshot.inventory.device.PhysicalAddress;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceLongTextAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.inventory.group.DeviceGroupMembership;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.inventory.group.StaticDeviceGroup;
import net.netshot.netshot.task.DebugLog;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.TaskDeviceListMember;
import net.netshot.netshot.userscript.DeviceJsScript;
import net.netshot.netshot.vault.HashicorpVaultKv2Instance;
import net.netshot.netshot.platform.database.DatabaseModel;

/**
 * The persistent model of the application: lists every entity, which the
 * platform database code is not allowed to know about.
 */
public final class EntityModel {

	/**
	 * Builds the persistent model: the annotated entity classes (including the
	 * subclasses of the tasks, rules and diagnostics, discovered by scanning)
	 * and the attribute converters.
	 *
	 * @return the model, to be given to {@code Database.init()}
	 */
	public static DatabaseModel build() {
		List<Class<?>> entities = new ArrayList<>(List.of(
			Device.class,
			DeviceGroup.class,
			DeviceGroupMembership.class,
			Config.class,
			DeviceAttribute.class,
			DeviceNumericAttribute.class,
			DeviceTextAttribute.class,
			DeviceLongTextAttribute.class,
			DeviceBinaryAttribute.class,
			ConfigAttribute.class,
			ConfigNumericAttribute.class,
			ConfigTextAttribute.class,
			ConfigLongTextAttribute.class,
			ConfigBinaryAttribute.class,
			ConfigBinaryFileAttribute.class,
			LongTextConfiguration.class,
			StaticDeviceGroup.class,
			DynamicDeviceGroup.class,
			Module.class,
			Domain.class,
			PhysicalAddress.class,
			NetworkAddress.class,
			Network4Address.class,
			Network6Address.class,
			NetworkInterface.class,
			DeviceAccess.class,
			DeviceSnmpv1Community.class,
			DeviceSnmpv2cCommunity.class,
			DeviceSnmpv3Community.class,
			DeviceSshAccount.class,
			DeviceSshKeyAccount.class,
			DeviceTelnetAccount.class,
			DeviceHttpAccount.class,
			Policy.class,
			Rule.class,
			Task.class,
			TaskDeviceListMember.class,
			DebugLog.class,
			Exemption.class,
			Exemption.Key.class,
			CheckResult.class,
			CheckResult.Key.class,
			SoftwareRule.class,
			HardwareRule.class,
			DeviceJsScript.class,
			Diagnostic.class,
			DiagnosticResult.class,
			DiagnosticBinaryResult.class,
			DiagnosticNumericResult.class,
			DiagnosticLongTextResult.class,
			DiagnosticTextResult.class,
			UiUser.class,
			ApiToken.class,
			Hook.class,
			WebHook.class,
			HookTrigger.class,
			HashicorpVaultKv2Instance.class));
		entities.addAll(Task.getTaskClasses());
		for (Class<?> clazz : Rule.getRuleClasses()) {
			entities.add(clazz);
			for (Class<?> subClass : clazz.getClasses()) {
				if (subClass.getAnnotation(Entity.class) != null) {
					entities.add(subClass);
				}
			}
		}
		entities.addAll(Diagnostic.getDiagnosticClasses());
		List<Class<? extends AttributeConverter<?, ?>>> converters = List.of(Task.Status.StatusConverter.class);
		return new DatabaseModel(entities, converters);
	}

	private EntityModel() {
	}
}
