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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.query.Query;

import net.netshot.netshot.platform.database.Page;

/**
 * The queries on configurations. The {@link Session} is always the first parameter: the caller
 * (the service) owns the session and the transaction.
 */
public class ConfigRepository {

	/**
	 * Finds a configuration, without any of its associations.
	 *
	 * @param session the session
	 * @param id the configuration ID
	 * @return the configuration, or null if there is no such configuration
	 */
	public Config find(Session session, long id) {
		return session.get(Config.class, id);
	}

	/**
	 * Finds the configurations of a device, the most recent first.
	 *
	 * <p>Fetch plan: the attributes of the configurations are fetched with them (the long texts
	 * and the binary files they refer to are not loaded).</p>
	 *
	 * @param session the session
	 * @param deviceId the device ID
	 * @param page the window on the result
	 * @return the configurations
	 */
	public List<Config> findByDevice(Session session, long deviceId, Page page) {
		Query<Config> query = session
			.createQuery(
				"select c from Config c left join fetch c.attributes ca where c.device.id = :device order by c.changeDate desc",
				Config.class)
			.setParameter("device", deviceId);
		page.apply(query);
		return query.list();
	}

	/**
	 * Finds the configuration gathered just before another one on the same device.
	 *
	 * @param session the session
	 * @param config the configuration
	 * @return the previous configuration, or null if there is none
	 */
	public Config findPrevious(Session session, Config config) {
		return session
			.createQuery(
				"from Config c where c.device = :device and c.changeDate < :date2 order by c.changeDate desc",
				Config.class)
			.setParameter("device", config.getDevice())
			.setParameter("date2", config.getChangeDate())
			.setMaxResults(1)
			.uniqueResult();
	}

	/**
	 * Loads the attributes of a configuration, so that it can be used once the session is closed.
	 * (The long texts and the binary files they refer to are not loaded.)
	 *
	 * @param config the configuration
	 */
	public void loadAttributes(Config config) {
		Hibernate.initialize(config.getAttributes());
	}

	/**
	 * Lists the configuration changes in their summary form.
	 *
	 * @param session the session
	 * @param filter the criteria
	 * @param page the window on the result
	 * @return the configuration summaries
	 */
	public List<ConfigSummary> findSummaries(Session session, ConfigFilter filter, Page page) {
		String hql = "select new " + ConfigSummary.class.getName() + "("
			+ "c.id, "
			+ "c.device.name, "
			+ "c.device.id, "
			+ "c.changeDate, "
			+ "c.author, "
			+ "d.networkClass"
			+ ") from Config c join c.device d";
		Map<String, Object> hqlParams = new HashMap<>();
		if (!filter.groupIds().isEmpty()) {
			hql += " join d.groupMemberships gm";
			hqlParams.put("groupIds", filter.groupIds());
		}
		hql += " where 1 = 1";
		if (!filter.domainIds().isEmpty()) {
			hql += " and d.mgmtDomain.id in (:domainIds)";
			hqlParams.put("domainIds", filter.domainIds());
		}
		if (!filter.groupIds().isEmpty()) {
			hql += " and gm.key.group.id in (:groupIds)";
		}
		Date after = filter.after();
		if (after != null) {
			hql += " and c.changeDate >= :startDate";
			hqlParams.put("startDate", after);
		}
		Date before = filter.before();
		if (before != null) {
			hql += " and c.changeDate < :endDate";
			hqlParams.put("endDate", before);
		}
		Query<ConfigSummary> query = session.createQuery(hql, ConfigSummary.class);
		for (Entry<String, Object> param : hqlParams.entrySet()) {
			query.setParameter(param.getKey(), param.getValue());
		}
		page.apply(query);
		return query.list();
	}
}
