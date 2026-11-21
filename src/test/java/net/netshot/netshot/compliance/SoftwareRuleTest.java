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
package net.netshot.netshot.compliance;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.inventory.compliance.ConformanceLevel;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.testing.UnitTest;

/**
 * Matching of a device by a software rule.
 */
@UnitTest
@DisplayName("Software compliance rule")
class SoftwareRuleTest {

	private static final Set<Long> GROUPS = Set.of(7L, 8L);

	/**
	 * Builds a Cisco IOS router of family "Cisco 2900", running 15.4(3)M2, with two modules.
	 *
	 * @return the device
	 */
	private static Device device() {
		return Device.create("CiscoIOS12", "10.0.0.1", null, "tester").toBuilder()
			.family("Cisco 2900")
			.softwareVersion("15.4(3)M2")
			.modules(List.of(
				Module.builder().slot("chassis").partNumber("CISCO2911/K9").build(),
				Module.builder().slot("0/1").partNumber("EHWIC-4ESG").build()))
			.build();
	}

	/**
	 * Starts a rule giving GOLD, matching any device.
	 *
	 * @return the builder of the rule
	 */
	private static SoftwareRule.SoftwareRuleBuilder rule() {
		return SoftwareRule.builder().family("").softwareVersion("").partNumber("").level(ConformanceLevel.GOLD);
	}

	private static ConformanceLevel check(SoftwareRule.SoftwareRuleBuilder rule) {
		return rule.build().check(device(), GROUPS);
	}

	@Test
	@DisplayName("A rule without criteria gives its level to any device")
	void noCriteria() {
		Assertions.assertEquals(ConformanceLevel.GOLD, check(rule()));
		Assertions.assertEquals(ConformanceLevel.GOLD, check(rule().partNumber(null)));
	}

	@Test
	@DisplayName("The target group and the driver must be the device's ones")
	void groupAndDriver() {
		Assertions.assertEquals(ConformanceLevel.GOLD, check(rule().targetGroupId(8L)));
		Assertions.assertEquals(ConformanceLevel.UNKNOWN, check(rule().targetGroupId(9L)));
		Assertions.assertEquals(ConformanceLevel.GOLD, check(rule().driver("CiscoIOS12")));
		Assertions.assertEquals(ConformanceLevel.UNKNOWN, check(rule().driver("JuniperJunos")));
	}

	@Test
	@DisplayName("The family is matched exactly, or as a whole by a regular expression")
	void family() {
		Assertions.assertEquals(ConformanceLevel.GOLD, check(rule().family("Cisco 2900")));
		Assertions.assertEquals(ConformanceLevel.UNKNOWN, check(rule().family("Cisco 29")));
		Assertions.assertEquals(ConformanceLevel.UNKNOWN, check(rule().family("cisco 2900")));
		Assertions.assertEquals(ConformanceLevel.GOLD, check(rule().family("Cisco 29.*").familyRegExp(true)));
		Assertions.assertEquals(ConformanceLevel.UNKNOWN, check(rule().family("Cisco 29").familyRegExp(true)));
		Assertions.assertEquals(ConformanceLevel.UNKNOWN, check(rule().family("Cisco (29").familyRegExp(true)),
			"An invalid regular expression matches nothing");
	}

	@Test
	@DisplayName("The software version is matched exactly, or as a whole by a regular expression")
	void softwareVersion() {
		Assertions.assertEquals(ConformanceLevel.GOLD, check(rule().softwareVersion("15.4(3)M2")));
		Assertions.assertEquals(ConformanceLevel.UNKNOWN, check(rule().softwareVersion("15.4")));
		Assertions.assertEquals(ConformanceLevel.GOLD, check(rule().softwareVersion("15\\.4\\(.*").softwareVersionRegExp(true)));
		Assertions.assertEquals(ConformanceLevel.UNKNOWN, check(rule().softwareVersion("15\\.5.*").softwareVersionRegExp(true)));
		Assertions.assertEquals(ConformanceLevel.UNKNOWN, check(rule().softwareVersion("15.4(3").softwareVersionRegExp(true)),
			"An invalid regular expression matches nothing");
	}

	@Test
	@DisplayName("The part number must be the one of any module, exactly or as a whole by a regular expression")
	void partNumber() {
		Assertions.assertEquals(ConformanceLevel.GOLD, check(rule().partNumber("EHWIC-4ESG")));
		Assertions.assertEquals(ConformanceLevel.UNKNOWN, check(rule().partNumber("EHWIC")));
		Assertions.assertEquals(ConformanceLevel.GOLD, check(rule().partNumber("CISCO29[0-9]+/K9").partNumberRegExp(true)));
		Assertions.assertEquals(ConformanceLevel.UNKNOWN, check(rule().partNumber("ISR.*").partNumberRegExp(true)));
		Assertions.assertEquals(ConformanceLevel.UNKNOWN, check(rule().partNumber("CISCO[29").partNumberRegExp(true)),
			"An invalid regular expression matches nothing");
		Assertions.assertEquals(ConformanceLevel.UNKNOWN, rule().partNumber("EHWIC-4ESG").build()
			.check(device().toBuilder().modules(List.of()).build(), GROUPS), "No module");
	}

	@Test
	@DisplayName("All the criteria must match")
	void allCriteria() {
		SoftwareRule.SoftwareRuleBuilder all = rule().targetGroupId(7L).driver("CiscoIOS12")
			.family("Cisco 2900").softwareVersion("15\\.4.*").softwareVersionRegExp(true).partNumber("EHWIC-4ESG")
			.level(ConformanceLevel.BRONZE);
		Assertions.assertEquals(ConformanceLevel.BRONZE, check(all));
		Assertions.assertEquals(ConformanceLevel.UNKNOWN, check(all.softwareVersion("15\\.5.*")));
	}
}
