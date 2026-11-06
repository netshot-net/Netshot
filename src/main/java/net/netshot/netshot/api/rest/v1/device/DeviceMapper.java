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
package net.netshot.netshot.api.rest.v1.device;

import java.util.stream.Collectors;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongFunction;

import net.netshot.netshot.platform.query.ListQuery;
import net.netshot.netshot.inventory.search.SearchService;
import net.netshot.netshot.api.rest.v1.StatsResponse;
import net.netshot.netshot.api.rest.v1.RefDto;
import net.netshot.netshot.api.rest.v1.RefId;
import net.netshot.netshot.api.rest.v1.V1Links;
import net.netshot.netshot.api.rest.v1.credentials.CredentialSetMapper;
import net.netshot.netshot.api.rest.v1.credentials.CredentialSetRequest;
import net.netshot.netshot.api.rest.v1.domain.DomainMapper;
import net.netshot.netshot.api.rest.v1.group.GroupMapper;
import net.netshot.netshot.api.rest.v1.vault.VaultInstanceDto;
import net.netshot.netshot.credentials.DeviceSshAccount;
import net.netshot.netshot.driver.DeviceDriver;
import net.netshot.netshot.driver.DriverRegistry;
import net.netshot.netshot.inventory.device.AccessSpecificCredential;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.DeviceAccess;
import net.netshot.netshot.inventory.device.DeviceChange;
import net.netshot.netshot.inventory.device.DeviceCreation;
import net.netshot.netshot.inventory.device.DeviceSummary;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.inventory.device.attribute.DeviceAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceBinaryAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceLongTextAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceNumericAttribute;
import net.netshot.netshot.inventory.device.attribute.DeviceTextAttribute;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * Maps the devices to their v1 representation.
 */
public final class DeviceMapper {

	/** The path of the devices. */
	public static final String PATH = "devices";

	private DeviceMapper() {
	}

	private static String driverDescription(DriverRegistry driverRegistry, String driverName) {
		DeviceDriver driver = driverName == null ? null : driverRegistry.getDriverByName(driverName);
		return driver == null ? "Unknown driver" : driver.getDescription();
	}

	private static boolean isPast(Instant date) {
		return date != null && date.isBefore(Instant.now());
	}

	/**
	 * Gives the representation of a device by its ID only (not loaded: a device deleted meanwhile, or a reference
	 * not written).
	 *
	 * @param id the device ID
	 * @return the representation
	 */
	public static DeviceDto byId(long id) {
		return new DeviceDto(id, V1Links.of(PATH, id), null, null, null, null, null, null, null, null, null, null, null,
			null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
			null, null, null, null, null, null);
	}

	/**
	 * Gets the IDs of the Vault instances the credential sets of the accesses of a device read values from.
	 *
	 * @param device the device
	 * @return the IDs (possibly repeated)
	 */
	public static List<Long> vaultInstanceIds(Device device) {
		List<Long> ids = new ArrayList<>();
		for (DeviceAccess access : device.accesses()) {
			if (access.globalCredentialSet() != null) {
				ids.addAll(CredentialSetMapper.vaultInstanceIds(access.globalCredentialSet()));
			}
			if (access.specificCredentialSet() != null) {
				ids.addAll(CredentialSetMapper.vaultInstanceIds(access.specificCredentialSet()));
			}
		}
		return ids;
	}

	/**
	 * Maps a device.
	 *
	 * @param driverRegistry the loaded drivers (for the description of the driver)
	 * @param device the device
	 * @param vaults gives the representation of a Vault instance the credentials of the accesses read from, by ID
	 * @return the representation
	 */
	public static DeviceDto toDto(DriverRegistry driverRegistry, Device device, LongFunction<VaultInstanceDto> vaults) {
		return new DeviceDto(device.id(), V1Links.of(PATH, device.id()), device.name(), (long) device.version(),
			device.changeDate(), device.createdDate(), device.creator(), device.name(), device.mgmtAddress(), device.status(),
			DomainMapper.toDto(device.mgmtDomain()), device.family(), device.driver(),
			driverDescription(driverRegistry, device.driver()), device.networkClass(), device.softwareVersion(),
			device.softwareLevel(), device.serialNumber(), device.location(), device.contact(), device.comments(),
			device.cachedIpAddress() == null ? null : device.cachedIpAddress().getHostAddress(),
			device.compliant(), isPast(device.eolDate()), isPast(device.eosDate()),
			device.eolDate(), toDto(device.eolModule()), device.eosDate(), toDto(device.eosModule()),
			device.lastSnapshotSuccessDate(), device.lastSnapshotFailureDate(),
			device.tags(), device.virtualDevices(), device.vrfInstances(), device.options(),
			device.attributes() == null ? null : device.attributes().stream().map(DeviceMapper::toDto).toList(),
			device.accesses() == null ? null : device.accesses().stream().map(a -> toDto(a, vaults)).toList(),
			device.ownerGroups() == null ? null : device.ownerGroups().stream().map(GroupMapper::toDto).toList());
	}

	/**
	 * Maps a device summary: the properties the summary doesn't hold are null (the resource only uses a summary
	 * when the selection writes none of them, see {@link DeviceDto#SUMMARY_PROPERTIES}).
	 *
	 * @param driverRegistry the loaded drivers (for the description of the driver)
	 * @param device the device summary
	 * @return the representation
	 */
	public static DeviceDto toDto(DriverRegistry driverRegistry, DeviceSummary device) {
		return new DeviceDto(device.id(), V1Links.of(PATH, device.id()), device.name(), null, null, null, null,
			device.name(), device.mgmtAddress(), device.status(), null, device.family(), device.driver(),
			driverDescription(driverRegistry, device.driver()), device.networkClass(), null, device.softwareLevel(), null,
			null, null, null, null, device.configCompliant(), device.eol(), device.eos(), null, null, null, null,
			null, null, null, null, null, null, null, null, null);
	}

	/**
	 * Maps a module.
	 *
	 * @param module the module (null gives null)
	 * @return the representation
	 */
	public static ModuleDto toDto(Module module) {
		if (module == null) {
			return null;
		}
		return new ModuleDto(module.id(), module.slot(), module.partNumber(), module.serialNumber(), module.firstSeenDate(),
			module.lastSeenDate(), module.removed());
	}

	private static DeviceAttributeDto toDto(DeviceAttribute attribute) {
		return switch (attribute) {
			case DeviceTextAttribute a -> new DeviceAttributeDto(a.name(), "TEXT", a.text());
			case DeviceLongTextAttribute a -> new DeviceAttributeDto(a.name(), "LONGTEXT", a.text());
			case DeviceNumericAttribute a -> new DeviceAttributeDto(a.name(), "NUMERIC", a.number());
			case DeviceBinaryAttribute a -> new DeviceAttributeDto(a.name(), "BINARY", a.assumption());
		};
	}

	private static DeviceAccessDto toDto(DeviceAccess access, LongFunction<VaultInstanceDto> vaults) {
		return new DeviceAccessDto(access.accessName(), access.address(), access.port(),
			CredentialSetMapper.toDto(access.globalCredentialSet(), vaults), CredentialSetMapper.toDto(access.specificCredentialSet(), vaults),
			access.sshHostKeyVerification(), access.sshTrustedHostKeys(), access.sshTrustedHostKeysSince(),
			access.httpsCaTrustMode(), access.httpsCustomCaCertificate());
	}

	/**
	 * The accesses of a request, split into the accesses and the definitions of their specific credential sets.
	 *
	 * @param accesses the transient accesses
	 * @param specificCredentials the definitions of the specific credential sets
	 */
	private record Accesses(List<DeviceAccess> accesses, List<AccessSpecificCredential> specificCredentials) {
	}

	private static Accesses toAccesses(List<DeviceAccessRequest> requests) {
		List<DeviceAccess> accesses = new ArrayList<>();
		List<AccessSpecificCredential> specifics = new ArrayList<>();
		Set<String> names = new HashSet<>();
		for (DeviceAccessRequest request : requests == null ? List.<DeviceAccessRequest>of() : requests) {
			if (request == null || request.accessName() == null || request.accessName().isBlank()) {
				throw NetshotException.invalidParameter("accesses", "Every access needs an accessName.");
			}
			if (!names.add(request.accessName())) {
				throw NetshotException.invalidParameter("accesses", "The access '%s' is given twice.".formatted(request.accessName()));
			}
			accesses.add(DeviceAccess.builder()
				.accessName(request.accessName())
				.address(request.address())
				.port(request.port())
				// Only the ID of the global credential set is used: the service resolves it
				.globalCredentialSet(request.globalCredentialSet() == null ? null
					: DeviceSshAccount.builder().id(request.globalCredentialSet().id()).build())
				.sshHostKeyVerification(request.sshHostKeyVerification())
				.sshTrustedHostKeys(request.sshTrustedHostKeys())
				.httpsCaTrustMode(request.httpsCaTrustMode())
				.httpsCustomCaCertificate(request.httpsCustomCaCertificate())
				.build());
			if (request.specificCredentialSet() != null) {
				DeviceCredentialSetRequest cs = request.specificCredentialSet();
				specifics.add(new AccessSpecificCredential.Definition(request.accessName(), CredentialSetMapper.toDefinition(
					new CredentialSetRequest("specific", cs.type(), null, cs.username(), cs.usernameVault(), cs.password(),
						cs.passwordVault(), cs.superPassword(), cs.superPasswordVault(), cs.privateKey(), cs.privateKeyVault(),
						cs.community(), cs.communityVault(), cs.authType(), cs.authKey(), cs.authKeyVault(), cs.privType(),
						cs.privKey(), cs.privKeyVault()))));
			}
		}
		return new Accesses(accesses, specifics);
	}

	private static long domainId(RefId domain) {
		if (domain == null) {
			throw NetshotException.invalidParameter("mgmtDomain", "The management domain is required.");
		}
		return domain.id();
	}

	/**
	 * Maps a request to add a device to the command of the service.
	 *
	 * @param request the request
	 * @param author the name of the user adding the device
	 * @return the command
	 * @throws NetshotException if the request is incoherent (accesses or options without a driver, bad specific credentials...)
	 */
	public static DeviceCreation toCreation(DeviceCreationRequest request, String author) {
		boolean autoDiscover = request.driver() == null || request.driver().isBlank();
		if (autoDiscover && request.accesses() != null && !request.accesses().isEmpty()) {
			throw NetshotException.invalidParameter("accesses", "The accesses can only be given with a driver (no auto discovery).");
		}
		if (autoDiscover && request.options() != null && !request.options().isEmpty()) {
			throw NetshotException.invalidParameter("options", "The options can only be given with a driver (no auto discovery).");
		}
		Accesses accesses = toAccesses(request.accesses());
		return new DeviceCreation(autoDiscover, request.mgmtAddress() == null ? "" : request.mgmtAddress(), domainId(request.mgmtDomain()),
			autoDiscover ? "" : request.driver(), accesses.accesses(), request.options(), request.comments(),
			request.tags() == null ? null : List.copyOf(request.tags()), author, accesses.specificCredentials());
	}

	/**
	 * Maps a request to replace the editable properties of a device to the command of the service.
	 *
	 * @param request the request
	 * @return the command
	 * @throws NetshotException if the request is incoherent
	 */
	public static DeviceChange toChange(DeviceRequest request) {
		Accesses accesses = toAccesses(request.accesses());
		return new DeviceChange(request.status(), request.comments(), request.mgmtAddress() == null ? "" : request.mgmtAddress(),
			domainId(request.mgmtDomain()), accesses.accesses(), accesses.specificCredentials(), request.options(), request.tags());
	}

	/**
	 * Maps a device to the request which would define it as it is (the base of a merge patch): the secrets of the
	 * specific credential sets are absent, which leaves them unchanged.
	 *
	 * @param device the device
	 * @return the request
	 */
	public static DeviceRequest toRequest(Device device) {
		List<DeviceAccessRequest> accesses = device.accesses().stream()
			.sorted(Comparator.comparing(DeviceAccess::accessName, Comparator.nullsFirst(Comparator.naturalOrder())))
			.map(DeviceMapper::toRequest).toList();
		return new DeviceRequest(device.mgmtAddress(), new RefId(device.mgmtDomain().id()), device.status(), device.comments(),
			device.tags(), device.options(), accesses);
	}

	private static DeviceAccessRequest toRequest(DeviceAccess access) {
		DeviceCredentialSetRequest specific = null;
		if (access.specificCredentialSet() != null) {
			CredentialSetRequest cs = CredentialSetMapper.toRequest(access.specificCredentialSet());
			specific = new DeviceCredentialSetRequest(cs.type(), null, null, null, null, null, null, null, null, null, null,
				cs.authType(), null, null, cs.privType(), null, null);
		}
		return new DeviceAccessRequest(access.accessName(), access.address(), access.port(),
			access.globalCredentialSet() == null ? null : new RefId(access.globalCredentialSet().id()), specific,
			access.sshHostKeyVerification(), access.sshTrustedHostKeys(), access.httpsCaTrustMode(), access.httpsCustomCaCertificate());
	}

	/**
	 * Builds the resolver of device IDs into references (statistics grouped by device).
	 *
	 * @param searchService the device search use cases
	 * @return the resolver
	 */
	public static StatsResponse.Resolver resolver(SearchService searchService) {
		return ids -> searchService.listDeviceSummaries(ListQuery.byIds(ids), null).items().stream()
			.collect(Collectors.toMap(DeviceSummary::id, d -> new RefDto(d.id(), V1Links.of(PATH, d.id()), d.name())));
	}
}
