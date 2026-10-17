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
package net.netshot.netshot.task.handler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.hibernate.CacheMode;
import org.hibernate.HibernateException;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.Query;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.config.Config;
import net.netshot.netshot.inventory.config.attribute.ConfigAttribute;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.group.DeviceGroup;
import net.netshot.netshot.platform.database.Database;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.type.DeviceBasedTask;
import net.netshot.netshot.task.type.PurgeDatabaseTask;

/**
 * Cleans up the database.
 */
@Slf4j
public class PurgeDatabaseHandler implements TaskHandler<PurgeDatabaseTask> {


	@Override
	public Class<PurgeDatabaseTask> taskType() {
		return PurgeDatabaseTask.class;
	}

	@Override
	public void run(PurgeDatabaseTask task) {
		DeviceGroup group = task.getDeviceGroup();
		List<Device> deviceList = group == null ? task.getDeviceList() : null;
		List<Long> deviceIds = (deviceList != null && !deviceList.isEmpty())
			? deviceList.stream().map(Device::getId).toList() : null;
		log.debug("Task {}. Starting cleanup process (group {}).", task.getId(),
			group == null ? (deviceIds == null ? "all" : deviceIds.size() + " listed device(s)") : group.getId());

		int days = task.getDays();
		int configDays = task.getConfigDays();
		int configSize = task.getConfigSize();
		int configKeepDays = task.getConfigKeepDays();
		int moduleDays = task.getModuleDays();

		if (days > 0) {
			Session session = Database.getSession();
			try {
				session.beginTransaction();
				log.trace("Task {}. Cleaning up tasks finished more than {} days ago...", task.getId(), days);
				task.logger.info("Cleaning up tasks more than {} days ago...", days);
				Calendar when = Calendar.getInstance();
				when.add(Calendar.DATE, -1 * days);

				int count = 0;
				if (group == null && deviceIds == null) {
					count += session.createMutationQuery(
						"delete Task t where (t.status = :cancelled or t.status = :failure "
							+ "or t.status = :success) and (t.executionDate < :when)")
						.setParameter("cancelled", Task.Status.CANCELLED)
						.setParameter("failure", Task.Status.FAILURE)
						.setParameter("success", Task.Status.SUCCESS)
						.setParameter("when", when.getTime())
						.executeUpdate();
				}
				else {
					for (Class<? extends Task> taskClass : Task.getTaskClasses()) {
						if (DeviceBasedTask.class.isAssignableFrom(taskClass)) {
							MutationQuery deleteQuery;
							if (group != null) {
								deleteQuery = session.createMutationQuery(
									String.format(
										"delete %1$s t where t in "
											+ "(select t from %1$s join t.device d join d.groupMemberships gm "
											+ "where gm.key.group = :group and (t.status = :cancelled or t.status = :failure or t.status = :success) "
											+ "and (t.executionDate < :when))", taskClass.getSimpleName()))
									.setParameter("group", group);
							}
							else {
								deleteQuery = session.createMutationQuery(
									String.format(
										"delete %1$s t where t in "
											+ "(select t from %1$s join t.device d "
											+ "where d.id in :deviceIds and (t.status = :cancelled or t.status = :failure or t.status = :success) "
											+ "and (t.executionDate < :when))", taskClass.getSimpleName()))
									.setParameter("deviceIds", deviceIds);
							}
							count += deleteQuery
								.setParameter("cancelled", Task.Status.CANCELLED)
								.setParameter("failure", Task.Status.FAILURE)
								.setParameter("success", Task.Status.SUCCESS)
								.setParameter("when", when.getTime())
								.executeUpdate();
						}
					}
				}
				session.getTransaction().commit();
				log.trace("Task {}. Cleaning up done on tasks, {} entries affected.", task.getId(), count);
				task.logger.info("Cleaning up done on tasks, {} entries affected.", count);
			}
			catch (HibernateException e) {
				Database.rollbackSilently(session);
				log.error("Task {}. Database error while purging the old tasks from the database.", task.getId(), e);
				task.logger.error("Database error during the task purge.");
				task.setStatus(Status.FAILURE);
				return;
			}
			catch (Exception e) {
				Database.rollbackSilently(session);
				log.error("Task {}. Error while purging the old tasks from the database.", task.getId(), e);
				task.logger.error("Error during the task purge.");
				task.setStatus(Status.FAILURE);
				return;
			}
			finally {
				session.close();
			}
		}

		if (configDays > 0) {
			Session session = Database.getSession();
			try {
				session.beginTransaction();
				log.trace("Task {}. Cleaning up configurations taken more than {} days ago...", task.getId(), configDays);
				task.logger.info("Cleaning up configurations older than {} days...", configDays);
				Calendar when = Calendar.getInstance();
				when.add(Calendar.DATE, -1 * configDays);
				Query<Config> query;
				if (configSize > 0) {
					if (group == null && deviceIds == null) {
						query = session
							.createQuery(
								"select c from Config c join c.attributes a where (a.class = ConfigLongTextAttribute or a.class = ConfigBinaryFileAttribute) "
									+ "group by c.id having ((max(length(a.longText.text)) > :size) or (max(a.fileSize) > :size)) and (c.changeDate < :when) "
									+ "order by c.device asc, c.changeDate desc", Config.class)
							.setParameter("size", configSize * 1024);
					}
					else if (group != null) {
						query = session
							.createQuery(
								"select c from Config c join c.device d join d.groupMemberships gm join c.attributes a "
									+ "where gm.key.group = :group and (a.class = ConfigLongTextAttribute or a.class = ConfigBinaryFileAttribute) "
									+ "group by c.id having ((max(length(a.longText.text)) > :size) or (max(a.fileSize) > :size)) and (c.changeDate < :when) "
									+ "order by c.device asc, c.changeDate desc", Config.class)
							.setParameter("group", group)
							.setParameter("size", configSize * 1024);
					}
					else {
						query = session
							.createQuery(
								"select c from Config c join c.device d join c.attributes a "
									+ "where d.id in :deviceIds and (a.class = ConfigLongTextAttribute or a.class = ConfigBinaryFileAttribute) "
									+ "group by c.id having ((max(length(a.longText.text)) > :size) or (max(a.fileSize) > :size)) and (c.changeDate < :when) "
									+ "order by c.device asc, c.changeDate desc", Config.class)
							.setParameter("deviceIds", deviceIds)
							.setParameter("size", configSize * 1024);
					}
				}
				else if (group == null && deviceIds == null) {
					query = session.createQuery(
						"select c from Config c where (c.changeDate < :when) order by c.device asc, c.changeDate desc", Config.class);
				}
				else if (group != null) {
					query = session.createQuery(
						"select c from Config c join c.device d join d.groupMemberships gm "
							+ "where gm.key.group = :group and (c.changeDate < :when) "
							+ "order by c.device asc, c.changeDate desc", Config.class)
						.setParameter("group", group);
				}
				else {
					query = session.createQuery(
						"select c from Config c join c.device d "
							+ "where d.id in :deviceIds and (c.changeDate < :when) "
							+ "order by c.device asc, c.changeDate desc", Config.class)
						.setParameter("deviceIds", deviceIds);
				}
				ScrollableResults<Config> configs = query
					.setParameter("when", when.getTime())
					.setCacheMode(CacheMode.IGNORE)
					.scroll(ScrollMode.FORWARD_ONLY);
				long dontDeleteDevice = -1;
				Date dontDeleteBefore = null;
				int count = 0;
				List<Path> toDeletePathes = new ArrayList<>();
				while (configs.next()) {
					try {
						Config config = configs.get();
						if ((config.getDevice().getLastConfig() != null && config.getDevice().getLastConfig().getId() == config.getId())
							|| (dontDeleteBefore != null && config.getChangeDate().before(dontDeleteBefore))
							|| (configKeepDays > 0 && dontDeleteDevice != config.getDevice().getId())) {
							if (configKeepDays > 0) {
								Calendar limitCalendar = Calendar.getInstance();
								limitCalendar.setTime(config.getChangeDate());
								limitCalendar.add(Calendar.DATE, -1 * configKeepDays);
								dontDeleteBefore = limitCalendar.getTime();
							}
						}
						else {
							for (ConfigAttribute attribute : config.getAttributes()) {
								if (attribute instanceof ConfigBinaryFileAttribute cbfa) {
									toDeletePathes.add(cbfa.getFilePath());
								}
							}
							session.remove(config);
							if (++count % 30 == 0) {
								session.flush();
								session.clear();
							}
						}
						dontDeleteDevice = config.getDevice().getId();
					}
					catch (NullPointerException e1) {
					}
				}
				session.getTransaction().commit();
				log.trace("Task {}. Cleaning up done on configurations, {} entries affected.", task.getId(), count);
				task.logger.info("Cleaning up done on configurations, {} entries affected.", count);
				for (Path toDeletePath : toDeletePathes) {
					try {
						Files.delete(toDeletePath);
					}
					catch (Exception e) {
						log.error("Error while removing binary file {}", toDeletePath, e);
					}
				}
			}
			catch (HibernateException e) {
				Database.rollbackSilently(session);
				log.error("Task {}. Database error while purging the old configurations from the database.",
					task.getId(), e);
				task.logger.error("Database error during the configuration purge.");
				task.setStatus(Status.FAILURE);
				return;
			}
			catch (Exception e) {
				Database.rollbackSilently(session);
				log.error("Task {}. Error while purging the old configurations from the database.",
					task.getId(), e);
				task.logger.error("Error during the configuration purge.");
				task.setStatus(Status.FAILURE);
				return;
			}
			finally {
				session.close();
			}
		}


		if (moduleDays > 0) {
			Session session = Database.getSession();
			try {
				session.beginTransaction();
				log.trace("Task {}. Cleaning up hardware modules removed more than {} days ago...", task.getId(), moduleDays);
				task.logger.info("Cleaning up hardware modules removed more than {} days...", moduleDays);
				Calendar when = Calendar.getInstance();
				when.add(Calendar.DATE, -1 * moduleDays);

				final MutationQuery query;

				if (group == null && deviceIds == null) {
					query = session
						.createMutationQuery("delete from Module m where m.removed and m.lastSeenDate <= :when")
						.setParameter("when", when.getTime());
				}
				else if (group != null) {
					query = session
						.createMutationQuery("delete from Module m where m in "
							+ "(select m from Module m join m.device d join d.groupMemberships gm "
							+ "where gm.key.group = :group and m.removed and m.lastSeenDate <= :when)")
						.setParameter("group", group)
						.setParameter("when", when.getTime());
				}
				else {
					query = session
						.createMutationQuery("delete from Module m where m in "
							+ "(select m from Module m join m.device d "
							+ "where d.id in :deviceIds and m.removed and m.lastSeenDate <= :when)")
						.setParameter("deviceIds", deviceIds)
						.setParameter("when", when.getTime());
				}

				int count = query.executeUpdate();
				session.getTransaction().commit();
				log.trace("Task {}. Cleaning up done on modules, {} entries affected.", task.getId(), count);
				task.logger.info("Cleaning up done on modules, {} entries affected.", count);

			}
			catch (Exception e) {
				Database.rollbackSilently(session);
				log.error("Task {}. Error while purging the old modules from the database.",
					task.getId(), e);
				task.logger.error("Error during the module purge.");
				task.setStatus(Status.FAILURE);
				return;
			}
			finally {
				session.close();
			}
		}

		if (task.isRemoveOrphanFiles()) {
			Session session = Database.getSession();
			try {
				session.beginTransaction();
				log.trace("Task {}. Cleaning up orphan binary attribute files...", task.getId());
				task.logger.info("Cleaning up orphan binary attribute files...");
				Set<String> knownUids = new HashSet<>(session
					.createQuery("select cfa.uid from ConfigBinaryFileAttribute cfa", String.class)
					.list());
				session.getTransaction().commit();

				int count = 0;
				Path storageFolder = ConfigBinaryFileAttribute.SETTINGS.getStorageFolderPath();
				if (storageFolder != null) {
					Instant pendingSafeBefore = Instant.now().minus(24, ChronoUnit.HOURS);
					try (Stream<Path> entries = Files.list(storageFolder)) {
						for (Path entry : entries.toList()) {
							try {
								String fileName = entry.getFileName().toString();
								if (!fileName.endsWith(".data")) {
									continue;
								}
								boolean pending = fileName.startsWith(".tmp.");
								String withoutSuffix = fileName.substring(0, fileName.length() - ".data".length());
								String[] parts = withoutSuffix.split("_");
								String uid = parts[parts.length - 1];
								try {
									UUID.fromString(uid);
								}
								catch (IllegalArgumentException e) {
									continue; // Not a recognized binary attribute file name
								}
								if (knownUids.contains(uid)) {
									// Referenced by a real attribute row - never delete,
									// even if it's still sitting at its pending name.
									continue;
								}
								if (pending && !Files.getLastModifiedTime(entry).toInstant().isBefore(pendingSafeBefore)) {
									// Too recent - could be a snapshot genuinely in progress.
									continue;
								}
								Files.delete(entry);
								count++;
							}
							catch (IOException e) {
								log.error("Error while removing orphan binary file {}", entry, e);
							}
						}
					}
				}
				log.trace("Task {}. Cleaning up done on orphan binary files, {} entries affected.", task.getId(), count);
				task.logger.info("Cleaning up done on orphan binary files, {} entries affected.", count);
			}
			catch (HibernateException e) {
				Database.rollbackSilently(session);
				log.error("Task {}. Database error while purging orphan binary files.", task.getId(), e);
				task.logger.error("Database error during the orphan binary file purge.");
				task.setStatus(Status.FAILURE);
				return;
			}
			catch (Exception e) {
				Database.rollbackSilently(session);
				log.error("Task {}. Error while purging orphan binary files.", task.getId(), e);
				task.logger.error("Error during the orphan binary file purge.");
				task.setStatus(Status.FAILURE);
				return;
			}
			finally {
				session.close();
			}
		}

		task.setStatus(Status.SUCCESS);
		log.trace("Task {}. Cleaning up process finished.", task.getId());
	}

}
