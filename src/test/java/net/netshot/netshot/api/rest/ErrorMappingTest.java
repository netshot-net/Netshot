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
package net.netshot.netshot.api.rest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;
import net.netshot.netshot.NetshotBadRequestException;
import net.netshot.netshot.platform.error.ErrorCode;
import net.netshot.netshot.platform.error.NetshotException;

public class ErrorMappingTest {

	private final NetshotExceptionMapper mapper = new NetshotExceptionMapper();

	@Test
	@DisplayName("NetshotException is rendered like NetshotBadRequestException, for every error code")
	void sameRenderingForEveryCode() {
		for (ErrorCode code : ErrorCode.values()) {
			Response expected = this.mapper.toResponse(new NetshotBadRequestException("Some message", code));
			Response actual = this.mapper.toResponse(new NetshotException(code, "Some message"));
			Assertions.assertEquals(expected.getStatus(), actual.getStatus(), code.name());
			Assertions.assertEquals(code.getHttpStatus(), actual.getStatus(), code.name());
			RsErrorBean expectedBody = (RsErrorBean) expected.getEntity();
			RsErrorBean actualBody = (RsErrorBean) actual.getEntity();
			Assertions.assertEquals(expectedBody.getErrorMsg(), actualBody.getErrorMsg(), code.name());
			Assertions.assertEquals(expectedBody.getErrorCode(), actualBody.getErrorCode(), code.name());
			Assertions.assertEquals(code.getCode(), actualBody.getErrorCode(), code.name());
		}
	}

	@Test
	@DisplayName("Error codes keep their numeric value and HTTP status")
	void codesAreStable() {
		Assertions.assertEquals(10, ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER.getCode());
		Assertions.assertEquals(400, ErrorCode.NETSHOT_INVALID_REQUEST_PARAMETER.getHttpStatus());
		Assertions.assertEquals(111, ErrorCode.NETSHOT_DUPLICATE_DOMAIN.getCode());
		Assertions.assertEquals(409, ErrorCode.NETSHOT_DUPLICATE_DOMAIN.getHttpStatus());
		Assertions.assertEquals(114, ErrorCode.NETSHOT_DOMAIN_NOT_FOUND.getCode());
		Assertions.assertEquals(404, ErrorCode.NETSHOT_DOMAIN_NOT_FOUND.getHttpStatus());
		Assertions.assertEquals(113, ErrorCode.NETSHOT_DOMAIN_IN_USE.getCode());
		Assertions.assertEquals(412, ErrorCode.NETSHOT_DOMAIN_IN_USE.getHttpStatus());
		Assertions.assertEquals(20, ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR.getCode());
		Assertions.assertEquals(500, ErrorCode.NETSHOT_DATABASE_ACCESS_ERROR.getHttpStatus());
	}
}
