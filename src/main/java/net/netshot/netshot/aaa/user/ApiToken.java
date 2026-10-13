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
package net.netshot.netshot.aaa.user;

import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.platform.crypto.Sha2BasedHash;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;

@Entity
@XmlRootElement
@XmlAccessorType(XmlAccessType.NONE)
@Table(indexes = {
	@Index(name = "hashedTokenIndex", columnList = "hashedToken")
})
public final class ApiToken implements User {

	public static String hashToken(String token) {
		Sha2BasedHash hash = new Sha2BasedHash();
		hash.setSalt(null); // No salt
		hash.setIterations(1000);
		hash.digest(token);
		return hash.toHashString();
	}

	protected static final Pattern TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9]{32}$");

	/**
	 * Checks whether the given text is a valid token.
	 * @param token the token
	 * @return true if the given token is of valid format
	 */
	public static boolean isValidToken(String token) {
		return token != null && TOKEN_PATTERN.matcher(token).matches();
	}

	/** The id. */
	@Getter(onMethod = @__({
		@Id,
		@GeneratedValue(strategy = GenerationType.IDENTITY),
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private long id;

	/** The hashed token. */
	@Getter
	@Setter
	private String hashedToken;

	/** The description. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private String description;

	/** The level. */
	@Getter(onMethod = @__({
		@XmlElement, @JsonView(DefaultView.class)
	}))
	@Setter
	private int level = User.LEVEL_ADMIN;

	/**
	 * Instantiates a new API Token.
	 */
	protected ApiToken() {
	}

	public ApiToken(String description, String token, int level) {
		this.description = description;
		this.setToken(token);
		this.level = level;
	}

	public void setToken(String token) {
		this.hashedToken = hashToken(token);
	}

	/*(non-Javadoc)
	 * @see java.security.Principal#getName()
	 */
	@Override
	@Transient
	public String getName() {
		return String.format("API Token [%d: %s]", this.id, this.description);
	}

	@Override
	@Transient
	public String getUsername() {
		return getName();
	}

	@Override
	public String toString() {
		return "ApiToken [description=" + description + ", id=" + id + ", level=" + level + "]";
	}

}
