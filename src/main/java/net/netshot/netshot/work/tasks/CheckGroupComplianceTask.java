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
package net.netshot.netshot.work.tasks;

import java.util.List;

import org.hibernate.CacheMode;
import org.hibernate.Hibernate;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.quartz.JobKey;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.database.Database;
import net.netshot.netshot.device.Device;
import net.netshot.netshot.device.DeviceGroup;
import net.netshot.netshot.device.DynamicDeviceGroup;
import net.netshot.netshot.rest.RestViews.DefaultView;
import net.netshot.netshot.work.Task;

/**
 * This task checks the configuration compliance status of a group of devices
 * (either a real device group, or a one-time device list).
 */
@Entity
@DiscriminatorValue("CheckGroupComplianceTask")
@Slf4j
public final class CheckGroupComplianceTask extends Task implements GroupBasedTask, DeviceListBasedTask {

	/**
	 * Instantiates a new check group compliance task.
	 */
	public CheckGroupComplianceTask() {

	}

	/**
	 * Instantiates a new check group compliance task, targeting a real device group.
	 *
	 * @param group the group
	 * @param comments the comments
	 * @param author the author
	 */
	public CheckGroupComplianceTask(DeviceGroup group, String comments, String author) {
		super(comments, group.getName(), author);
		this.setDeviceGroup(group);
	}

	/**
	 * Instantiates a new check group compliance task, targeting a one-time device list.
	 *
	 * @param devices the ordered list of devices
	 * @param comments the comments
	 * @param author the author
	 */
	public CheckGroupComplianceTask(List<Device> devices, String comments, String author) {
		super(comments, String.format("%d device(s)", devices.size()), author);
		this.setDeviceList(devices);
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#getTaskDescription()
	 */
	@Override
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public String getTaskDescription() {
		return "Group compliance check";
	}

	/**
	 * Get the ID of the associate group.
	 * @return the ID of the group
	 */
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public long getDeviceGroupId() {
		if (this.getDeviceGroup() == null) {
			return 0;
		}
		return this.getDeviceGroup().getId();
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#prepare()
	 */
	@Override
	public void prepare(Session session) {
		Hibernate.initialize(this.getDeviceGroup());
		if (this.getDeviceGroup() == null) {
			Hibernate.initialize(this.getDeviceListMembers());
		}
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		CheckGroupComplianceTask task = (CheckGroupComplianceTask) super.clone();
		task.setDeviceGroup(this.getDeviceGroup());
		if (this.getDeviceGroup() == null) {
			// Mirrors prepare(), which only initializes the lazy deviceListMembers
			// collection in this same case -- device group and device list are
			// mutually exclusive, so there is nothing to copy (and nothing safe to
			// touch) otherwise.
			task.setDeviceList(this.getDeviceList());
		}
		return task;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.work.Task#run()
	 */
	@Override
	public void run() {
		DeviceGroup group = this.getDeviceGroup();
		List<Device> devices = group == null ? this.getDeviceList() : null;
		if (group == null && (devices == null || devices.isEmpty())) {
			this.logger.info("Neither a device group nor a device list is set, the task will be cancelled.");
			this.status = Status.CANCELLED;
			return;
		}
		log.debug("Task {}. Starting check compliance task for {}.", this.getId(),
			group == null ? devices.size() + " listed device(s)" : "group " + group.getId());

		List<Long> deviceIds = group == null ? devices.stream().map(Device::getId).toList() : null;

		Session session = Database.getSession();
		try {
			List<Policy> policies =
				session.createQuery("select p from Policy p", Policy.class).list();

			session.beginTransaction();
			if (group != null) {
				session
					.createMutationQuery(
						"delete from CheckResult c where c.key.device.id in "
						+ "(select dm1.key.device.id as id from DeviceGroup g1 join g1.cachedMemberships dm1 where dm1.key.group.id = :id)")
					.setParameter("id", group.getId())
					.executeUpdate();
			}
			else {
				session
					.createMutationQuery("delete from CheckResult c where c.key.device.id in :ids")
					.setParameter("ids", deviceIds)
					.executeUpdate();
			}
			for (Policy policy : policies) {
				// Get devices which are part of the target group/list and which are in a group which the policy is applied to
				ScrollableResults<Device> scrolledDevices;
				if (group != null) {
					scrolledDevices = session
						.createQuery(
							"select d from Device d join d.groupMemberships gm where gm.key.group.id = :groupId and d in "
							+ "(select dm1.key.device from Policy p join p.targetGroups g1 join g1.cachedMemberships dm1 where p.id = :policyId)",
							Device.class)
						.setParameter("groupId", group.getId())
						.setParameter("policyId", policy.getId())
						.setCacheMode(CacheMode.IGNORE)
						.scroll(ScrollMode.FORWARD_ONLY);
				}
				else {
					scrolledDevices = session
						.createQuery(
							"select d from Device d where d.id in :deviceIds and d in "
							+ "(select dm1.key.device from Policy p join p.targetGroups g1 join g1.cachedMemberships dm1 where p.id = :policyId)",
							Device.class)
						.setParameter("deviceIds", deviceIds)
						.setParameter("policyId", policy.getId())
						.setCacheMode(CacheMode.IGNORE)
						.scroll(ScrollMode.FORWARD_ONLY);
				}
				while (scrolledDevices.next()) {
					Device device = scrolledDevices.get();
					this.logger.info("Checking configuration compliance of device {} ({})", device.getName(), device.getId());
					policy.check(device, session, this.logger);
					session.persist(device);
					session.flush();
					session.evict(device);
				}
			}
			session.getTransaction().commit();
			this.status = Status.SUCCESS;
		}
		catch (Exception e) {
			Database.rollbackSilently(session);
			log.error("Task {}. Error while checking compliance.", this.getId(), e);
			this.logger.error("Error while checking compliance: {}", e.getMessage());
			this.status = Status.FAILURE;
			return;
		}
		finally {
			session.close();
		}

		log.debug("Task {}. Request to refresh all the groups after compliance check.", this.getId());
		DynamicDeviceGroup.refreshAllGroups();
		log.debug("Task {}. Group refreshing done.", this.getId());
	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.work.Task#getIdentity()
	 */
	@Override
	@Transient
	public JobKey getIdentity() {
		return new JobKey(String.format("Task_%d", this.getId()),
			String.format("CheckGroupCompliance_%d", this.getDeviceGroupId()));
	}

}
