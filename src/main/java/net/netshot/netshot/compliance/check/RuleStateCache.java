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
package net.netshot.netshot.compliance.check;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A thread-safe cache of the state prepared from a rule (compiled patterns, validity of the script...).
 * The entries are keyed by the identity of the rule instance and weakly held, so that the state lasts
 * as long as the rule instance, as it did when it was a transient field of the entity. The identity
 * matters: rules are equal when they have the same id, but two instances (two sessions, or the
 * same rule before and after an edit) must not share their prepared state.
 *
 * <p>A value must not reference its rule, or it would never be released.</p>
 *
 * @param <K> the type of rule
 * @param <V> the type of prepared state
 */
final class RuleStateCache<K, V> {

	private static final class Key<K> extends WeakReference<K> {
		private final int hash;

		Key(K referent, ReferenceQueue<K> queue) {
			super(referent, queue);
			this.hash = System.identityHashCode(referent);
		}

		@Override
		public int hashCode() {
			return this.hash;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof Key<?> other)) {
				return false;
			}
			K referent = this.get();
			return referent != null && referent == other.get();
		}
	}

	private final ReferenceQueue<K> queue = new ReferenceQueue<>();
	private final Map<Key<K>, V> entries = new HashMap<>();

	@SuppressWarnings("unchecked")
	private void expunge() {
		Object stale;
		while ((stale = this.queue.poll()) != null) {
			this.entries.remove((Key<K>) stale);
		}
	}

	/**
	 * Gets the prepared state of a rule, computing it if there is none yet.
	 *
	 * @param rule the rule
	 * @param preparer computes the state of a rule
	 * @return the prepared state
	 */
	synchronized V get(K rule, Function<K, V> preparer) {
		this.expunge();
		V value = this.entries.get(new Key<>(rule, null));
		if (value == null) {
			value = preparer.apply(rule);
			this.entries.put(new Key<>(rule, this.queue), value);
		}
		return value;
	}

	/**
	 * Replaces the prepared state of a rule, if it has one.
	 *
	 * @param rule the rule
	 * @param value the new state
	 */
	synchronized void replaceIfPresent(K rule, V value) {
		this.expunge();
		this.entries.replace(new Key<>(rule, null), value);
	}

	/**
	 * Gets the number of entries.
	 *
	 * @return the number of live entries
	 */
	synchronized int size() {
		this.expunge();
		return this.entries.size();
	}

}
