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
package net.netshot.netshot.api.rest.v1.device;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import net.netshot.netshot.api.rest.v1.Selection;
import net.netshot.netshot.api.rest.v1.vault.VaultInstanceDto;
import net.netshot.netshot.api.rest.v1.vault.VaultInstanceMapper;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.vault.VaultInstanceService;

/**
 * Writes the devices in their v1 representation, with what the device records don't hold: the Vault instances
 * the credentials of their accesses read from (loaded in one query per response). Also loads the devices other
 * objects refer to.
 */
public class DeviceRefs {

	private final SearchService searchService;
	private final DriverRegistry driverRegistry;
	private final VaultInstanceService vaultInstanceService;

	/**
	 * Instantiates the device writer.
	 *
	 * @param searchService the device search use cases
	 * @param driverRegistry the loaded device drivers
	 * @param vaultInstanceService the Vault instance use cases (to name the instances credentials refer to)
	 */
	@Inject
	public DeviceRefs(SearchService searchService, DriverRegistry driverRegistry, VaultInstanceService vaultInstanceService) {
		this.searchService = searchService;
		this.driverRegistry = driverRegistry;
		this.vaultInstanceService = vaultInstanceService;
	}

	/**
	 * Gives the mapper of devices, the Vault instances their accesses refer to being loaded first.
	 *
	 * @param devices the devices which will be mapped
	 * @return the mapper
	 */
	public Function<Device, DeviceDto> mapper(Collection<Device> devices) {
		List<Long> vaultIds = new ArrayList<>();
		devices.forEach(d -> vaultIds.addAll(DeviceMapper.vaultInstanceIds(d)));
		LongFunction<VaultInstanceDto> vaults = VaultInstanceMapper.resolver(this.vaultInstanceService, vaultIds);
		return d -> DeviceMapper.toDto(this.driverRegistry, d, vaults);
	}

	/**
	 * Maps a device.
	 *
	 * @param device the device
	 * @return the representation
	 */
	public DeviceDto toDto(Device device) {
		return this.mapper(List.of(device)).apply(device);
	}

	/**
	 * Maps a device summary.
	 *
	 * @param device the device summary
	 * @return the representation (the properties a summary doesn't hold are null)
	 */
	public DeviceDto toDto(DeviceSummary device) {
		return DeviceMapper.toDto(this.driverRegistry, device);
	}

	/**
	 * Loads the devices other objects refer to, in one query (only if the response writes the references, in their
	 * full representation only when expanded).
	 *
	 * @param selection the selection of the response
	 * @param property the name of the property holding the references
	 * @param ids the IDs of the devices (null elements ignored)
	 * @return the devices by ID (the ones not found, by ID only)
	 */
	public LongFunction<DeviceDto> byIds(Selection selection, String property, Collection<Long> ids) {
		Set<Long> wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
		Map<Long, DeviceDto> devices = new HashMap<>();
		if (!wanted.isEmpty() && selection.includes(property)) {
			ListQuery query = ListQuery.byIds(wanted);
			if (selection.expands(property)) {
				List<Device> full = this.searchService.listDevices(query, null).items();
				Function<Device, DeviceDto> mapper = this.mapper(full);
				full.forEach(d -> devices.put(d.id(), mapper.apply(d)));
			}
			else {
				this.searchService.listDeviceSummaries(query, null).items()
					.forEach(d -> devices.put(d.id(), this.toDto(d)));
			}
		}
		return id -> {
			DeviceDto found = devices.get(id);
			return found != null ? found : DeviceMapper.byId(id);
		};
	}
}
