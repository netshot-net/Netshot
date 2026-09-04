package net.netshot.netshot;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RoutingHandler;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.util.StatusCodes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.MediaType;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FakeOidcIdpServer {

	private static final Logger logger = LoggerFactory.getLogger(FakeOidcIdpServer.class);

	public static class User {
		private String username;
		private String role;

		public User(String username, String role) {
			this.username = username;
			this.role = role;
		}
	}

	private static final URI DEFAULT_BASE_URI = URI.create("http://localhost:8980/");
	private static final String DISCOVERY_PATH = "/.well-known/openid-configuration";
	private static final String AUTH_PATH = "/protocol/openid-connect/auth";
	private static final String TOKEN_PATH = "/protocol/openid-connect/token";
	private static final String JWKS_PATH = "/protocol/openid-connect/certs";

	private static final String SAMPLE_KID = "ukbTXswbjExjfqPBKPREf8VULCASfzi05E97YKsj6g0";

	// Sample RSA key (PCKS8, PEM-encoded)
	private static final String SAMPLE_PRIVATE_KEY =
		"MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDLFrHaHNdJAyV3"
			+ "Tctaam0MX978CKEDKGlxeuRmSnjMVDfTljI7zThWowbJ4sqg6ICvDtTlfAAhIPxp"
			+ "spPYHITyA79PV57/Y/k3Cz55DMpm19YwJUbEcHSn2Z6fIcOmXanPiNUB3A1Zx/Jx"
			+ "ZHYPzgIaWGEwRTBECjSIlZwYM1iOVtZaMrOSqc5z2cfRVNfBYQ5/mDn3ZEGv+ny+"
			+ "auVEAgmhmlaGxgX7mLToRBzv0t9TNfM8kosaYBG+QanCh92vsswEAASUDtzzkmiN"
			+ "g1tQ0c8gew22hOJJnh/ohtn+mNHWkS5UISzBlpZWl9/6olv71s1tyvbZhfhyXp0x"
			+ "FN6qsK7zAgMBAAECggEAApruBiInlHBKmAvl7c9+sNFya7sNzoIQTc92dzKn93Fy"
			+ "U+MFDiWISvtry19lTqIc5uH8UvZ/wLnXf+DPRIIjjHOfp4kBHHD8xSF+S13U8A1l"
			+ "wH4oUoqUwuoOPsFXbRHRHonrtzEXWyb7xUubt7RihevLUPiz17OZaYUg/vutotaU"
			+ "p0pOiJ3OQStS4F44MIcc9xUUXo6j2OfkYRCIsgYhPdsjpGQhpEd6ANKPBkjqHoyQ"
			+ "B47ctALf2nlwtjtn/Wa1sctBzy4Oy3F2guNtaZZGnuhk8NE8yv/pDgFmABE8+cAZ"
			+ "9waivuf4OkbmRp7WWYIjA69bzcHJWWOZXjLHsPSHVQKBgQD8nLGbPTPwC2LK6nkk"
			+ "8mkrGn130z9YX3zUXUuIXXbfc1IT0cBGgLk+gHKgo75yqlQCa7/Tkz2ZJA2eu0vV"
			+ "D4IqpcgBCNlBKOIGG2ninvzy0dYzPP5LtwqFAjIxF63faMGU8+ym1/KMGzHqPoYz"
			+ "BiDaWJgp+UrxwuigPeOOH50rFQKBgQDNz/g2G4+6+JCNfBlQOYubeLODrOVb8qEj"
			+ "M2Gi1BUtqA+XmEElwtKWUXqgH+I9gKHRWo4AUD0Yi64f5T7zRwe0rDfUhDc67Wpz"
			+ "8HVm98fTRK6VOdwXRt/1jAchWG07otz+KOA2ou3nQqMmY52tKdDz1mL6FzJ/fcAe"
			+ "UyizQQtT5wKBgCVySPW9PdzAo1V3KpwqfyKPm7fOjd5Y0VVduxus1zlKjAk6F6mb"
			+ "3VoBinx7qXiv/SIavOXtNr1j1c0I8LXVxbLyvlJA8IuzNsY2/BxG+zI3nuwbh4rL"
			+ "yHhtGemjG/g5PDELc7JL4r2YLm8N87DOoMIdTfky5kQuY3OVmQzxbMf9AoGBAJXo"
			+ "MBuBEbyWxfs389waPhSc4uw658iEPlg8WZZXMaHSsqCxdmpBsE9qw42UC57ObY7m"
			+ "jV2vFAEn5Ek5GhPqnbM8aWHyd6QFP6946pp4SeUZNqxcu3F83y2js6HXHaD9bEf3"
			+ "j/Bb1jrGr70Le9KgDaE9e1Q7xz1TY7byzUdbThvrAoGBAIsjuzdd376LUb6ClFVL"
			+ "KMMCNFq9z2c8YHYITmKTRUVGtrKcS1daHByBiLA24jI43xzNVcjwaaI+OUmjH5XY"
			+ "qhWo2p2F+diSkZSK7wBq9wFQaURXDv36QzdVbspMQluruMmUa5FCnApNoX4hPIUS"
			+ "oby0A0cpHjPY+bR+KlAPivKg";

	// Sample IdP certificate for tests (private key above)
	private static final String SAMPLE_CERTIFICATE =
		"MIIClzCCAX8CBgGXq4B+JzANBgkqhkiG9w0BAQsFADAPMQ0wCwYDVQQDDAR0ZXN0MB4XDTI1MDYyNjA5"
			+ "MDgzNFoXDTM1MDYyNjA5MTAxNFowDzENMAsGA1UEAwwEdGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEP"
			+ "ADCCAQoCggEBAMsWsdoc10kDJXdNy1pqbQxf3vwIoQMoaXF65GZKeMxUN9OWMjvNOFajBsniyqDogK8O"
			+ "1OV8ACEg/Gmyk9gchPIDv09Xnv9j+TcLPnkMymbX1jAlRsRwdKfZnp8hw6Zdqc+I1QHcDVnH8nFkdg/O"
			+ "AhpYYTBFMEQKNIiVnBgzWI5W1loys5KpznPZx9FU18FhDn+YOfdkQa/6fL5q5UQCCaGaVobGBfuYtOhE"
			+ "HO/S31M18zySixpgEb5BqcKH3a+yzAQABJQO3POSaI2DW1DRzyB7DbaE4kmeH+iG2f6Y0daRLlQhLMGW"
			+ "llaX3/qiW/vWzW3K9tmF+HJenTEU3qqwrvMCAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAasPZXVq0qJn4"
			+ "mt1h0wZSlpyPm54hV2MNlxE7bd1I2fRtxmBTTUuOLEi6ca5iStKoHO6N7kCGlSWLs49/dqeNKjHweCV7"
			+ "daK7t8tp8sKNPEcDoz0jFiVXVeUTLm0VjrdbdCSpamm9/Z/4EMaxE5SQKTHEHu83uqr1biNjr6n82WSj"
			+ "xWMdh/hDQbtS08rItYSrkxJ3PLIdhxqJ/uUTZ3EqE3Ulcc+coiIzeyVdO/r4sZJvG2XLyidGsxQBcCef"
			+ "5AlNWd7EQUc1lioS1HNSqTiXwxKvfAd06bGCN3mz8z6XgpFNTRDtHsmK3L7h5EYNa3DBKg/mgxfE3DJt"
			+ "XXCXJHBCzQ==";

	@Getter
	@Setter
	private URI baseUri;
	@Getter
	@Setter
	private String clientId;
	@Getter
	@Setter
	private String clientSecret;
	@Getter
	@Setter
	private URI redirectUri;
	private PrivateKey privKey;
	private Certificate certificate;

	private Map<String, User> authorizationCodes = new ConcurrentHashMap<>();

	private Undertow server;

	public FakeOidcIdpServer(URI baseUri) throws GeneralSecurityException {
		this.baseUri = baseUri;
		this.loadSampleCert();
	}

	public FakeOidcIdpServer() throws GeneralSecurityException {
		this(DEFAULT_BASE_URI);
	}

	public void registerClient(String clientId, String clientSecret, URI redirectUri) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.redirectUri = redirectUri;
	}

	public void addAuthorizatioCode(String code, String username, String role) {
		this.authorizationCodes.put(code, new User(username, role));
	}

	private void loadSampleCert() throws NoSuchAlgorithmException, InvalidKeySpecException, CertificateException {
		CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
		InputStream is = new ByteArrayInputStream(Base64.getDecoder().decode(SAMPLE_CERTIFICATE));
		this.certificate = certFactory.generateCertificate(is);
		PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(SAMPLE_PRIVATE_KEY));
		KeyFactory kf = KeyFactory.getInstance("RSA");
		this.privKey = kf.generatePrivate(privSpec);
	}

	private void checkBasicAuth(HeaderMap headerMap) throws ForbiddenException {
		final String basicPrefix = "Basic ";
		String authHeader = headerMap.get(Headers.AUTHORIZATION).peekFirst();
		if (authHeader == null) {
			throw new ForbiddenException("Authorization header not present");
		}
		if (!authHeader.startsWith(basicPrefix)) {
			throw new ForbiddenException("Authorization header is not basic auth");
		}
		String encodedCredentials = authHeader.substring(basicPrefix.length());
		String decodedCredentials = new String(Base64.getDecoder().decode(encodedCredentials), StandardCharsets.UTF_8);
		String expectedCredentials = "%s:%s".formatted(this.clientId, this.clientSecret);
		if (!expectedCredentials.equals(decodedCredentials)) {
			throw new ForbiddenException("Invalid credentials");
		}
	}

	private String signEncodeToken(ObjectNode token)
		throws SignatureException, InvalidKeyException, NoSuchAlgorithmException {
		ObjectNode header = JsonNodeFactory.instance.objectNode().put("alg", "RS256").put("typ", "JWT").put("kid",
			SAMPLE_KID);
		String jwt = encodeJson(header) + "." + encodeJson(token);
		Signature signature = Signature.getInstance("SHA256withRSA");
		signature.initSign(privKey);
		signature.update(jwt.getBytes(StandardCharsets.UTF_8));
		byte[] signBytes = signature.sign();
		jwt = jwt + "." + Base64.getUrlEncoder().encodeToString(signBytes);
		return jwt;
	}

	private String encodeJson(ObjectNode token) {
		return Base64.getUrlEncoder().encodeToString(token.toString().getBytes(StandardCharsets.UTF_8));
	}

	private ObjectNode generateIdToken(User user) {
		ObjectNode token = JsonNodeFactory.instance.objectNode()
			.put("exp", Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond()).put("iat", Instant.now().getEpochSecond())
			.put("auth_time", Instant.now().getEpochSecond()).put("jti", "f9e39317-81e0-4334-a75e-994003f1d160")
			.put("iss", this.baseUri.toString()).put("typ", "ID").put("azp", this.clientId)
			.put("sid", "5eac7f33-3818-4438-b20a-78dfaec74abd").put("scope", "openid").put("acr", "1")
			.put("aud", this.clientId).put("sub", "8b17b6a1-3c91-491b-9f9f-d7c314f08271")
			.put("preferred_username", user.username).put("role", user.role);
		return token;
	}

	private ObjectNode generateAccessToken(User user) {
		ObjectNode token = JsonNodeFactory.instance.objectNode()
			.put("exp", Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond()).put("iat", Instant.now().getEpochSecond())
			.put("jti", "trrtna:df789356-42fe-4bf3-8607-303161e9fe0e").put("iss", this.baseUri.toString())
			.put("typ", "Bearer").put("azp", this.clientId).put("sid", "c83ec19e-fc41-4764-9fe6-2b92b46fa10e")
			.put("acr", "1").put("preferred_username", user.username);
		return token;
	}

	public void start() throws IOException {

		RoutingHandler routingHandler = new RoutingHandler();
		routingHandler.get(DISCOVERY_PATH, new HttpHandler() {
			@Override
			public void handleRequest(HttpServerExchange exchange) throws Exception {
				ObjectNode data = JsonNodeFactory.instance.objectNode()
					.put("issuer", baseUri.toString())
					.put("authorization_endpoint", baseUri.resolve(AUTH_PATH).toString())
					.put("token_endpoint", baseUri.resolve(TOKEN_PATH).toString())
					.put("jwks_uri", baseUri.resolve(JWKS_PATH).toString());
				data.putArray("grant_types_supported")
					.add("authorization_code");
				data.putArray("response_types_supported")
					.add("code")
					.add("none")
					.add("id_token")
					.add("token");
				data.putArray("claims_supported")
					.add("aud")
					.add("sub")
					.add("iss")
					.add("auth_time")
					.add("preferred_username")
					.add("email");
				data.putArray("subject_types_supported")
					.add("public");
				data.putArray("id_token_signing_alg_values_supported")
					.add("RS256");
				data.putArray("token_endpoint_auth_methods_supported")
					.add("client_secret_basic");
				String content = data.toPrettyString();
				exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, MediaType.APPLICATION_JSON);
				exchange.setResponseContentLength(content.length());
				exchange.getResponseSender().send(content);
			}
		});
		routingHandler.post(TOKEN_PATH, new HttpHandler() {
			@Override
			public void handleRequest(HttpServerExchange exchange) throws Exception {
				try {
					checkBasicAuth(exchange.getRequestHeaders());
					if (!MediaType.APPLICATION_FORM_URLENCODED_TYPE.withCharset(StandardCharsets.UTF_8.name())
						.equals(MediaType.valueOf(
							exchange.getRequestHeaders().getFirst(Headers.CONTENT_TYPE)))) {
						throw new Exception("Received token request content type is not form-urlencoded");
					}

					FormParserFactory.Builder builder = FormParserFactory.builder();
					try (FormDataParser formDataParser = builder.build().createParser(exchange)) {
						FormData formData = formDataParser.parseBlocking();
						if (!"authorization_code".equals(
							formData.get("grant_type").peek().getValue())) {
							throw new Exception("The received grant_type is not correct");
						}
						if (!redirectUri.toString().equals(
							formData.get("redirect_uri").peek().getValue())) {
							throw new Exception("The received redirect_uri is not correct");
						}
						String code = formData.get("code").peek().getValue();
						if (code == null) {
							throw new ForbiddenException("Missing code parameter");
						}
						User user = authorizationCodes.remove(code);
						if (user == null) {
							throw new ForbiddenException("Invalid authorization code");
						}
						ObjectNode data = JsonNodeFactory.instance.objectNode()
							.put("token_type", "Bearer")
							.put("refresh_token", "...")
							.put("access_token", signEncodeToken(generateAccessToken(user)))
							.put("id_token", signEncodeToken(generateIdToken(user)))
							.put("expires_in", 3600);
						String content = data.toPrettyString();
						exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, MediaType.APPLICATION_JSON);
						exchange.setResponseContentLength(content.length());
						exchange.getResponseSender().send(content);
					}
				}
				catch (ForbiddenException e) {
					exchange.setStatusCode(StatusCodes.UNAUTHORIZED);
					exchange.setReasonPhrase("Unauthorized");
				}
				catch (Exception e) {
					logger.warn("Rejecting OIDC token request with a 400", e);
					exchange.setStatusCode(StatusCodes.BAD_REQUEST);
					exchange.setReasonPhrase("Bad request");
				}
			}
		});
		routingHandler.get(JWKS_PATH, new HttpHandler() {
			@Override
			public void handleRequest(HttpServerExchange exchange) throws Exception {
				ObjectNode data = JsonNodeFactory.instance.objectNode();
				BigInteger certModulus = ((RSAPublicKey) certificate.getPublicKey()).getModulus();
				BigInteger certExponent = ((RSAPublicKey) certificate.getPublicKey()).getPublicExponent();
				ObjectNode key = JsonNodeFactory.instance.objectNode().put("kid", SAMPLE_KID).put("kty", "RSA").put("alg", "RS256")
					.put("n", Base64.getUrlEncoder().encodeToString(certModulus.toByteArray()))
					.put("e", Base64.getUrlEncoder().encodeToString(certExponent.toByteArray()));
				key.putArray("x5c").add(Base64.getEncoder().encodeToString(certificate.getEncoded()));
				data.putArray("keys").add(key);
				String content = data.toPrettyString();
				exchange.getResponseHeaders().add(Headers.CONTENT_TYPE, MediaType.APPLICATION_JSON);
				exchange.setResponseContentLength(content.length());
				exchange.getResponseSender().send(content);
			}
		});

		Undertow.Builder builder = Undertow
			.builder()
			.addHttpListener(this.baseUri.getPort(), this.baseUri.getHost())
			.setHandler(routingHandler);
		this.server = builder.build();
		this.server.start();
	}

	public void shutdown() {
		if (this.server != null) {
			this.server.stop();
		}
	}

}
