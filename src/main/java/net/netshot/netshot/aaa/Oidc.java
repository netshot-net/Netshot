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
package net.netshot.netshot.aaa;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.Scope;
import com.nimbusds.oauth2.sdk.TokenErrorResponse;
import com.nimbusds.oauth2.sdk.TokenRequest;
import com.nimbusds.oauth2.sdk.auth.ClientAuthentication;
import com.nimbusds.oauth2.sdk.auth.ClientSecretBasic;
import com.nimbusds.oauth2.sdk.auth.Secret;
import com.nimbusds.oauth2.sdk.http.HTTPRequest;
import com.nimbusds.oauth2.sdk.http.HTTPResponse;
import com.nimbusds.oauth2.sdk.id.ClientID;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.openid.connect.sdk.OIDCScopeValue;
import com.nimbusds.openid.connect.sdk.OIDCTokenResponse;
import com.nimbusds.openid.connect.sdk.claims.IDTokenClaimsSet;
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import com.nimbusds.openid.connect.sdk.rp.OIDCClientInformation;
import com.nimbusds.openid.connect.sdk.rp.OIDCClientMetadata;
import com.nimbusds.openid.connect.sdk.token.OIDCTokens;
import com.nimbusds.openid.connect.sdk.validators.IDTokenValidator;
import lombok.extern.slf4j.Slf4j;
import net.netshot.netshot.Netshot;

/**
 * The Oidc class authenticates the users using JSON Web Token.
 */
@Slf4j
public final class Oidc {

	/** The AAA logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/** IdP metadata (retrieved from issuer endpoint URL). */
	private static OIDCProviderMetadata idpMetadata;

	/** OIDC client info. */
	private static OIDCClientInformation clientInfo;

	/** ID Token validator based on IdP metadata and client info. */
	private static IDTokenValidator idTokenValidator;

	/** Name of the claim that will carry the role. */
	private static String roleClaim;

	/** Default permission level for authenticated user. */
	private static int defaultLevel;

	/** Name of the claim that will carry the username. */
	private static String usernameClaim;

	/** Configured roles. */
	private static Map<String, Integer> roles = new HashMap<>();

	private static IdPDiscoveryDaemon idpDiscoveryThread;

	/**
	 * Thread to refresh the IdP Metadata from endpoint URL in background.
	 */
	private static final class IdPDiscoveryDaemon extends Thread {

		private String endpoint;
		private int refreshInterval;
		private int retryInterval;
		private boolean stopping;

		private IdPDiscoveryDaemon(String endpoint, int retryInterval, int refreshInterval) {
			this.endpoint = endpoint;
			this.refreshInterval = refreshInterval;
			this.retryInterval = retryInterval;
			this.setName("NetshotOidcIdPDiscovery");
			this.setDaemon(true);
		}

		public void setStopping() {
			this.stopping = true;
			synchronized (this) {
				this.notify();
			}
		}

		@Override
		public void run() {
			log.info("Starting OIDC IdP metadata discovery daemon");
			while (!this.stopping) {
				int waitingTime = this.refreshInterval;
				try {
					Oidc.fetchIdpMetadata(this.endpoint);
				}
				catch (Exception e) {
					log.error("Error while fetching/preparing OIDC configuration", e);
					waitingTime = this.retryInterval;
				}
				try {
					log.trace("OIDC IdP discovery - pausing for {}ms", waitingTime);
					synchronized (this) {
						this.wait(waitingTime);
					}
				}
				catch (InterruptedException e) {
					break;
				}
			}
			log.info("End of OIDC IdP metadata discovery daemon");
		}
	}

	private static synchronized void fetchIdpMetadata(String endpoint) throws Exception {
		log.debug("Retrieving OIDC metatada from issuer endpoint {}...", endpoint);
		Oidc.idpMetadata = OIDCProviderMetadata.resolve(new Issuer(endpoint));
		log.trace("OIDC IDP metadata {}", Oidc.idpMetadata);
		Oidc.idTokenValidator = IDTokenValidator.create(Oidc.idpMetadata, Oidc.clientInfo);
	}

	/**
	 * Load the configuration from Netshot config file.
	 */
	public static synchronized void loadConfig() {
		roleClaim = Netshot.getConfig("netshot.aaa.oidc.role.claimname", "role");
		roles.clear();
		for (User.Role role : User.Role.values()) {
			String roleKey = role.getName().replaceAll("-", "");
			String claimValue = Netshot.getConfig("netshot.aaa.oidc.role.%srole".formatted(roleKey), role.getName());
			roles.put(claimValue, role.getLevel());
		}
		defaultLevel = Netshot.getConfig("netshot.aaa.oidc.role.defaultlevel", 0, 0, 9999);
		usernameClaim = Netshot.getConfig("netshot.aaa.oidc.usernameclaimname", "preferred_username");
		String endpoint = Netshot.getConfig("netshot.aaa.oidc.idp.url");
		String clientId = Netshot.getConfig("netshot.aaa.oidc.clientid");
		String clientSecret = Netshot.getSecretConfig("netshot.aaa.oidc.clientsecret");
		int idpRetryInterval = Netshot.getConfig("netshot.aaa.oidc.idp.retryinterval", 30000, 1000, Integer.MAX_VALUE);
		int idpRefreshInterval = Netshot.getConfig("netshot.aaa.oidc.idp.refreshinterval", 43200000, 60000, Integer.MAX_VALUE);

		OIDCClientMetadata clientMetadata = new OIDCClientMetadata();
		clientMetadata.applyDefaults();
		if (endpoint == null) {
			Oidc.idpMetadata = null;
		}
		else if (clientId == null) {
			log.warn("OIDC client id configuration is missing, OIDC won't be enabled");
			Oidc.idpMetadata = null;
		}
		else if (clientSecret == null) {
			log.warn("OIDC client secret configuration is missing, OIDC won't be enabled");
			Oidc.idpMetadata = null;
		}
		else {
			Oidc.clientInfo = new OIDCClientInformation(
				new ClientID(clientId), null, clientMetadata, new Secret(clientSecret));
			if (Oidc.idpDiscoveryThread != null) {
				if (!Oidc.idpDiscoveryThread.endpoint.equals(endpoint)) {
					log.info("OIDC IdP endpoint configuration has changed, stopping the discovery thread");
					Oidc.idpDiscoveryThread.setStopping();
					Oidc.idpDiscoveryThread = null;
					Oidc.idpMetadata = null;
				}
			}
			if (Oidc.idpDiscoveryThread == null) {
				Oidc.idpDiscoveryThread = new IdPDiscoveryDaemon(
					endpoint, idpRetryInterval, idpRefreshInterval);
				Oidc.idpDiscoveryThread.start();
			}
		}
	}

	public static synchronized boolean isAvailable() {
		return Oidc.idpMetadata != null;
	}

	public static synchronized URI getAuthorizationEndpointURI() {
		if (!Oidc.isAvailable()) {
			return null;
		}
		return Oidc.idpMetadata.getAuthorizationEndpointURI();
	}

	public static synchronized String getClientId() {
		if (!Oidc.isAvailable()) {
			return null;
		}
		return Oidc.clientInfo.getID().getValue();
	}

	public static synchronized URI getEndSessionEndpointURI() {
		if (!Oidc.isAvailable()) {
			return null;
		}
		return Oidc.idpMetadata.getEndSessionEndpointURI();
	}

	/**
	 * Exchange authorization code for ID/Access tokens.
	 * @param code = the authorization code
	 * @param redirectUrl = the redirect URL
	 * @return the OIDC tokens
	 * @throws Exception in case of problem
	 */
	private static OIDCTokens codeToTokens(String code, URL redirectUrl) throws Exception {
		// Create token request
		AuthorizationCode authCode = new AuthorizationCode(code);
		AuthorizationGrant codeGrant = new AuthorizationCodeGrant(authCode, redirectUrl.toURI());

		// Client authentication
		ClientAuthentication clientAuth = new ClientSecretBasic(clientInfo.getID(), clientInfo.getSecret());

		// Prepare request
		TokenRequest tokenRequest = new TokenRequest
			.Builder(Oidc.idpMetadata.getTokenEndpointURI(), clientAuth, codeGrant)
			.scope(new Scope(OIDCScopeValue.OPENID))
			.build();
		HTTPRequest request = tokenRequest.toHTTPRequest();
		// Send request
		HTTPResponse response = request.send();
		OIDCTokenResponse tokenResponse = OIDCTokenResponse.parse(response);

		if (!tokenResponse.indicatesSuccess()) {
			TokenErrorResponse errorResponse = tokenResponse.toErrorResponse();
			throw new RuntimeException("Token exchange failed: " + errorResponse.getErrorObject().getDescription());
		}

		// Parse OIDC token response
		AccessTokenResponse oidcTokenResponse = tokenResponse.toSuccessResponse();
		return oidcTokenResponse.getTokens().toOIDCTokens();
	}

	/**
	 * Exchange authorization code for tokens and validate.
	 * @param authorizationCode = the authorization code to exchange for token
	 * @param redirectUrl = the redirect URL
	 * @return the authenticated user or null if authentication failed
	 */
	public static synchronized UiUser authenticateWithCode(String authorizationCode, URL redirectUrl) {
		if (!Oidc.isAvailable()) {
			return null;
		}
		try {
			// Exchange authorization token for tokens
			final OIDCTokens tokens = codeToTokens(authorizationCode, redirectUrl);
			AAA_LOG.debug(
				MarkerFactory.getMarker("AAA"),
				"OIDC authorization code exchanged for OIDC tokens"
			);
			final IDTokenClaimsSet tokenClaims = Oidc.idTokenValidator
				.validate(tokens.getIDToken(), null);
			AAA_LOG.debug(
				MarkerFactory.getMarker("AAA"),
				"ID token successfully validated"
			);
			String username = tokenClaims.getStringClaim(usernameClaim);
			if (username == null) {
				throw new RuntimeException("The ID token is missing the expected claim %s".formatted(usernameClaim));
			}
			Integer level = null;
			final Set<String> passedRoles = new HashSet<>();
			final String roleName = tokenClaims.getStringClaim(roleClaim);
			// Try the claim as simple string
			if (roleName != null) {
				passedRoles.add(roleName);
			}
			// Also try an array of strings
			List<String> allRoleNames = tokenClaims.getStringListClaim(roleClaim);
			if (allRoleNames != null) {
				passedRoles.addAll(allRoleNames);
			}
			// Select the highest role level among passed roles
			for (String passedRole : passedRoles) {
				Integer roleLevel = roles.get(passedRole);
				if (roleLevel != null) {
					if (level == null || roleLevel > level) {
						level = roleLevel;
					}
				}
			}
			if (level == null) {
				level = Oidc.defaultLevel;
			}
			UiUser user = new UiUser(username, level);
			AAA_LOG.info("User {} successfully authenticated via OIDC (level {})", username, level);
			return user;
		}
		catch (Exception e) {
			log.warn("Error while authenticating user based on OIDC authorization code", e);
			AAA_LOG.warn("Error while authenticating user based on OIDC authorization code", e);
			return null;
		}
	}

	private Oidc() {
	}
}
