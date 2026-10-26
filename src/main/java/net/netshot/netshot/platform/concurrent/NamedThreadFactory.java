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
package net.netshot.netshot.platform.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates the threads of an executor, with a name (to recognize them in the logs and thread dumps)
 * and a handler logging the exceptions which would otherwise kill them silently.
 *
 * <p>The threads of Netshot are created through this factory (by the executors of the components),
 * not by extending or instantiating {@link Thread} directly.</p>
 */
@Slf4j
public final class NamedThreadFactory implements ThreadFactory {

	/** The name of the threads (with a number if there are several). */
	private final String name;

	/** Whether the threads are daemon threads (which don't prevent the JVM from exiting). */
	private final boolean daemon;

	/** The number of created threads. */
	private final AtomicInteger count = new AtomicInteger();

	/**
	 * Creates a factory of daemon threads.
	 *
	 * @param name the name of the threads, e.g. {@code NetshotSyslogServer}
	 */
	public NamedThreadFactory(String name) {
		this(name, true);
	}

	/**
	 * Creates a factory.
	 *
	 * @param name the name of the threads, e.g. {@code NetshotSyslogServer}
	 * @param daemon whether the threads are daemon threads
	 */
	public NamedThreadFactory(String name, boolean daemon) {
		this.name = name;
		this.daemon = daemon;
	}

	@Override
	public Thread newThread(Runnable runnable) {
		int number = this.count.incrementAndGet();
		Thread thread = new Thread(runnable, number == 1 ? this.name : "%s-%d".formatted(this.name, number));
		thread.setDaemon(this.daemon);
		thread.setUncaughtExceptionHandler((failed, error) ->
			log.error("Uncaught exception in thread {}", failed.getName(), error));
		return thread;
	}
}
