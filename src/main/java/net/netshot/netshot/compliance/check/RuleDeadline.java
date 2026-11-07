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
package net.netshot.netshot.compliance.check;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Helper of the script-based rule evaluators, bounding the steps of a script by a deadline.
 */
final class RuleDeadline {

	private RuleDeadline() {
	}

	/**
	 * Runs a step of a script-based rule on the given executor, bounded by the given deadline.
	 *
	 * @param <T> the result type
	 * @param executor the executor to run the step on
	 * @param step the step to run
	 * @param deadline the deadline (epoch, in ms)
	 * @return the result of the step
	 * @throws java.util.concurrent.TimeoutException if the deadline is reached before the step completes
	 * @throws Exception the exception thrown by the step itself
	 */
	static <T> T runBeforeDeadline(ExecutorService executor, Callable<T> step, long deadline)
		throws Exception {
		long remaining = Math.max(1, deadline - System.currentTimeMillis());
		try {
			return executor.submit(step).get(remaining, TimeUnit.MILLISECONDS);
		}
		catch (ExecutionException e) {
			if (e.getCause() instanceof Exception cause) {
				throw cause;
			}
			throw e;
		}
	}

}
