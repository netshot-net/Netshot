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
package net.netshot.netshot.boot;

import net.netshot.netshot.aaa.auth.ConfiguredRemoteAuthenticator;
import net.netshot.netshot.aaa.auth.LoginService;
import net.netshot.netshot.aaa.auth.Oidc;
import net.netshot.netshot.aaa.auth.OidcDiscovery;
import net.netshot.netshot.aaa.auth.Radius;
import net.netshot.netshot.aaa.auth.Tacacs;
import net.netshot.netshot.aaa.user.ApiTokenService;
import net.netshot.netshot.aaa.user.UserService;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.database.Transactions;

/**
 * The authentication and the authorization: the remote authenticators (OIDC, TACACS+, RADIUS), the use cases of the users, the API tokens and the logins, and the discovery of the OIDC provider.
 *
 * @param oidc the OIDC authenticator
 * @param tacacs the TACACS+ authenticator
 * @param userService the user use cases
 * @param apiTokenService the API token use cases
 * @param loginService the login use cases
 * @param oidcDiscovery the discovery of the OIDC provider (a service)
 */
public record AaaModule(
	Oidc oidc,
	Tacacs tacacs,
	UserService userService,
	ApiTokenService apiTokenService,
	LoginService loginService,
	OidcDiscovery oidcDiscovery) {

	/**
	 * Builds the module.
	 *
	 * @param core the core module
	 * @return the module
	 */
	public static AaaModule create(CoreModule core) {
		ConfigStore config = core.config();
		Transactions transactions = core.transactions();
		Repositories r = core.repositories();
		Oidc oidc = new Oidc(config);
		Tacacs tacacs = new Tacacs(config);
		return new AaaModule(oidc, tacacs, new UserService(transactions, r.user(), config), new ApiTokenService(transactions, r.apiToken()),
			new LoginService(transactions, r.user(), new ConfiguredRemoteAuthenticator(oidc, new Radius(config), tacacs), config),
			new OidcDiscovery(oidc, config));
	}
}
