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
package net.netshot.netshot;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.platform.event.DomainEvents;

/**
 * Tests of the domain events publisher.
 */
class DomainEventsTest {

	private record First(String name) {
	}

	private record Second(String name) {
	}

	@Test
	void listenersAreCalledInOrderForTheirType() {
		DomainEvents events = new DomainEvents();
		List<String> calls = new ArrayList<>();
		events.subscribe(First.class, event -> calls.add("a:" + event.name()));
		events.subscribe(Second.class, event -> calls.add("other:" + event.name()));
		events.subscribe(First.class, event -> calls.add("b:" + event.name()));
		events.publish(new First("x"));
		events.publish(new Second("y"));
		Assertions.assertEquals(List.of("a:x", "b:x", "other:y"), calls);
	}

	@Test
	void aFailingListenerDoesNotStopTheOthers() {
		DomainEvents events = new DomainEvents();
		List<String> calls = new ArrayList<>();
		events.subscribe(First.class, event -> calls.add("before"));
		events.subscribe(First.class, event -> {
			throw new IllegalStateException("Expected failure of a listener");
		});
		events.subscribe(First.class, event -> calls.add("after"));
		Assertions.assertDoesNotThrow(() -> events.publish(new First("x")));
		Assertions.assertEquals(List.of("before", "after"), calls);
	}

	@Test
	void publishingWithoutListenerIsHarmless() {
		Assertions.assertDoesNotThrow(() -> new DomainEvents().publish(new First("x")));
	}

	@Test
	void listenersAreCalledInThePublisherThread() {
		DomainEvents events = new DomainEvents();
		List<Thread> threads = new ArrayList<>();
		events.subscribe(First.class, event -> threads.add(Thread.currentThread()));
		events.publish(new First("x"));
		Assertions.assertEquals(List.of(Thread.currentThread()), threads);
	}
}
