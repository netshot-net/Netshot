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
package net.netshot.netshot.inventory.device;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.query.Query;

import net.netshot.netshot.compliance.CheckResult;
import net.netshot.netshot.credentials.DeviceCredentialSet;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.platform.database.Page;

/**
 * The queries on devices. The {@link Session} is always the first parameter: the caller
 * (the service) owns the session and the transaction. No query here changes how a
 * transaction behaves, none catches exceptions.
 */
public class DeviceRepository {

	/**
	 * Finds a device, without any of its associations.
	 *
	 * @param session the session
	 * @param id the device ID
	 * @return the device, or null if there is no such device
	 */
	public Device find(Session session, long id) {
		return session.get(Device.class, id);
	}

	/**
	 * Finds a device with everything its full representation needs, so that it
	 * can be used once the session is closed (detached).
	 *
	 * <p>Fetch plan: the management domain, the end of life and end of sale modules
	 * and the attributes are fetched with the device; the group memberships (with their
	 * group), the compliance check results (for the compliance state), the accesses
	 * (with their credential sets and the domain of these) and the tags are initialized.
	 * The other associations (modules, interfaces, configs, diagnostic results,
	 * exemptions, virtual devices, VRFs) are left uninitialized.</p>
	 *
	 * @param session the session
	 * @param id the device ID
	 * @return the device, or null if there is no such device
	 */
	public Device findWithDetails(Session session, long id) {
		Device device = session.createQuery(
			"from Device d "
				+ "left join fetch d.mgmtDomain "
				+ "left join fetch d.eolModule "
				+ "left join fetch d.eosModule "
				+ "left join fetch d.attributes "
				+ "where d.id = :id",
			Device.class)
			.setParameter("id", id)
			.uniqueResult();
		if (device == null) {
			return null;
		}
		Hibernate.initialize(device.getGroupMemberships());
		Hibernate.initialize(device.getComplianceCheckResults());
		Hibernate.initialize(device.getAccesses());
		Hibernate.initialize(device.getTags());
		return device;
	}

	/**
	 * Finds an active (not disabled) device having the given management address (case insensitive).
	 *
	 * @param session the session
	 * @param address the management address
	 * @param excludedId the ID of a device to ignore (the one being edited), or null
	 * @return a matching device, or null if there is none
	 */
	public Device findActiveByMgmtAddress(Session session, String address, Long excludedId) {
		String hql = "from Device d where lower(d.mgmtAddress) = lower(:address) and d.status <> :disabled"
			+ (excludedId == null ? "" : " and d.id <> :id");
		Query<Device> query = session.createQuery(hql, Device.class)
			.setParameter("address", address)
			.setParameter("disabled", Device.Status.DISABLED)
			.setMaxResults(1);
		if (excludedId != null) {
			query.setParameter("id", excludedId);
		}
		return query.uniqueResult();
	}

	/**
	 * Lists the devices in their summary form.
	 *
	 * @param session the session
	 * @param groupId the ID of a group whose members are listed (in the order of the group), or null for all the devices
	 * @param page the window on the result
	 * @return the device summaries
	 */
	public List<DeviceSummary> findSummaries(Session session, Long groupId, Page page) {
		String hql = "select new " + DeviceSummary.class.getName() + "("
			+ "d.id, "
			+ "d.name, "
			+ "d.family, "
			+ "d.mgmtAddress, "
			+ "d.status, "
			+ "d.driver, "
			+ "case when (d.eolDate < current_date()) then true else false end,  "
			+ "case when (d.eosDate < current_date()) then true else false end, "
			+ "case when (select count(cr) from CheckResult cr where cr.key.device = d and cr.result = :nonConforming) = 0 then true else false end, "
			+ "d.softwareLevel, "
			+ "d.networkClass "
			+ ") from Device d";
		if (groupId != null) {
			hql += " join d.groupMemberships gm where gm.key.group.id = :groupId order by gm.position asc, d.name asc";
		}
		Query<DeviceSummary> query = session.createQuery(hql, DeviceSummary.class);
		query.setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING);
		if (groupId != null) {
			query.setParameter("groupId", groupId);
		}
		page.apply(query);
		return query.list();
	}

	/**
	 * Finds the domain a device is managed in.
	 * (Lookup of a related aggregate: to move to its own repository when the domain gets its own use cases.)
	 *
	 * @param session the session
	 * @param id the domain ID
	 * @return the domain, or null if there is no such domain
	 */
	public Domain findDomain(Session session, long id) {
		return session.get(Domain.class, id);
	}

	/**
	 * Finds a credential set (of any kind, global or device specific).
	 * (Lookup of a related aggregate: to move to its own repository when the credential sets get their own use cases.)
	 *
	 * @param session the session
	 * @param id the credential set ID
	 * @return the credential set, or null if there is no such credential set
	 */
	public DeviceCredentialSet findCredentialSet(Session session, long id) {
		return session.get(DeviceCredentialSet.class, id);
	}

	/**
	 * Finds the global SNMP communities usable to poll a device of the given domain
	 * (those of the domain and those of no particular domain).
	 *
	 * @param session the session
	 * @param domain the domain
	 * @return the SNMP communities
	 */
	public List<DeviceCredentialSet> findSnmpCommunities(Session session, Domain domain) {
		return session
			.createQuery("from DeviceSnmpCommunity c where (mgmtDomain = :domain or mgmtDomain is null) and (not (c.deviceSpecific))",
				DeviceCredentialSet.class)
			.setParameter("domain", domain)
			.list();
	}

	/**
	 * Finds the paths of the binary files holding configuration attributes of a device.
	 *
	 * @param session the session
	 * @param device the device
	 * @return the file paths
	 */
	public List<Path> findConfigBinaryFilePaths(Session session, Device device) {
		List<ConfigBinaryFileAttribute> attributes = session
			.createQuery("from ConfigBinaryFileAttribute cfa where cfa.config.device = :device",
				ConfigBinaryFileAttribute.class)
			.setParameter("device", device)
			.list();
		List<Path> paths = new ArrayList<>();
		for (ConfigBinaryFileAttribute attribute : attributes) {
			paths.add(attribute.getFilePath());
		}
		return paths;
	}

	/**
	 * Deletes the long text values (attributes, configurations, diagnostic results) of a device.
	 * They have to be removed explicitly before the device itself (delete cascade constraint).
	 *
	 * @param session the session
	 * @param device the device
	 */
	public void deleteLongTexts(Session session, Device device) {
		session
			.createMutationQuery("delete from LongTextConfiguration ltc where ltc in (select da.longText from DeviceLongTextAttribute da where da.device = :device)")
			.setParameter("device", device)
			.executeUpdate();
		session
			.createMutationQuery("delete from LongTextConfiguration ltc where ltc in (select ca.longText from Config c join c.attributes ca where c.device = :device)")
			.setParameter("device", device)
			.executeUpdate();
		session
			.createMutationQuery("delete from LongTextConfiguration ltc where ltc in (select dr.longText from DiagnosticLongTextResult dr where dr.device = :device)")
			.setParameter("device", device)
			.executeUpdate();
	}
}
