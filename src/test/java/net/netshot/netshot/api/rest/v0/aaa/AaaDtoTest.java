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
package net.netshot.netshot.api.rest.v0.aaa;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import net.netshot.netshot.aaa.user.ApiToken;
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.UserCreation;
import net.netshot.netshot.aaa.user.UserUpdate;
import net.netshot.netshot.testing.UnitTest;
import net.netshot.netshot.testing.contract.DtoContract;

/**
 * Checks that the user and API token DTOs are a faithful replacement of the entities the REST API
 * used to serialize (same JSON and XML), that no secret (password hash, previous hashes, token
 * value or hash) is part of any output, and that the requests are read like the former request
 * classes were.
 */
@UnitTest
class AaaDtoTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final XmlMapper XML = XmlMapper.builder().build();

	private static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz012345";

	@RegisterExtension
	private final DtoContract contract = new DtoContract(AaaDtoTest.class);

	private static byte[] json(Object value) throws Exception {
		return JSON.writer().writeValueAsBytes(value);
	}

	private static byte[] xml(Object value) throws Exception {
		return XML.writer().writeValueAsBytes(value);
	}

	private void assertRepresentation(Object dto) throws Exception {
		this.contract.checkBoth(json(dto), xml(dto));
	}

	private static UiUser localUser() {
		UiUser user = UiUser.withPassword("alice", true, 100, "Sup3r-s3cret").withId(7);
		return new UiUser(user.id(), user.username(), user.local(), user.level(), user.hashedPassword(),
			List.of("$argon2id$old-hash-1"), user.lastPasswordChangeDate());
	}

	private static ApiToken token() {
		return ApiToken.of("Monitoring", TOKEN, 10).withId(3);
	}

	@Test
	@DisplayName("A local user has the same representation as the entity")
	void localUserRepresentation() throws Exception {
		UiUser user = localUser();
		this.assertRepresentation(UserMapper.toDto(user));
		Assertions.assertTrue(new String(xml(UserMapper.toDto(user))).startsWith("<UiUser>"));
	}

	@Test
	@DisplayName("A remote user has the same representation as the entity")
	void remoteUserRepresentation() throws Exception {
		UiUser user = UiUser.remote("radius-user", 50).withId(8);
		this.assertRepresentation(UserMapper.toDto(user));
	}

	@Test
	@DisplayName("A list of users has the same representation as a list of entities")
	void userListRepresentation() throws Exception {
		List<UiUser> users = new ArrayList<>(List.of(localUser(), UiUser.remote("radius-user", 50)));
		this.assertRepresentation(UserMapper.toUserDtos(users));
	}

	@Test
	@DisplayName("An API token has the same representation as the entity")
	void apiTokenRepresentation() throws Exception {
		ApiToken apiToken = token();
		this.assertRepresentation(UserMapper.toDto(apiToken));
		Assertions.assertTrue(new String(xml(UserMapper.toDto(apiToken))).startsWith("<ApiToken>"));
		List<ApiToken> tokens = new ArrayList<>(List.of(apiToken, ApiToken.of("", "ZyXwVuTsRqPoNmLkJiHgFeDcBa987654", 1000)));
		this.assertRepresentation(UserMapper.toApiTokenDtos(tokens));
	}

	@Test
	@DisplayName("The current user is published as a user or as an API token, depending on the principal")
	void currentUserRepresentation() throws Exception {
		UiUser user = localUser();
		CurrentUserDto userDto = UserMapper.toCurrentUserDto(user);
		Assertions.assertInstanceOf(UserDto.class, userDto);
		this.assertRepresentation(userDto);
		ApiToken apiToken = token();
		CurrentUserDto tokenDto = UserMapper.toCurrentUserDto(apiToken);
		Assertions.assertInstanceOf(ApiTokenDto.class, tokenDto);
		this.assertRepresentation(tokenDto);
	}

	@Test
	@DisplayName("No password hash, previous hash, token value or token hash is ever written")
	void noSecretInOutput() throws Exception {
		UiUser user = localUser();
		ApiToken apiToken = token();
		String[] outputs = {
			new String(json(UserMapper.toDto(user))), new String(xml(UserMapper.toDto(user))),
			new String(json(UserMapper.toUserDtos(List.of(user)))), new String(xml(UserMapper.toUserDtos(List.of(user)))),
			new String(json(UserMapper.toCurrentUserDto(user))), new String(xml(UserMapper.toCurrentUserDto(user))),
			new String(json(UserMapper.toDto(apiToken))), new String(xml(UserMapper.toDto(apiToken))),
			new String(json(UserMapper.toApiTokenDtos(List.of(apiToken)))), new String(xml(UserMapper.toApiTokenDtos(List.of(apiToken)))),
			new String(json(UserMapper.toCurrentUserDto(apiToken))), new String(xml(UserMapper.toCurrentUserDto(apiToken))),
		};
		String[] secrets = {
			user.hashedPassword(), "$argon2id", "old-hash", "Sup3r-s3cret", TOKEN, apiToken.hashedToken(),
			"hashedPassword", "oldHashedPasswords", "lastPasswordChangeDate", "hashedToken", "password", "token",
		};
		Assertions.assertNotNull(user.hashedPassword());
		Assertions.assertNotNull(apiToken.hashedToken());
		for (String output : outputs) {
			for (String secret : secrets) {
				// "description" and "token" would only match as a property name: the check is on whole words
				if ("token".equals(secret)) {
					Assertions.assertFalse(output.toLowerCase().contains("\"token\"") || output.contains("<token>"), output);
				}
				else {
					Assertions.assertFalse(output.contains(secret), "Leaks '" + secret + "': " + output);
				}
			}
		}
	}

	@Test
	@DisplayName("The DTOs carry no secret component")
	void noSecretComponent() {
		for (Class<?> dto : List.of(UserDto.class, ApiTokenDto.class)) {
			for (var component : dto.getRecordComponents()) {
				String name = component.getName().toLowerCase();
				Assertions.assertFalse(name.contains("password") || name.contains("hash") || name.contains("token"),
					dto.getSimpleName() + "." + component.getName());
			}
		}
	}

	// ------------------------------------------------------------------
	// Requests
	// ------------------------------------------------------------------

	@Test
	@DisplayName("A user request is read like RsUser: absent properties are zero, false or null")
	void userRequest() throws Exception {
		UserRequest empty = JSON.readerFor(UserRequest.class).readValue("{}");
		Assertions.assertEquals(new UserRequest(0, null, null, 0, false), empty);
		UserRequest nulls = JSON.readerFor(UserRequest.class)
			.readValue("{\"id\": null, \"level\": null, \"local\": null, \"username\": null, \"password\": null}");
		Assertions.assertEquals(new UserRequest(0, null, null, 0, false), nulls);
		UserRequest full = JSON.readerFor(UserRequest.class)
			.readValue("{\"id\": 4, \"username\": \" bob \", \"password\": \"pw\", \"level\": 100, \"local\": true}");
		Assertions.assertEquals(new UserRequest(4, " bob ", "pw", 100, true), full);
		// The mask of the secrets is not applied to the password of a user (a scalar has no content)
		UserRequest masked = JSON.readerFor(UserRequest.class).readValue("{\"password\": \"=\"}");
		Assertions.assertEquals("=", masked.password());
		Assertions.assertThrows(UnrecognizedPropertyException.class,
			() -> JSON.readerFor(UserRequest.class).readValue("{\"admin\": true}"));
	}

	@Test
	@DisplayName("The user request is mapped to the commands without trimming nor defaulting")
	void userCommands() {
		UserRequest request = new UserRequest(4, " bob ", "pw", 100, true);
		Assertions.assertEquals(new UserCreation(" bob ", true, "pw", 100), UserMapper.toCreation(request));
		Assertions.assertEquals(new UserUpdate(" bob ", true, "pw", 100), UserMapper.toUpdate(request));
	}

	@Test
	@DisplayName("An API token request is read like RsApiToken")
	void apiTokenRequest() throws Exception {
		ApiTokenRequest empty = JSON.readerFor(ApiTokenRequest.class).readValue("{}");
		Assertions.assertEquals(new ApiTokenRequest(0, null, null, 0), empty);
		ApiTokenRequest full = JSON.readerFor(ApiTokenRequest.class)
			.readValue("{\"id\": 9, \"description\": \"d\", \"token\": \"" + TOKEN + "\", \"level\": 50}");
		Assertions.assertEquals(new ApiTokenRequest(9, "d", TOKEN, 50), full);
		Assertions.assertThrows(UnrecognizedPropertyException.class,
			() -> JSON.readerFor(ApiTokenRequest.class).readValue("{\"hashedToken\": \"x\"}"));
	}

	@Test
	@DisplayName("A login request is read like RsLogin")
	void loginRequest() throws Exception {
		LoginRequest empty = JSON.readerFor(LoginRequest.class).readValue("{}");
		Assertions.assertEquals(new LoginRequest(null, null, null, null, null), empty);
		LoginRequest full = JSON.readerFor(LoginRequest.class).readValue("""
			{"username": "u", "password": "p", "newPassword": "n", "authorizationCode": "c", "redirectUri": "https://x/"}""");
		Assertions.assertEquals(new LoginRequest("u", "p", "n", "c", "https://x/"), full);
		Assertions.assertThrows(UnrecognizedPropertyException.class,
			() -> JSON.readerFor(LoginRequest.class).readValue("{\"remember\": true}"));
	}
}
