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
package net.netshot.netshot.platform.query;

import java.net.InetAddress;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;
import net.netshot.netshot.testing.UnitTest;

/**
 * Reading of the filters and sort keys of the list queries.
 */
@UnitTest
@DisplayName("List query schema")
public class QuerySchemaTest {

	private static final QuerySchema SCHEMA;

	static {
		Map<String, FieldSpec> fields = new LinkedHashMap<>();
		fields.put("id", new FieldSpec("id", FieldType.INTEGER, FieldType.INTEGER.lookups(), List.of(), true, false));
		fields.put("name", new FieldSpec("name", FieldType.STRING, FieldType.STRING.lookups(), List.of(), true, true));
		fields.put("status", new FieldSpec("status", FieldType.ENUM, FieldType.ENUM.lookups(), List.of("ON", "OFF"), false, false));
		fields.put("date", new FieldSpec("date", FieldType.DATE, FieldType.DATE.lookups(), List.of(), true, false));
		fields.put("ip", new FieldSpec("ip", FieldType.INET, FieldType.INET.lookups(), List.of(), false, false));
		fields.put("flag", new FieldSpec("flag", FieldType.BOOLEAN, FieldType.BOOLEAN.lookups(), List.of(), false, false));
		fields.put("group", new FieldSpec("group", FieldType.INTEGER, EnumSet.of(Lookup.EXACT), List.of(), false, false));
		SCHEMA = new QuerySchema(fields, List.of(SortOrder.asc("name")));
	}

	private static void assertInvalid(String parameter, String... values) {
		NetshotException e = Assertions.assertThrows(NetshotException.class, () -> SCHEMA.criterion(parameter, List.of(values)));
		Assertions.assertEquals(ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER, e.getErrorCode());
		Assertions.assertEquals(parameter, e.getTarget());
	}

	@Test
	@DisplayName("Field and lookup are read from the parameter, values by type")
	void criteria() throws Exception {
		Assertions.assertEquals(new Criterion("name", Lookup.EXACT, List.of("a", "b")), SCHEMA.criterion("name", List.of("a", "b")));
		Assertions.assertEquals(new Criterion("name", Lookup.NIC, List.of("x")), SCHEMA.criterion("name__nic", List.of("x")));
		Assertions.assertEquals(new Criterion("id", Lookup.GTE, List.of(5L)), SCHEMA.criterion("id__gte", List.of(" 5")));
		Assertions.assertEquals(new Criterion("status", Lookup.EXACT, List.of("ON")), SCHEMA.criterion("status", List.of("on")));
		Assertions.assertEquals(new Criterion("date", Lookup.LT, List.of(Instant.parse("2026-10-30T00:00:00Z"))),
			SCHEMA.criterion("date__lt", List.of("2026-10-30")));
		Assertions.assertEquals(new Criterion("date", Lookup.LT, List.of(Instant.parse("2026-10-30T08:00:00Z"))),
			SCHEMA.criterion("date__lt", List.of("2026-10-30T10:00:00+02:00")));
		Assertions.assertEquals(new Criterion("ip", Lookup.EXACT, List.of(InetAddress.getByName("10.0.0.1"))),
			SCHEMA.criterion("ip", List.of("10.0.0.1")));
		Assertions.assertEquals(new Criterion("ip", Lookup.NET, List.of("10.0.0.0/8")), SCHEMA.criterion("ip__net", List.of("10.0.0.0/8")));
		Assertions.assertEquals(new Criterion("ip", Lookup.NET, List.of("2001:db8:0:0:0:0:0:0/32")),
			SCHEMA.criterion("ip__net", List.of("2001:db8::/32")));
		Assertions.assertEquals(new Criterion("name", Lookup.EMPTY, List.of(true)), SCHEMA.criterion("name__empty", List.of("TRUE")));
		Assertions.assertEquals(new Criterion("flag", Lookup.EXACT, List.of(false)), SCHEMA.criterion("flag", List.of("false")));
	}

	@Test
	@DisplayName("Invalid filters are refused, naming the parameter")
	void invalidCriteria() {
		assertInvalid("color", "red");
		assertInvalid("name__foo", "x");
		assertInvalid("id__ic", "1");
		assertInvalid("id", "one");
		assertInvalid("status", "MAYBE");
		assertInvalid("date", "2026-10-30T10:00:00");
		assertInvalid("date", "yesterday");
		assertInvalid("ip", "router.example.com");
		assertInvalid("ip__net", "10.0.0.0/33");
		assertInvalid("ip__net", "10.0.0.0");
		assertInvalid("flag", "yes");
		assertInvalid("flag__n", "true");
		assertInvalid("group__gt", "1");
		assertInvalid("name__regex", "(");
		assertInvalid("name__empty", "maybe");
	}

	@Test
	@DisplayName("Sort keys")
	void sort() {
		Assertions.assertEquals(List.of(SortOrder.asc("name"), SortOrder.desc("date")), SCHEMA.sort("sort", "name, -date"));
		Assertions.assertThrows(NetshotException.class, () -> SCHEMA.sort("sort", "status"));
		Assertions.assertThrows(NetshotException.class, () -> SCHEMA.sort("sort", "nothing"));
		Assertions.assertThrows(NetshotException.class, () -> SCHEMA.sort("sort", "group"));
	}
}
