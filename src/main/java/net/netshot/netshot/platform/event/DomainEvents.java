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
package net.netshot.netshot.platform.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * A synchronous, in-process publish/subscribe of domain events.
 *
 * <p>{@link #publish(Object)} calls the listeners of the event type one after the other, in the
 * order they subscribed, in the thread of the caller, and returns when all of them are done. An
 * exception thrown by a listener is logged and does not prevent the other listeners from being
 * called, nor does it reach the publisher.</p>
 */
@Slf4j
public class DomainEvents {

	private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

	/**
	 * Subscribes a listener to the events of a type.
	 *
	 * @param <E> the type of event
	 * @param type the type of event (the listener is also called for the subtypes)
	 * @param listener the listener
	 */
	public <E> void subscribe(Class<E> type, Consumer<? super E> listener) {
		this.listeners.computeIfAbsent(type, _ -> new CopyOnWriteArrayList<>())
			.add(event -> listener.accept(type.cast(event)));
	}

	/**
	 * Publishes an event: calls the listeners of its type, in the current thread.
	 *
	 * @param event the event
	 */
	public void publish(Object event) {
		for (Map.Entry<Class<?>, List<Consumer<Object>>> entry : this.listeners.entrySet()) {
			if (!entry.getKey().isInstance(event)) {
				continue;
			}
			for (Consumer<Object> listener : entry.getValue()) {
				try {
					listener.accept(event);
				}
				catch (Exception e) {
					log.error("A listener of the event {} failed.", event, e);
				}
			}
		}
	}
}
