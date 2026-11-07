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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.compliance.TextRule;
import net.netshot.netshot.testing.UnitTest;

@UnitTest
public class RuleStateCacheTest {

	@Test
	@DisplayName("The state is prepared once per rule instance, even for equal rules")
	void identityKeyed() {
		RuleStateCache<TextRule, String> cache = new RuleStateCache<>();
		AtomicInteger prepared = new AtomicInteger();
		TextRule rule1 = TextRule.builder("Rule", 0).build();
		TextRule rule2 = TextRule.builder("Rule", 0).build();
		Assertions.assertEquals(rule1, rule2, "Rules with the same components are equal");

		Assertions.assertEquals("state", cache.get(rule1, _ -> {
			prepared.incrementAndGet();
			return "state";
		}));
		Assertions.assertEquals("state", cache.get(rule1, _ -> {
			prepared.incrementAndGet();
			return "other";
		}));
		Assertions.assertEquals(1, prepared.get());
		Assertions.assertEquals("other", cache.get(rule2, _ -> "other"), "Another instance has its own state");
		Assertions.assertEquals(2, cache.size());
	}

	@Test
	@DisplayName("The state is replaced only if present")
	void replaceIfPresent() {
		RuleStateCache<TextRule, Boolean> cache = new RuleStateCache<>();
		TextRule rule = TextRule.builder("Rule", 0).build();
		cache.replaceIfPresent(rule, false);
		Assertions.assertEquals(0, cache.size());
		Assertions.assertTrue(cache.get(rule, _ -> true));
		cache.replaceIfPresent(rule, false);
		Assertions.assertFalse(cache.get(rule, _ -> true));
	}

	@Test
	@DisplayName("The state is released with the rule instance")
	void weaklyHeld() throws Exception {
		RuleStateCache<TextRule, String> cache = new RuleStateCache<>();
		TextRule rule = TextRule.builder("Rule", 0).build();
		cache.get(rule, _ -> "state");
		Assertions.assertEquals(1, cache.size());
		rule = null;
		for (int i = 0; i < 50 && cache.size() > 0; i++) {
			System.gc();
			Thread.sleep(50);
		}
		Assertions.assertEquals(0, cache.size());
	}

}
