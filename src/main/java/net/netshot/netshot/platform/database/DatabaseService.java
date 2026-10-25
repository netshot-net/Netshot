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
package net.netshot.netshot.platform.database;

import java.util.Map;

import net.netshot.netshot.platform.lifecycle.Service;

/**
 * The database access as a service: creates the connection pools when started, closes them when stopped.
 * Its statistics are the ones of the pools (connections total, busy, idle, threads waiting for one).
 */
public final class DatabaseService implements Service {

	@Override
	public String name() {
		return "database";
	}

	@Override
	public void start() {
		Database.init();
	}

	@Override
	public void stop() {
		Database.shutdown();
	}

	@Override
	public boolean isHealthy() {
		return Database.isInitialized();
	}

	@Override
	public Map<String, Object> stats() {
		return Database.stats();
	}
}
