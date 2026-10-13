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
package net.netshot.netshot.platform.database;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.type.BasicTypeRegistry;
import org.hibernate.type.StandardBasicTypes;

public class CustomPostgreSQLDialect extends PostgreSQLDialect {

	@Override
	public void initializeFunctionRegistry(FunctionContributions functionContributions) {
		super.initializeFunctionRegistry(functionContributions);

		BasicTypeRegistry basicTypeRegistry = functionContributions.getTypeConfiguration().getBasicTypeRegistry();

		functionContributions.getFunctionRegistry().registerPattern(
			"regexp_like",
			"?1 ~ ?2",
			basicTypeRegistry.resolve(StandardBasicTypes.BOOLEAN));

		functionContributions.getFunctionRegistry().registerPattern(
			"net_contains",
			"?1 <<= ?2::inet",
			basicTypeRegistry.resolve(StandardBasicTypes.BOOLEAN));

		// Whether the subnet defined by a stored (address, prefix length) pair
		// contains the given target address; unlike net_contains, the subnet's
		// prefix length is not embedded in the stored "inet" value itself (it
		// is a separate sibling column), so the CIDR has to be rebuilt in SQL.
		functionContributions.getFunctionRegistry().registerPattern(
			"net_in_subnet",
			"?1 <<= (host(?2) || '/' || ?3)::inet",
			basicTypeRegistry.resolve(StandardBasicTypes.BOOLEAN));
	}
}
