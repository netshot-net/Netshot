/**
 * Copyright 2013-present Netshot
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

import java.util.Optional;

import org.glassfish.hk2.utilities.binding.AbstractBinder;

import net.netshot.netshot.api.hook.HookDispatcher;
import net.netshot.netshot.api.hook.WebHookSender;
import net.netshot.netshot.api.rest.RestServer;
import net.netshot.netshot.hook.HookService;

/**
 * The API channels: the hooks (their use cases, and the dispatcher of the events to the webhooks) and the REST server (absent when disabled).
 *
 * @param hookService the hook use cases
 * @param restServer the REST server (a service), absent when disabled
 */
public record ApiModule(
	HookService hookService,
	Optional<RestServer> restServer) {

	/**
	 * Builds the module.
	 *
	 * @param core the core module
	 * @param tasks the task module (the tasks the hooks describe)
	 * @param binder the dependency injection binder exposing the use cases to the REST resources
	 * @return the module
	 */
	public static ApiModule create(CoreModule core, TaskModule tasks, AbstractBinder binder) {
		HookService hookService = new HookService(core.transactions(), core.repositories().hook());
		new HookDispatcher(hookService, tasks.taskService(), new WebHookSender(), core.driverRegistry()).register(core.domainEvents());
		Optional<RestServer> restServer = core.config().current().get(RestServer.ENABLED)
			? Optional.of(new RestServer(binder, core.config()))
			: Optional.empty();
		return new ApiModule(hookService, restServer);
	}
}
