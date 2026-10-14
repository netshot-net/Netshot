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
package net.netshot.netshot.boot;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

import net.netshot.netshot.inventory.device.DeviceService;
import net.netshot.netshot.platform.database.Transactions;
import net.netshot.netshot.task.TaskService;

/**
 * Exposes the objects of the {@link Wiring} to the dependency injection of the REST
 * resources (HK2, the injection container of Jersey).
 *
 * <p>The web application does not know the composition root: {@code boot} hands this
 * binder over to it, and the resources declare what they need with {@code @Inject}.</p>
 */
final class WiringBinder extends AbstractBinder {

	private final Wiring wiring;

	WiringBinder(Wiring wiring) {
		this.wiring = wiring;
	}

	@Override
	protected void configure() {
		this.bind(this.wiring.transactions()).to(Transactions.class);
		this.bind(this.wiring.deviceService()).to(DeviceService.class);
		// Tasks
		this.bind(this.wiring.taskService()).to(TaskService.class);
	}
}
