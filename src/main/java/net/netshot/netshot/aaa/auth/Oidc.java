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
package net.netshot.netshot.aaa.auth;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.oauth2.sdk.AccessTokenResponse;
import com.nimbusds.oauth2.sdk.AuthorizationCode;
import com.nimbusds.oauth2.sdk.AuthorizationCodeGrant;
import com.nimbusds.oauth2.sdk.AuthorizationGrant;
import com.nimbusds.oauth2.sdk.GeneralException;
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
import net.netshot.netshot.aaa.user.UiUser;
import net.netshot.netshot.aaa.user.User;
import net.netshot.netshot.platform.config.Config;
import net.netshot.netshot.platform.config.ConfigStore;
import net.netshot.netshot.platform.config.Setting;

/**
 * The Oidc class authenticates the users using JSON Web Token.
 */
@Slf4j
public final class Oidc {

	/** The AAA logger. */
	private static final Logger AAA_LOG = LoggerFactory.getLogger("AAA");

	/** IdP metadata (retrieved from issuer endpoint URL). */
	private OIDCProviderMetadata idpMetadata;

	/** OIDC client info. */
	private OIDCClientInformation clientInfo;

	/** ID Token validator based on IdP metadata and client info. */
	private IDTokenValidator idTokenValidator;

	/**
	 * The settings of the OIDC authentication.
	 *
	 * @param roleClaim the name of the claim carrying the role
	 * @param roles the levels by role name (value of the role claim)
	 * @param defaultLevel the level of an authenticated user without known role
	 * @param usernameClaim the name of the claim carrying the username
	 * @param endpoint the URL of the IdP (issuer), null if OIDC isn't configured
	 * @param clientId the client ID
	 * @param clientSecret the client secret
	 * @param retryInterval the interval (ms) before retrying to fetch the IdP metadata after a failure
	 * @param refreshInterval the interval (ms) between two fetches of the IdP metadata
	 * @param jwtEnabled whether the JWT access tokens issued by the IdP are accepted by the API
	 * @param jwtAudience the audience the JWT access tokens must be issued for (the client ID if null)
	 */
	record Settings(String roleClaim, Map<String, Integer> roles, int defaultLevel, String usernameClaim,
		String endpoint, String clientId, String clientSecret, int retryInterval, int refreshInterval,
		boolean jwtEnabled, String jwtAudience) {

		/** Whether the API accepts the JWT access tokens issued by the IdP (Authorization: Bearer). */
		static final Setting<Boolean> JWT_ENABLED = Setting.bool("netshot.aaa.oidc.jwt.enabled").defaultValue(false).live();

		/** The audience the JWT access tokens must be issued for (default: the client ID). */
		static final Setting<String> JWT_AUDIENCE = Setting.text("netshot.aaa.oidc.jwt.audience").live();

		/** Name of the claim carrying the role. */
		static final Setting<String> ROLE_CLAIM = Setting.text("netshot.aaa.oidc.role.claimname").defaultValue("role").live();

		/** Value of the role claim for the read-only role. */
		static final Setting<String> READONLY_ROLE = roleSetting(User.Role.READONLY);

		/** Value of the role claim for the operator role. */
		static final Setting<String> OPERATOR_ROLE = roleSetting(User.Role.OPERATOR);

		/** Value of the role claim for the read-write role. */
		static final Setting<String> READWRITE_ROLE = roleSetting(User.Role.READWRITE);

		/** Value of the role claim for the execute-read-write role. */
		static final Setting<String> EXECUTEREADWRITE_ROLE = roleSetting(User.Role.EXECUTEREADWRITE);

		/** Value of the role claim for the admin role. */
		static final Setting<String> ADMIN_ROLE = roleSetting(User.Role.ADMIN);

		/** Level of an authenticated user without known role. */
		static final Setting<Integer> DEFAULT_LEVEL = Setting.integer("netshot.aaa.oidc.role.defaultlevel")
			.defaultValue(0).range(0, 9999).live();

		/** Name of the claim carrying the username. */
		static final Setting<String> USERNAME_CLAIM = Setting.text("netshot.aaa.oidc.usernameclaimname")
			.defaultValue("preferred_username").live();

		/** URL of the IdP (issuer). */
		static final Setting<String> IDP_URL = Setting.text("netshot.aaa.oidc.idp.url").live();

		/** Client ID. */
		static final Setting<String> CLIENT_ID = Setting.text("netshot.aaa.oidc.clientid").live();

		/** Client secret. */
		static final Setting<String> CLIENT_SECRET = Setting.text("netshot.aaa.oidc.clientsecret").secret().live();

		/** Interval (ms) before retrying to fetch the IdP metadata after a failure. */
		static final Setting<Integer> IDP_RETRY_INTERVAL = Setting.integer("netshot.aaa.oidc.idp.retryinterval")
			.defaultValue(30000).range(1000, Integer.MAX_VALUE).live();

		/** Interval (ms) between two fetches of the IdP metadata. */
		static final Setting<Integer> IDP_REFRESH_INTERVAL = Setting.integer("netshot.aaa.oidc.idp.refreshinterval")
			.defaultValue(43200000).range(60000, Integer.MAX_VALUE).live();

		private static Setting<String> roleSetting(User.Role role) {
			return Setting.text("netshot.aaa.oidc.role.%srole".formatted(role.getName().replaceAll("-", "")))
				.defaultValue(role.getName()).live();
		}

		/**
		 * Reads the settings.
		 * @param config the configuration
		 * @return the settings
		 */
		static Settings from(Config config) {
			Map<String, Integer> roles = new HashMap<>();
			for (User.Role role : User.Role.values()) {
				Setting<String> roleSetting = switch (role) {
					case READONLY -> READONLY_ROLE;
					case OPERATOR -> OPERATOR_ROLE;
					case READWRITE -> READWRITE_ROLE;
					case EXECUTEREADWRITE -> EXECUTEREADWRITE_ROLE;
					case ADMIN -> ADMIN_ROLE;
				};
				roles.put(config.get(roleSetting), role.getLevel());
			}
			return new Settings(config.get(ROLE_CLAIM), Map.copyOf(roles), config.get(DEFAULT_LEVEL),
				config.get(USERNAME_CLAIM), config.get(IDP_URL), config.get(CLIENT_ID), config.get(CLIENT_SECRET),
				config.get(IDP_RETRY_INTERVAL), config.get(IDP_REFRESH_INTERVAL),
				config.get(JWT_ENABLED), config.get(JWT_AUDIENCE));
		}
	}

	/** The settings, from the configuration. */
	private final ConfigStore.Binding<Settings> settings;

	/** The validation of the JWT access tokens (null until the IdP metadata is fetched). */
	private DefaultJWTProcessor<SecurityContext> jwtProcessor;

	/**
	 * Gets the settings of the OIDC authentication.
	 * @return the settings
	 */
	Settings settings() {
		return this.settings.get();
	}

	/**
	 * Fetches the metadata of the IdP (without holding the lock of the OIDC state).
	 * @param endpoint the URL of the IdP (issuer)
	 * @return the metadata
	 * @throws Exception if the metadata can't be fetched
	 */
	static OIDCProviderMetadata fetchIdpMetadata(String endpoint) throws Exception {
		log.debug("Retrieving OIDC metatada from issuer endpoint {}...", endpoint);
		OIDCProviderMetadata metadata = OIDCProviderMetadata.resolve(new Issuer(endpoint));
		log.trace("OIDC IDP metadata {}", metadata);
		return metadata;
	}

	/**
	 * Uses the metadata of the IdP: prepares the validation of the ID tokens (if a client is set: enables the
	 * OIDC login) and of the JWT access tokens.
	 * @param metadata the metadata of the IdP
	 * @throws GeneralException if the ID token validation can't be prepared
	 */
	synchronized void setIdpMetadata(OIDCProviderMetadata metadata) throws GeneralException {
		this.idTokenValidator = this.clientInfo == null ? null : IDTokenValidator.create(metadata, this.clientInfo);
		this.jwtProcessor = createJwtProcessor(metadata);
		this.idpMetadata = metadata;
	}

	/**
	 * Prepares the validation of the JWT access tokens: signature with the keys published by the IdP (JWKS,
	 * fetched and cached by the processor), with the asymmetric algorithms the IdP supports.
	 *
	 * @param metadata the metadata of the IdP
	 * @return the processor, or null if the IdP publishes no key set
	 */
	private static DefaultJWTProcessor<SecurityContext> createJwtProcessor(OIDCProviderMetadata metadata) {
		if (metadata.getJWKSetURI() == null) {
			log.warn("The OIDC IdP publishes no JWK set, the JWT access tokens can't be validated.");
			return null;
		}
		JWKSource<SecurityContext> keys;
		try {
			keys = JWKSourceBuilder.create(metadata.getJWKSetURI().toURL()).retrying(true).build();
		}
		catch (MalformedURLException e) {
			log.warn("Invalid JWK set URI of the OIDC IdP: {}.", metadata.getJWKSetURI());
			return null;
		}
		Set<JWSAlgorithm> algorithms = new HashSet<>();
		if (metadata.getIDTokenJWSAlgs() != null) {
			metadata.getIDTokenJWSAlgs().stream()
				.filter(a -> !JWSAlgorithm.Family.HMAC_SHA.contains(a))
				.forEach(algorithms::add);
		}
		if (algorithms.isEmpty()) {
			algorithms.add(JWSAlgorithm.RS256);
		}
		DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
		processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(JOSEObjectType.JWT, new JOSEObjectType("at+jwt"), null));
		processor.setJWSKeySelector(new JWSVerificationKeySelector<>(algorithms, keys));
		// The claims are checked by authenticateWithBearerToken, with the current settings
		processor.setJWTClaimsSetVerifier((_, _) -> {
		});
		return processor;
	}

	/**
	 * Sets the client information (before the IdP metadata is fetched).
	 * @param clientId the client ID, null if no client is configured (the IdP is then only used to validate
	 *        the JWT access tokens, there is no OIDC login)
	 * @param clientSecret the client secret
	 */
	synchronized void setClient(String clientId, String clientSecret) {
		if (clientId == null) {
			this.clientInfo = null;
			return;
		}
		OIDCClientMetadata clientMetadata = new OIDCClientMetadata();
		clientMetadata.applyDefaults();
		this.clientInfo = new OIDCClientInformation(new ClientID(clientId), null, clientMetadata, new Secret(clientSecret));
	}

	/**
	 * Disables the OIDC authentication, until the IdP metadata is fetched again.
	 */
	synchronized void disable() {
		this.idpMetadata = null;
		this.idTokenValidator = null;
		this.jwtProcessor = null;
	}

	/**
	 * Tells whether the OIDC login (web UI SSO) is available: the IdP metadata is fetched and a client is
	 * configured.
	 * @return true if the users can log in with OIDC
	 */
	public synchronized boolean isAvailable() {
		return this.idpMetadata != null && this.clientInfo != null;
	}

	/**
	 * Tells whether the IdP metadata is fetched (for the OIDC login and/or the JWT access tokens).
	 * @return true if the metadata is fetched
	 */
	synchronized boolean hasIdpMetadata() {
		return this.idpMetadata != null;
	}

	public synchronized URI getAuthorizationEndpointURI() {
		if (!this.isAvailable()) {
			return null;
		}
		return this.idpMetadata.getAuthorizationEndpointURI();
	}

	public synchronized String getClientId() {
		if (!this.isAvailable()) {
			return null;
		}
		return this.clientInfo.getID().getValue();
	}

	public synchronized URI getEndSessionEndpointURI() {
		if (!this.isAvailable()) {
			return null;
		}
		return this.idpMetadata.getEndSessionEndpointURI();
	}

	/**
	 * Exchange authorization code for ID/Access tokens.
	 * @param code = the authorization code
	 * @param redirectUrl = the redirect URL
	 * @return the OIDC tokens
	 * @throws Exception in case of problem
	 */
	private OIDCTokens codeToTokens(String code, URL redirectUrl) throws Exception {
		// Create token request
		AuthorizationCode authCode = new AuthorizationCode(code);
		AuthorizationGrant codeGrant = new AuthorizationCodeGrant(authCode, redirectUrl.toURI());

		// Client authentication
		ClientAuthentication clientAuth = new ClientSecretBasic(clientInfo.getID(), clientInfo.getSecret());

		// Prepare request
		TokenRequest tokenRequest = new TokenRequest
			.Builder(this.idpMetadata.getTokenEndpointURI(), clientAuth, codeGrant)
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
	public synchronized UiUser authenticateWithCode(String authorizationCode, URL redirectUrl) {
		if (!this.isAvailable()) {
			return null;
		}
		try {
			// Exchange authorization token for tokens
			final OIDCTokens tokens = codeToTokens(authorizationCode, redirectUrl);
			AAA_LOG.debug(
				MarkerFactory.getMarker("AAA"),
				"OIDC authorization code exchanged for OIDC tokens"
			);
			final IDTokenClaimsSet tokenClaims = this.idTokenValidator
				.validate(tokens.getIDToken(), null);
			AAA_LOG.debug(
				MarkerFactory.getMarker("AAA"),
				"ID token successfully validated"
			);
			Settings current = this.settings.get();
			String usernameClaim = current.usernameClaim();
			String roleClaim = current.roleClaim();
			String username = tokenClaims.getStringClaim(usernameClaim);
			if (username == null) {
				throw new RuntimeException("The ID token is missing the expected claim %s".formatted(usernameClaim));
			}
			int level = levelOf(current, tokenClaims.getClaim(roleClaim));
			UiUser user = UiUser.remote(username, level);
			AAA_LOG.info("User {} successfully authenticated via OIDC (level {})", username, level);
			return user;
		}
		catch (Exception e) {
			log.warn("Error while authenticating user based on OIDC authorization code", e);
			AAA_LOG.warn("Error while authenticating user based on OIDC authorization code", e);
			return null;
		}
	}

	/**
	 * Computes the level of a user from the value of the role claim: the highest level of the known roles,
	 * or the default level.
	 *
	 * @param current the settings
	 * @param roleClaimValue the value of the role claim (a string, a list of strings, or null)
	 * @return the level
	 */
	private static int levelOf(Settings current, Object roleClaimValue) {
		Set<String> passedRoles = new HashSet<>();
		if (roleClaimValue instanceof String roleName) {
			passedRoles.add(roleName);
		}
		else if (roleClaimValue instanceof Collection<?> roleNames) {
			roleNames.stream().filter(String.class::isInstance).map(String.class::cast).forEach(passedRoles::add);
		}
		Integer level = null;
		for (String passedRole : passedRoles) {
			Integer roleLevel = current.roles().get(passedRole);
			if (roleLevel != null && (level == null || roleLevel > level)) {
				level = roleLevel;
			}
		}
		return level == null ? current.defaultLevel() : level;
	}

	/**
	 * Authenticates the bearer of a JWT access token issued by the IdP: the signature (keys of the IdP), the
	 * type ({@code JWT} or {@code at+jwt}), the issuer, the audience ({@code netshot.aaa.oidc.jwt.audience}, or
	 * the client ID; tokens are refused if there is neither), the expiration and the not-before dates are checked; the user name and level are read as
	 * for an OIDC login (username claim, falling back to {@code sub}; role claim).
	 *
	 * @param token the token
	 * @return the authenticated user, or null if the token is invalid, or the JWT authentication is disabled
	 *         or not available (IdP metadata not fetched)
	 */
	public UiUser authenticateWithBearerToken(String token) {
		Settings current = this.settings.get();
		if (!current.jwtEnabled()) {
			return null;
		}
		DefaultJWTProcessor<SecurityContext> processor;
		String issuer;
		String clientId;
		synchronized (this) {
			processor = this.jwtProcessor;
			issuer = this.idpMetadata == null ? null : this.idpMetadata.getIssuer().getValue();
			clientId = this.clientInfo == null ? null : this.clientInfo.getID().getValue();
		}
		if (processor == null || issuer == null) {
			AAA_LOG.warn("JWT bearer token received while the OIDC IdP metadata isn't available.");
			return null;
		}
		String audience = current.jwtAudience() != null ? current.jwtAudience() : clientId;
		if (audience == null) {
			AAA_LOG.warn("JWT bearer token refused: no audience to check (set netshot.aaa.oidc.jwt.audience).");
			return null;
		}
		try {
			// Signature and type (the processor fetches and caches the keys, out of the lock)
			JWTClaimsSet claims = processor.process(token, null);
			new DefaultJWTClaimsVerifier<SecurityContext>(audience,
				new JWTClaimsSet.Builder().issuer(issuer).build(), Set.of("exp")).verify(claims, null);
			Object username = claims.getClaim(current.usernameClaim());
			if (!(username instanceof String)) {
				username = claims.getSubject();
			}
			if (username == null) {
				AAA_LOG.warn("JWT bearer token without username ({} or sub claim).", current.usernameClaim());
				return null;
			}
			int level = levelOf(current, claims.getClaim(current.roleClaim()));
			AAA_LOG.debug("User {} authenticated by a JWT bearer token (level {}).", username, level);
			return UiUser.remote((String) username, level);
		}
		catch (ParseException | BadJOSEException | JOSEException e) {
			AAA_LOG.warn("Invalid JWT bearer token: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Creates the OIDC authentication.
	 *
	 * @param config the configuration store, to read the settings from
	 */
	public Oidc(ConfigStore config) {
		this.settings = config.bind(Settings::from);
	}
}
