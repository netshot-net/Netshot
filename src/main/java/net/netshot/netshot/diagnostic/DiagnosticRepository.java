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
package net.netshot.netshot.diagnostic;

import java.util.List;

import org.hibernate.Session;

import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.database.Page;

/**
 * The queries on the diagnostics and their results. No method opens a transaction or catches an
 * exception.
 */
public class DiagnosticRepository {

	/**
	 * Finds the diagnostics with their target group.
	 *
	 * <p>Fetch plan: the target group is fetched with the diagnostic (to-one association).</p>
	 *
	 * @param session the session
	 * @param page the window on the result
	 * @return the diagnostics
	 */
	public List<Diagnostic> findAllWithGroup(Session session, Page page) {
		var query = session.createQuery("select d from Diagnostic d left join fetch d.targetGroup order by d.id", Diagnostic.class);
		page.apply(query);
		return query.list();
	}

	/**
	 * Finds a diagnostic with its target group (same fetch plan as {@link #findAllWithGroup}).
	 *
	 * @param session the session
	 * @param id the diagnostic ID
	 * @return the diagnostic, or null if there is no such diagnostic
	 */
	public Diagnostic findWithGroup(Session session, long id) {
		return session
			.createQuery("select d from Diagnostic d left join fetch d.targetGroup where d.id = :id", Diagnostic.class)
			.setParameter("id", id)
			.uniqueResult();
	}

	/**
	 * Finds a diagnostic, with only its eager associations.
	 *
	 * @param session the session
	 * @param id the diagnostic ID
	 * @return the diagnostic, or null if there is no such diagnostic
	 */
	public Diagnostic find(Session session, long id) {
		return session.get(Diagnostic.class, id);
	}

	/**
	 * Finds a device group, to be the target of a diagnostic.
	 *
	 * @param session the session
	 * @param id the group ID
	 * @return the group, or null if there is no such group
	 */
	public DeviceGroup findGroup(Session session, long id) {
		return session.get(DeviceGroup.class, id);
	}

	/**
	 * Deletes all the results of a diagnostic.
	 *
	 * @param session the session
	 * @param diagnosticId the diagnostic ID
	 */
	public void deleteResults(Session session, long diagnosticId) {
		session.createMutationQuery("delete from DiagnosticResult dr where dr.diagnostic.id = :id")
			.setParameter("id", diagnosticId)
			.executeUpdate();
	}

	/**
	 * Finds the diagnostic results of a device.
	 *
	 * <p>Fetch plan: the diagnostic of each result is fetched with it (its ID and name are read).</p>
	 *
	 * @param session the session
	 * @param deviceId the device ID
	 * @param page the window on the result
	 * @return the results
	 */
	public List<DiagnosticResult> findResultsOfDevice(Session session, long deviceId, Page page) {
		var query = session
			.createQuery("from DiagnosticResult dr join fetch dr.diagnostic where dr.device.id = :id", DiagnosticResult.class)
			.setParameter("id", deviceId);
		page.apply(query);
		return query.list();
	}
}
