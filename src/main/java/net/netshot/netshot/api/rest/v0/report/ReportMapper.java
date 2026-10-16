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
package net.netshot.netshot.api.rest.v0.report;

import java.util.ArrayList;
import java.util.List;

import net.netshot.netshot.api.rest.v0.device.DeviceMapper;
import net.netshot.netshot.report.AccessFailureDevice;
import net.netshot.netshot.report.DailyChangeCount;
import net.netshot.netshot.report.GroupConfigComplianceStat;
import net.netshot.netshot.report.GroupSoftwareComplianceStat;
import net.netshot.netshot.report.HardwareSupportCount;
import net.netshot.netshot.report.HardwareSupportStats;
import net.netshot.netshot.report.PolicyRuleDevice;
import net.netshot.netshot.report.SoftwareLevelDevice;

/**
 * Maps the results of the report use cases to their DTOs.
 */
public final class ReportMapper {

	private ReportMapper() {
	}

	/**
	 * Maps the daily change counts.
	 *
	 * @param counts the counts
	 * @return the DTOs
	 */
	public static List<ConfigChangeCountDto> toChangeCountDtos(List<DailyChangeCount> counts) {
		List<ConfigChangeCountDto> dtos = new ArrayList<>(counts.size());
		counts.forEach(count -> dtos.add(new ConfigChangeCountDto(count.changeCount(), count.changeDay())));
		return dtos;
	}

	/**
	 * Maps the configuration compliance of the groups.
	 *
	 * @param stats the statistics
	 * @return the DTOs
	 */
	public static List<GroupConfigComplianceStatDto> toConfigComplianceDtos(List<GroupConfigComplianceStat> stats) {
		List<GroupConfigComplianceStatDto> dtos = new ArrayList<>(stats.size());
		stats.forEach(stat -> dtos.add(new GroupConfigComplianceStatDto(stat.groupId(), stat.groupName(),
			stat.groupFolder(), stat.compliantDeviceCount(), stat.deviceCount())));
		return dtos;
	}

	/**
	 * Maps the software compliance of the groups.
	 *
	 * @param stats the statistics
	 * @return the DTOs
	 */
	public static List<GroupSoftwareComplianceStatDto> toSoftwareComplianceDtos(List<GroupSoftwareComplianceStat> stats) {
		List<GroupSoftwareComplianceStatDto> dtos = new ArrayList<>(stats.size());
		stats.forEach(stat -> dtos.add(new GroupSoftwareComplianceStatDto(stat.groupId(), stat.groupName(),
			stat.goldDeviceCount(), stat.silverDeviceCount(), stat.bronzeDeviceCount(), stat.deviceCount())));
		return dtos;
	}

	/**
	 * Maps the hardware support status: the end-of-sale counts, then the end-of-life ones.
	 *
	 * @param stats the status
	 * @return the DTOs
	 */
	public static List<HardwareSupportStatDto> toHardwareSupportDtos(HardwareSupportStats stats) {
		List<HardwareSupportStatDto> dtos = new ArrayList<>(stats.endOfSale().size() + stats.endOfLife().size());
		for (HardwareSupportCount count : stats.endOfSale()) {
			dtos.add(new HardwareSupportStatDto(HardwareSupportStatDto.END_OF_SALE, count.eoxDate(), count.deviceCount()));
		}
		for (HardwareSupportCount count : stats.endOfLife()) {
			dtos.add(new HardwareSupportStatDto(HardwareSupportStatDto.END_OF_LIFE, count.eoxDate(), count.deviceCount()));
		}
		return dtos;
	}

	/**
	 * Maps the compliance results of devices.
	 *
	 * @param devices the results
	 * @return the DTOs
	 */
	public static List<PolicyRuleDeviceDto> toPolicyRuleDeviceDtos(List<PolicyRuleDevice> devices) {
		List<PolicyRuleDeviceDto> dtos = new ArrayList<>(devices.size());
		devices.forEach(device -> dtos.add(new PolicyRuleDeviceDto(DeviceMapper.toDto(device.device()),
			device.policyId(), device.policyName(), device.ruleId(), device.ruleName(), device.checkDate(),
			device.result())));
		return dtos;
	}

	/**
	 * Maps the devices with their software version.
	 *
	 * @param devices the devices
	 * @return the DTOs
	 */
	public static List<SoftwareLevelDeviceDto> toSoftwareLevelDeviceDtos(List<SoftwareLevelDevice> devices) {
		List<SoftwareLevelDeviceDto> dtos = new ArrayList<>(devices.size());
		devices.forEach(device -> dtos.add(new SoftwareLevelDeviceDto(DeviceMapper.toDto(device.device()),
			device.softwareVersion())));
		return dtos;
	}

	/**
	 * Maps the devices without recent successful snapshot.
	 *
	 * @param devices the devices
	 * @return the DTOs
	 */
	public static List<AccessFailureDeviceDto> toAccessFailureDeviceDtos(List<AccessFailureDevice> devices) {
		List<AccessFailureDeviceDto> dtos = new ArrayList<>(devices.size());
		devices.forEach(device -> dtos.add(new AccessFailureDeviceDto(DeviceMapper.toDto(device.device()),
			device.lastSuccess(), device.lastFailure())));
		return dtos;
	}
}
