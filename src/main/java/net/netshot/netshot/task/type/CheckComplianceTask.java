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
package net.netshot.netshot.task.type;

import java.util.List;

import org.hibernate.Session;
import org.quartz.JobKey;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.compliance.HardwareRule;
import net.netshot.netshot.compliance.Policy;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.SoftwareRule.ConformanceLevel;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DynamicDeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.task.Task;

/**
 * This task checks the configuration compliance of a device.
 */
@Entity
@DiscriminatorValue("CheckComplianceTask")
@Slf4j
public final class CheckComplianceTask extends Task implements DeviceBasedTask {

	/**
	 * Instantiates a new check compliance task.
	 */
	protected CheckComplianceTask() {
	}

	/**
	 * Instantiates a new check compliance task.
	 *
	 * @param device the device
	 * @param comments the comments
	 * @param author the author
	 */
	public CheckComplianceTask(Device device, String comments, String author) {
		super(comments, device.getLastConfig() == null ? device.getMgmtAddress() : device.getName(), author);
		this.device = device;
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#run()
	 */
	@Override
	public void run() {
		log.debug("Task {}. Starting check compliance task for device {}.", this.getId(),
			device == null ? "null" : device.getId());
		if (device == null) {
			this.logger.info("The device doesn't exist, the task will be cancelled.");
			this.status = Status.CANCELLED;
			return;
		}

		Session session = Database.getSession();
		try {
			session.beginTransaction();
			// Delete previous results
			session
				.createMutationQuery("delete from CheckResult c where c.key.device.id = :id")
				.setParameter("id", this.device.getId())
				.executeUpdate();
			// Start over from a fresh device from DB
			device = session.get(Device.class, device.getId());
			this.logger.info("Check compliance task for device {} ({}).",
				device.getName(), device.getMgmtAddress());
			if (this.device.getLastConfig() == null) {
				log.info("Task {}. Unable to fetch the device with its last config... has it been captured at least once?",
					this.getId());
				throw new Exception("No last config for this device. Has it been captured at least once?");
			}
			List<Policy> policies = session
				.createQuery("select distinct p from Policy p join p.targetGroups g "
					+ "join g.cachedMemberships dm with dm.key.device.id = :id", Policy.class)
				.setParameter("id", this.device.getId())
				.list();

			this.logger.info("Checking configuration compliance of device {} ({})...",
				this.device.getName(), this.device.getId());
			for (Policy policy : policies) {
				policy.check(device, session, this.logger);
			}
			session.persist(this.device);
			session.flush();
			List<SoftwareRule> softwareRules = session
				.createQuery("select sr from SoftwareRule sr where (sr.targetGroup is null) or "
					+ "sr.targetGroup in (select g from DeviceGroup g join g.cachedMemberships dm "
					+ "with dm.key.device.id = :id) order by sr.priority asc", SoftwareRule.class)
				.setParameter("id", this.device.getId())
				.list();
			device.setSoftwareLevel(ConformanceLevel.UNKNOWN);
			for (SoftwareRule rule : softwareRules) {
				rule.check(device);
				if (device.getSoftwareLevel() != ConformanceLevel.UNKNOWN) {
					break;
				}
			}
			session.persist(this.device);
			List<HardwareRule> hardwareRules = session
				.createQuery("select hr from HardwareRule hr", HardwareRule.class)
				.list();
			device.resetEoX();
			for (HardwareRule rule : hardwareRules) {
				rule.check(device);
			}
			session.persist(device);
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

		log.debug("Task {}. Request to refresh all the groups for the device after compliance check.", this.getId());
		DynamicDeviceGroup.refreshAllGroupsOfOneDevice(device);

	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getTaskDescription()
	 */
	@Override
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public String getTaskDescription() {
		return "Device compliance check";
	}

	/**
	 * Get the ID of the device.
	 * 
	 * @return the ID of the device
	 */
	@XmlElement
	@JsonView(DefaultView.class)
	@Transient
	public long getDeviceId() {
		if (this.device == null) {
			return 0;
		}
		return this.device.getId();
	}

	/*(non-Javadoc)
	 * @see net.netshot.netshot.task.Task#clone()
	 */
	@Override
	public Object clone() throws CloneNotSupportedException {
		CheckComplianceTask task = (CheckComplianceTask) super.clone();
		task.setDevice(this.device);
		return task;
	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getIdentity()
	 */
	@Override
	@Transient
	public JobKey getIdentity() {
		return new JobKey(String.format("Task_%d", this.getId()),
			String.format("CheckCompliance_%d", this.getDeviceId()));
	}

	/*
	 * (non-Javadoc)
	 * @see net.netshot.netshot.task.Task#getRunnerHash()
	 */
	@Override
	@Transient
	public long getRunnerHash() {
		if (this.getDevice() == null) {
			return 0;
		}
		return this.getDevice().getId();
	}

}
