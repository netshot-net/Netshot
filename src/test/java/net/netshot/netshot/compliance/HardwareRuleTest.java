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

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.inventory.device.Module;
import net.netshot.netshot.testing.UnitTest;

/**
 * End of sale and end of life dates given to a device by a hardware rule.
 */
@UnitTest
@DisplayName("Hardware support rule")
class HardwareRuleTest {

	private static final Set<Long> GROUPS = Set.of(7L);

	private static final Instant EOS = Instant.parse("2027-01-01T00:00:00Z");

	private static final Instant EOL = Instant.parse("2030-01-01T00:00:00Z");

	private static final Module CHASSIS = Module.builder().slot("chassis").partNumber("CISCO2911/K9").build();

	private static final Module CARD = Module.builder().slot("0/1").partNumber("EHWIC-4ESG").build();

	private static Device device() {
		return Device.create("CiscoIOS12", "10.0.0.1", null, "tester").toBuilder()
			.family("Cisco 2900")
			.modules(List.of(CHASSIS, CARD))
			.build();
	}

	/**
	 * Starts a rule for the part number EHWIC-4ESG, of any device.
	 *
	 * @return the builder of the rule
	 */
	private static HardwareRule.HardwareRuleBuilder rule() {
		return HardwareRule.builder().family("").partNumber("EHWIC-4ESG").endOfSale(EOS).endOfLife(EOL);
	}

	private static Device check(HardwareRule.HardwareRuleBuilder rule) {
		return rule.build().check(device(), GROUPS);
	}

	private static void assertDates(Device device, Instant eos, Module eosModule, Instant eol, Module eolModule) {
		Assertions.assertEquals(eos, device.eosDate(), "End of sale");
		Assertions.assertEquals(eosModule, device.eosModule(), "End of sale module");
		Assertions.assertEquals(eol, device.eolDate(), "End of life");
		Assertions.assertEquals(eolModule, device.eolModule(), "End of life module");
	}

	private static void assertUnchanged(Device device) {
		assertDates(device, null, null, null, null);
	}

	@Test
	@DisplayName("A matching module gives its dates to the device, and is recorded as the module which gives them")
	void matchingModule() {
		assertDates(check(rule()), EOS, CARD, EOL, CARD);
		assertUnchanged(check(rule().partNumber("EHWIC")));
	}

	@Test
	@DisplayName("A rule with one date only sets that date only")
	void oneDate() {
		assertDates(check(rule().endOfLife(null)), EOS, CARD, null, null);
		assertDates(check(rule().endOfSale(null)), null, null, EOL, CARD);
	}

	@Test
	@DisplayName("An empty part number matches all the modules: the first one gives the dates")
	void anyModule() {
		assertDates(check(rule().partNumber("")), EOS, CHASSIS, EOL, CHASSIS);
	}

	@Test
	@DisplayName("Across rules, the earliest dates are kept")
	void earliestDates() {
		Instant earlier = EOS.minusSeconds(86400);
		Device first = check(rule());
		Device second = rule().partNumber("CISCO2911/K9").endOfSale(earlier).endOfLife(EOL.plusSeconds(86400)).build()
			.check(first, GROUPS);
		assertDates(second, earlier, CHASSIS, EOL, CARD);
	}

	@Test
	@DisplayName("The target group, the driver and the family must be the device's ones")
	void groupDriverFamily() {
		assertDates(check(rule().targetGroupId(7L).driver("CiscoIOS12").family("Cisco 2900")), EOS, CARD, EOL, CARD);
		assertUnchanged(check(rule().targetGroupId(9L)));
		assertUnchanged(check(rule().driver("JuniperJunos")));
		assertUnchanged(check(rule().family("Cisco 29")));
		assertDates(check(rule().family("Cisco 29.*").familyRegExp(true)), EOS, CARD, EOL, CARD);
		assertUnchanged(check(rule().family("Cisco 39.*").familyRegExp(true)));
		assertUnchanged(check(rule().family("Cisco (29").familyRegExp(true)));
	}

	@Test
	@DisplayName("The part number can be a regular expression, matching the whole part number")
	void partNumberRegExp() {
		assertDates(check(rule().partNumber("EHWIC-.*").partNumberRegExp(true)), EOS, CARD, EOL, CARD);
		assertUnchanged(check(rule().partNumber("EHWIC").partNumberRegExp(true)));
		assertUnchanged(check(rule().partNumber("EHWIC-[4").partNumberRegExp(true)));
	}
}
