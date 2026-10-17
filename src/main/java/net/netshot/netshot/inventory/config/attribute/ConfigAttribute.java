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
package net.netshot.netshot.inventory.config.attribute;

import java.util.Objects;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import net.netshot.netshot.inventory.config.Config;

@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.CHAR)
@DiscriminatorValue("A")
public abstract class ConfigAttribute {

	@Getter(onMethod = @__({
		@Id, @GeneratedValue(strategy = GenerationType.IDENTITY)
	}))
	@Setter
	protected long id;

	@Getter
	@Setter
	protected String name;

	@Getter(onMethod = @__({
		@ManyToOne
	}))
	@Setter
	protected Config config;

	protected ConfigAttribute() {

	}

	public ConfigAttribute(Config config, String name) {
		this.config = config;
		this.name = name;
	}


	@Transient
	public abstract String getAsText();


	@Transient
	public abstract Object getData();

	public abstract boolean valueEquals(ConfigAttribute other);

	/*(non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		return Objects.hash(this.name, this.config);
	}

	/*(non-Javadoc)
	 * @see java.lang.Object#equals()
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ConfigAttribute)) {
			return false;
		}
		ConfigAttribute other = (ConfigAttribute) obj;
		return Objects.equals(this.name, other.name)
			&& Objects.equals(this.config, other.config);
	}

}
