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
package net.netshot.netshot.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.hibernate.Session;
import org.hibernate.query.Query;

import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.domain.Domain;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.database.Page;
import net.netshot.netshot.task.type.DeviceBasedTask;

/**
 * The queries on tasks. The {@link Session} is always the first parameter: the caller
 * (the service) owns the session and the transaction. No query here changes how a
 * transaction behaves, none catches exceptions.
 */
public class TaskRepository {

	/** The HQL select clause projecting a task row into a {@link TaskSummary}. */
	private static final String SUMMARY_SELECT = "select new " + TaskSummary.class.getName() + "("
		+ "t.id, "
		+ "type(t), "
		+ "t.status, "
		+ "t.author, "
		+ "t.target, "
		+ "t.comments, "
		+ "t.creationDate, "
		+ "t.changeDate, "
		+ "t.executionDate, "
		+ "t.scheduleReference, "
		+ "t.scheduleType, "
		+ "t.scheduleFactor, "
		+ "t.priority, "
		+ "t.runnerId, "
		+ "t.debugEnabled, "
		+ "t.device.id, "
		+ "t.deviceGroup.id, "
		+ "t.parentTaskId, "
		+ "t.childOrder, "
		+ "t.scheduleMode, "
		+ "t.stopOnFailure"
		+ ") ";

	private static final String STATUS_COUNT_SELECT = "select new " + TaskStatusCount.class.getName() + "(t.status, count(t.id)) from Task t";

	/**
	 * Finds a task.
	 *
	 * <p>Fetch plan: the task row only (including its log). The associations (device,
	 * group, domain, debug log, device list) are left uninitialized: only the ID of the
	 * device, group and result device can be read once the session is closed.</p>
	 *
	 * @param session the session
	 * @param id the task ID
	 * @return the task, of its concrete class, or null if there is no such task
	 */
	public Task find(Session session, long id) {
		return session.get(Task.class, id);
	}

	/**
	 * Finds a device, without any of its associations.
	 *
	 * @param session the session
	 * @param id the device ID
	 * @return the device, or null if there is no such device
	 */
	public Device findDevice(Session session, Long id) {
		return session.get(Device.class, id);
	}

	/**
	 * Finds a device group, without any of its associations.
	 *
	 * @param session the session
	 * @param id the group ID
	 * @return the group, or null if there is no such group
	 */
	public DeviceGroup findGroup(Session session, Long id) {
		return session.get(DeviceGroup.class, id);
	}

	/**
	 * Finds a domain.
	 *
	 * @param session the session
	 * @param id the domain ID
	 * @return the domain, or null if there is no such domain
	 */
	public Domain findDomain(Session session, Long id) {
		return session.get(Domain.class, id);
	}

	/**
	 * Lists the tasks in their summary form: the most recent first, or in the order of
	 * their siblings when listing the children of a parent task.
	 *
	 * @param session the session
	 * @param filter the criteria
	 * @param page the window on the result
	 * @return the task summaries
	 */
	public List<TaskSummary> findSummaries(Session session, TaskFilter filter, Page page) {
		StringBuilder hql = new StringBuilder(SUMMARY_SELECT + "from Task t where (1 = 1)");
		Map<String, Object> params = new HashMap<>();
		if (filter.statuses() != null && !filter.statuses().isEmpty()) {
			hql.append(" and t.status in :statuses");
			params.put("statuses", filter.statuses());
		}
		if (filter.types() != null && !filter.types().isEmpty()) {
			hql.append(" and type(t) in :classes");
			params.put("classes", resolveTaskClasses(filter.types()));
		}
		if (filter.after() != null) {
			hql.append(" and (((t.executionDate is not null) and (t.executionDate >= :startDate)) "
				+ "or ((t.executionDate is null) and (t.changeDate >= :startDate)))");
			params.put("startDate", new Date(filter.after()));
		}
		if (filter.before() != null) {
			hql.append(" and (((t.executionDate is not null) and (t.executionDate <= :endDate)) "
				+ "or ((t.executionDate is null) and (t.changeDate < :endDate)))");
			params.put("endDate", new Date(filter.before()));
		}
		if (filter.parentTaskId() != null) {
			hql.append(" and t.parentTaskId = :parentTaskId");
			params.put("parentTaskId", filter.parentTaskId());
		}
		hql.append(filter.parentTaskId() != null ? " order by t.childOrder asc" : " order by id desc");
		Query<TaskSummary> query = session.createQuery(hql.toString(), TaskSummary.class);
		for (Entry<String, Object> param : params.entrySet()) {
			query.setParameter(param.getKey(), param.getValue());
		}
		page.apply(query);
		return query.list();
	}

	/**
	 * Lists the tasks of a device in their summary form: the running, waiting, scheduled
	 * and new ones first, then the most recently executed or changed ones.
	 *
	 * @param session the session
	 * @param deviceId the device ID
	 * @param page the window on the result
	 * @return the task summaries
	 */
	public List<TaskSummary> findSummariesByDevice(Session session, long deviceId, Page page) {
		List<String> conditions = new ArrayList<>();
		for (Class<? extends Task> taskClass : Task.getTaskClasses()) {
			if (DeviceBasedTask.class.isAssignableFrom(taskClass)) {
				conditions.add(String.format(
					"t.id IN (select sub.id from %s sub where sub.device.id = :deviceId)",
					taskClass.getSimpleName()));
			}
		}
		if (conditions.isEmpty()) {
			return List.of();
		}
		String hql = SUMMARY_SELECT
			+ "from Task t where "
			+ String.join(" or ", conditions) + " "
			+ "order by "
			+ "case t.status "
			+ "when :running then 1 "
			+ "when :waiting then 2 "
			+ "when :scheduled then 3 "
			+ "when :newStatus then 4 "
			+ "else 10 end asc, "
			+ "coalesce(t.executionDate, t.changeDate) desc nulls first";
		Query<TaskSummary> query = session.createQuery(hql, TaskSummary.class)
			.setParameter("deviceId", deviceId)
			.setParameter("running", Task.Status.RUNNING)
			.setParameter("waiting", Task.Status.WAITING)
			.setParameter("scheduled", Task.Status.SCHEDULED)
			.setParameter("newStatus", Task.Status.NEW);
		page.apply(query);
		return query.list();
	}

	/**
	 * Counts the tasks by status.
	 *
	 * @param session the session
	 * @param parentTaskId the ID of a parent task whose children are counted, or null for all the tasks
	 * @return the count of the statuses having tasks
	 */
	public List<TaskStatusCount> countByStatus(Session session, Long parentTaskId) {
		String hql = STATUS_COUNT_SELECT
			+ (parentTaskId != null ? " where t.parentTaskId = :parentTaskId" : "")
			+ " group by t.status";
		Query<TaskStatusCount> query = session.createQuery(hql, TaskStatusCount.class);
		if (parentTaskId != null) {
			query.setParameter("parentTaskId", parentTaskId);
		}
		return query.list();
	}

	/**
	 * Counts the tasks of the given statuses by status, among the ones executed (or changed,
	 * if never executed) in a time window.
	 *
	 * @param session the session
	 * @param statuses the statuses
	 * @param from the start of the window (included)
	 * @param to the end of the window (excluded)
	 * @return the count of the statuses having tasks
	 */
	public List<TaskStatusCount> countByStatus(Session session, Collection<Task.Status> statuses, Date from, Date to) {
		return session
			.createQuery(
				STATUS_COUNT_SELECT + " "
					+ "where t.status in :statuses and ("
					+ "((t.executionDate is not null) and (t.executionDate >= :bucketStart) and (t.executionDate < :bucketEnd)) "
					+ "or ((t.executionDate is null) and (t.changeDate >= :bucketStart) and (t.changeDate < :bucketEnd))) "
					+ "group by t.status",
				TaskStatusCount.class)
			.setParameter("statuses", statuses)
			.setParameter("bucketStart", from)
			.setParameter("bucketEnd", to)
			.list();
	}

	/**
	 * Counts the tasks of a given type and of the given statuses, among the ones executed (or
	 * changed, if never executed) in a time window.
	 *
	 * @param session the session
	 * @param taskClass the task type
	 * @param statuses the statuses
	 * @param from the start of the window (included)
	 * @param to the end of the window (excluded)
	 * @return the number of tasks
	 */
	public long countByType(Session session, Class<? extends Task> taskClass, Collection<Task.Status> statuses, Date from, Date to) {
		Long count = session
			.createQuery(
				"select count(t.id) from Task t where type(t) = :class and t.status in :statuses and ("
					+ "((t.executionDate is not null) and (t.executionDate >= :rangeStart) and (t.executionDate < :rangeEnd)) "
					+ "or ((t.executionDate is null) and (t.changeDate >= :rangeStart) and (t.changeDate < :rangeEnd)))",
				Long.class)
			.setParameter("class", taskClass)
			.setParameter("statuses", statuses)
			.setParameter("rangeStart", from)
			.setParameter("rangeEnd", to)
			.uniqueResult();
		return count == null ? 0L : count;
	}

	// Resolves task type simple class names (as used in the "type" discriminator) to the
	// corresponding task classes, ignoring names that don't match any known type.
	private static Set<Class<? extends Task>> resolveTaskClasses(Set<String> types) {
		Set<Class<? extends Task>> classes = new HashSet<>();
		for (Class<? extends Task> taskClass : Task.getTaskClasses()) {
			if (types.contains(taskClass.getSimpleName())) {
				classes.add(taskClass);
			}
		}
		return classes;
	}
}
