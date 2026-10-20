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
package net.netshot.netshot.task.engine;

import net.netshot.netshot.TestConfig;
import net.netshot.netshot.task.Task.SequentialScheduling.NextAction;
import net.netshot.netshot.task.Task.SequentialScheduling;
import net.netshot.netshot.task.Task.Status;
import net.netshot.netshot.testing.UnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Properties;

@UnitTest
@DisplayName("Sequential group-task scheduling decisions")
public class SequentialDecisionTest {

	@BeforeAll
	static void initNetshot() {
		TestConfig.init(new Properties());
	}

	@Test
	@DisplayName("Continues while nothing failed and no cancel was requested")
	void continuesByDefault() {
		Assertions.assertEquals(NextAction.CONTINUE,
			SequentialScheduling.decideNextAction(false, false, false));
		Assertions.assertEquals(NextAction.CONTINUE,
			SequentialScheduling.decideNextAction(false, true, false));
	}

	@Test
	@DisplayName("Continues after a failure when stopOnFailure is false")
	void continuesOnFailureWhenNotStopping() {
		Assertions.assertEquals(NextAction.CONTINUE,
			SequentialScheduling.decideNextAction(true, false, false));
	}

	@Test
	@DisplayName("Stops after a failure when stopOnFailure is true")
	void stopsOnFailureWhenStopping() {
		Assertions.assertEquals(NextAction.STOP,
			SequentialScheduling.decideNextAction(true, true, false));
	}

	@Test
	@DisplayName("Stops when cancellation was requested, regardless of stopOnFailure")
	void stopsOnCancelRequested() {
		Assertions.assertEquals(NextAction.STOP,
			SequentialScheduling.decideNextAction(false, false, true));
		Assertions.assertEquals(NextAction.STOP,
			SequentialScheduling.decideNextAction(false, true, true));
	}

	@Test
	@DisplayName("Final status is SUCCESS when nothing failed and no cancel was honored")
	void finalStatusSuccess() {
		Assertions.assertEquals(Status.SUCCESS,
			SequentialScheduling.decideFinalStatus(false, false, false, false));
		Assertions.assertEquals(Status.SUCCESS,
			SequentialScheduling.decideFinalStatus(false, true, false, false));
	}

	@Test
	@DisplayName("Final status is CANCELLED when a cancel was honored and nothing failed")
	void finalStatusCancelled() {
		Assertions.assertEquals(Status.CANCELLED,
			SequentialScheduling.decideFinalStatus(false, false, true, false));
	}

	@Test
	@DisplayName("Final status is FAILURE if a child failed and stop-on-failure is enabled, even if a cancel was also honored")
	void finalStatusFailureWinsOverCancel() {
		Assertions.assertEquals(Status.FAILURE,
			SequentialScheduling.decideFinalStatus(true, true, true, false));
		Assertions.assertEquals(Status.FAILURE,
			SequentialScheduling.decideFinalStatus(true, true, false, false));
	}

	@Test
	@DisplayName("Final status is SUCCESS if a child failed but stop-on-failure is disabled and nothing else went wrong")
	void finalStatusSuccessDespiteFailureWhenNotStopping() {
		Assertions.assertEquals(Status.SUCCESS,
			SequentialScheduling.decideFinalStatus(true, false, false, false));
	}

	@Test
	@DisplayName("Final status is CANCELLED if a child failed but stop-on-failure is disabled and a cancel was honored")
	void finalStatusCancelledDespiteFailureWhenNotStopping() {
		Assertions.assertEquals(Status.CANCELLED,
			SequentialScheduling.decideFinalStatus(true, false, true, false));
	}

	@Test
	@DisplayName("Final status is FAILURE whenever a scheduling error occurred, regardless of other flags")
	void finalStatusFailureOnSchedulingError() {
		Assertions.assertEquals(Status.FAILURE,
			SequentialScheduling.decideFinalStatus(false, false, false, true));
		Assertions.assertEquals(Status.FAILURE,
			SequentialScheduling.decideFinalStatus(false, false, true, true));
	}
}
