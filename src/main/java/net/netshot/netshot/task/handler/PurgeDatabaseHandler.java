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

import static net.netshot.netshot.platform.database.schema.Tables.CONFIG;
import static net.netshot.netshot.platform.database.schema.Tables.CONFIG_ATTRIBUTE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE;
import static net.netshot.netshot.platform.database.schema.Tables.DEVICE_GROUP_CACHED_DEVICES;
import static net.netshot.netshot.platform.database.schema.Tables.LONG_TEXT_CONFIGURATION;
import static net.netshot.netshot.platform.database.schema.Tables.MODULE;
import static net.netshot.netshot.platform.database.schema.Tables.TASK;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record4;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;

import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.inventory.config.attribute.ConfigBinaryFileAttribute;
import net.netshot.netshot.inventory.device.DeviceRepository;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.Task;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.task.TaskHandler;
import net.netshot.netshot.task.TaskRepository;
import net.netshot.netshot.task.TaskRun;
import net.netshot.netshot.task.type.PurgeDatabaseTask;

/**
 * Cleans up the database.
 */
@Slf4j
public class PurgeDatabaseHandler implements TaskHandler<PurgeDatabaseTask> {

	private final TaskRepository taskRepository = new TaskRepository();

	/** The transaction support. */
	private final Transactions transactions;

	/**
	 * Instantiates the handler.
	 *
	 * @param transactions the transaction support
	 */
	public PurgeDatabaseHandler(Transactions transactions) {
		this.transactions = transactions;
	}

	@Override
	public Class<PurgeDatabaseTask> taskType() {
		return PurgeDatabaseTask.class;
	}

	/**
	 * The condition on the devices targeted by the purge.
	 *
	 * @param deviceId the device ID column to filter
	 * @param groupId the target group, or null
	 * @param deviceIds the target devices, or null
	 * @return the condition (none for all the devices)
	 */
	private static Condition targetDevices(org.jooq.Field<Long> deviceId, Long groupId, List<Long> deviceIds) {
		if (groupId != null) {
			return deviceId.in(DSL.select(DEVICE_GROUP_CACHED_DEVICES.CACHED_DEVICES).from(DEVICE_GROUP_CACHED_DEVICES)
				.where(DEVICE_GROUP_CACHED_DEVICES.OWNER_GROUPS.eq(groupId)));
		}
		else if (deviceIds != null) {
			return deviceId.in(deviceIds);
		}
		return DSL.noCondition();
	}

	@Override
	public void run(Task task, PurgeDatabaseTask spec, TaskRun run) {
		Long groupId = spec.deviceGroupId();
		List<Long> deviceList = groupId == null ? spec.deviceIds() : null;
		List<Long> deviceIds = (deviceList != null && !deviceList.isEmpty()) ? deviceList : null;
		log.debug("Task {}. Starting cleanup process (group {}).", task.id(),
			groupId == null ? (deviceIds == null ? "all" : deviceIds.size() + " listed device(s)") : groupId);

		int days = spec.days();
		int configDays = spec.configDays();
		int configSize = spec.configSize();
		int configKeepDays = spec.configKeepDays();
		int moduleDays = spec.moduleDays();

		if (days > 0) {
			try {
				log.trace("Task {}. Cleaning up tasks finished more than {} days ago...", task.id(), days);
				run.logger.info("Cleaning up tasks more than {} days ago...", days);
				Calendar when = Calendar.getInstance();
				when.add(Calendar.DATE, -1 * days);
				Condition deviceCondition = groupId == null && deviceIds == null ? null : targetDevices(TASK.DEVICE, groupId, deviceIds);
				int count = this.transactions.write(dsl -> this.taskRepository.deleteFinished(dsl, when.toInstant(), deviceCondition));
				log.trace("Task {}. Cleaning up done on tasks, {} entries affected.", task.id(), count);
				run.logger.info("Cleaning up done on tasks, {} entries affected.", count);
			}
			catch (DataAccessException e) {
				log.error("Task {}. Database error while purging the old tasks from the database.", task.id(), e);
				run.logger.error("Database error during the task purge.");
				run.setStatus(Status.FAILURE);
				return;
			}
			catch (Exception e) {
				log.error("Task {}. Error while purging the old tasks from the database.", task.id(), e);
				run.logger.error("Error during the task purge.");
				run.setStatus(Status.FAILURE);
				return;
			}
		}

		if (configDays > 0) {
			try {
				log.trace("Task {}. Cleaning up configurations taken more than {} days ago...", task.id(), configDays);
				run.logger.info("Cleaning up configurations older than {} days...", configDays);
				Calendar when = Calendar.getInstance();
				when.add(Calendar.DATE, -1 * configDays);
				List<Path> toDeletePathes = new ArrayList<>();
				int count = this.transactions.write(dsl -> this.purgeConfigs(dsl, when.toInstant(), configSize, configKeepDays,
					groupId, deviceIds, toDeletePathes));
				log.trace("Task {}. Cleaning up done on configurations, {} entries affected.", task.id(), count);
				run.logger.info("Cleaning up done on configurations, {} entries affected.", count);
				for (Path toDeletePath : toDeletePathes) {
					try {
						Files.delete(toDeletePath);
					}
					catch (Exception e) {
						log.error("Error while removing binary file {}", toDeletePath, e);
					}
				}
			}
			catch (DataAccessException e) {
				log.error("Task {}. Database error while purging the old configurations from the database.",
					task.id(), e);
				run.logger.error("Database error during the configuration purge.");
				run.setStatus(Status.FAILURE);
				return;
			}
			catch (Exception e) {
				log.error("Task {}. Error while purging the old configurations from the database.",
					task.id(), e);
				run.logger.error("Error during the configuration purge.");
				run.setStatus(Status.FAILURE);
				return;
			}
		}

		if (moduleDays > 0) {
			try {
				log.trace("Task {}. Cleaning up hardware modules removed more than {} days ago...", task.id(), moduleDays);
				run.logger.info("Cleaning up hardware modules removed more than {} days...", moduleDays);
				Calendar when = Calendar.getInstance();
				when.add(Calendar.DATE, -1 * moduleDays);
				int count = this.transactions.write(dsl -> dsl.deleteFrom(MODULE)
					.where(MODULE.REMOVED.isTrue(), MODULE.LAST_SEEN_DATE.le(when.toInstant()), targetDevices(MODULE.DEVICE, groupId, deviceIds))
					.execute());
				log.trace("Task {}. Cleaning up done on modules, {} entries affected.", task.id(), count);
				run.logger.info("Cleaning up done on modules, {} entries affected.", count);
			}
			catch (Exception e) {
				log.error("Task {}. Error while purging the old modules from the database.",
					task.id(), e);
				run.logger.error("Error during the module purge.");
				run.setStatus(Status.FAILURE);
				return;
			}
		}

		if (spec.removeOrphanFiles()) {
			try {
				log.trace("Task {}. Cleaning up orphan binary attribute files...", task.id());
				run.logger.info("Cleaning up orphan binary attribute files...");
				Set<String> knownUids = new HashSet<>(this.transactions.write(dsl -> dsl.select(CONFIG_ATTRIBUTE.UID)
					.from(CONFIG_ATTRIBUTE).where(CONFIG_ATTRIBUTE.TYPE.eq("F")).fetch(CONFIG_ATTRIBUTE.UID)));

				int count = 0;
				Path storageFolder = ConfigBinaryFileAttribute.settings().storageFolderPath();
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
				log.trace("Task {}. Cleaning up done on orphan binary files, {} entries affected.", task.id(), count);
				run.logger.info("Cleaning up done on orphan binary files, {} entries affected.", count);
			}
			catch (DataAccessException e) {
				log.error("Task {}. Database error while purging orphan binary files.", task.id(), e);
				run.logger.error("Database error during the orphan binary file purge.");
				run.setStatus(Status.FAILURE);
				return;
			}
			catch (Exception e) {
				log.error("Task {}. Error while purging orphan binary files.", task.id(), e);
				run.logger.error("Error during the orphan binary file purge.");
				run.setStatus(Status.FAILURE);
				return;
			}
		}

		run.setStatus(Status.SUCCESS);
		log.trace("Task {}. Cleaning up process finished.", task.id());
	}

	/**
	 * Removes the old configurations: those taken before the given date (and, if a size is given,
	 * with a long text or a binary file bigger than it), but the last configuration of each device
	 * and, if a number of days to keep is given, those taken during the given days before the
	 * last one which is kept.
	 *
	 * @param dsl the jOOQ context
	 * @param when the date before which the configurations are removed
	 * @param configSize the minimum size (in kB) of a long text or binary file for a configuration to be removed, 0 for any
	 * @param configKeepDays the number of days of configurations to keep before the last kept one, 0 for none
	 * @param groupId the group of the target devices, or null
	 * @param deviceIds the target devices, or null
	 * @param toDeletePathes receives the binary files to delete once the transaction is committed
	 * @return the number of removed configurations
	 */
	private int purgeConfigs(DSLContext dsl, Instant when, int configSize, int configKeepDays, Long groupId, List<Long> deviceIds,
			List<Path> toDeletePathes) {
		Condition condition = CONFIG.CHANGE_DATE.lt(when).and(targetDevices(CONFIG.DEVICE, groupId, deviceIds));
		if (configSize > 0) {
			condition = condition.and(CONFIG.ID.in(DSL.select(CONFIG_ATTRIBUTE.CONFIG).from(CONFIG_ATTRIBUTE)
				.leftJoin(LONG_TEXT_CONFIGURATION).on(LONG_TEXT_CONFIGURATION.ID.eq(CONFIG_ATTRIBUTE.LONG_TEXT))
				.where(CONFIG_ATTRIBUTE.TYPE.in("T", "F"))
				.groupBy(CONFIG_ATTRIBUTE.CONFIG)
				.having(DSL.max(DSL.length(LONG_TEXT_CONFIGURATION.TEXT)).gt(configSize * 1024)
					.or(DSL.max(CONFIG_ATTRIBUTE.FILE_SIZE).gt((long) configSize * 1024)))));
		}
		List<Record4<Long, Long, Instant, Long>> configs = dsl.select(CONFIG.ID, CONFIG.DEVICE, CONFIG.CHANGE_DATE, DEVICE.LAST_CONFIG)
			.from(CONFIG)
			.join(DEVICE).on(DEVICE.ID.eq(CONFIG.DEVICE))
			.where(condition)
			.orderBy(CONFIG.DEVICE.asc(), CONFIG.CHANGE_DATE.desc())
			.fetch();
		long dontDeleteDevice = -1;
		Instant dontDeleteBefore = null;
		int count = 0;
		for (Record4<Long, Long, Instant, Long> config : configs) {
			long configId = config.value1();
			long deviceId = config.value2();
			Instant changeDate = config.value3();
			if (Objects.equals(config.value4(), configId)
				|| (dontDeleteBefore != null && changeDate.isBefore(dontDeleteBefore))
				|| (configKeepDays > 0 && dontDeleteDevice != deviceId)) {
				if (configKeepDays > 0) {
					dontDeleteBefore = changeDate.minus(configKeepDays, ChronoUnit.DAYS);
				}
			}
			else {
				dsl.select(CONFIG_ATTRIBUTE.NAME, CONFIG_ATTRIBUTE.UID, CONFIG_ATTRIBUTE.ORIGINAL_NAME)
					.from(CONFIG_ATTRIBUTE)
					.where(CONFIG_ATTRIBUTE.CONFIG.eq(configId), CONFIG_ATTRIBUTE.TYPE.eq("F"))
					.forEach(a -> toDeletePathes.add(new ConfigBinaryFileAttribute(a.value1(), a.value2(), a.value3(), 0, null)
						.getFilePath(deviceId, configId)));
				List<Long> longTextIds = dsl.select(CONFIG_ATTRIBUTE.LONG_TEXT).from(CONFIG_ATTRIBUTE)
					.where(CONFIG_ATTRIBUTE.CONFIG.eq(configId), CONFIG_ATTRIBUTE.LONG_TEXT.isNotNull())
					.fetch(CONFIG_ATTRIBUTE.LONG_TEXT);
				DeviceRepository.deleteLongTexts(dsl, longTextIds);
				dsl.deleteFrom(CONFIG).where(CONFIG.ID.eq(configId)).execute();
				count++;
			}
			dontDeleteDevice = deviceId;
		}
		return count;
	}
}
