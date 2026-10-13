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

import org.hibernate.HibernateException;
import org.hibernate.boot.model.naming.EntityNaming;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.ImplicitAnyDiscriminatorColumnNameSource;
import org.hibernate.boot.model.naming.ImplicitAnyKeyColumnNameSource;
import org.hibernate.boot.model.naming.ImplicitBasicColumnNameSource;
import org.hibernate.boot.model.naming.ImplicitCollectionTableNameSource;
import org.hibernate.boot.model.naming.ImplicitDiscriminatorColumnNameSource;
import org.hibernate.boot.model.naming.ImplicitEntityNameSource;
import org.hibernate.boot.model.naming.ImplicitForeignKeyNameSource;
import org.hibernate.boot.model.naming.ImplicitIdentifierColumnNameSource;
import org.hibernate.boot.model.naming.ImplicitIndexColumnNameSource;
import org.hibernate.boot.model.naming.ImplicitIndexNameSource;
import org.hibernate.boot.model.naming.ImplicitJoinColumnNameSource;
import org.hibernate.boot.model.naming.ImplicitJoinTableNameSource;
import org.hibernate.boot.model.naming.ImplicitMapKeyColumnNameSource;
import org.hibernate.boot.model.naming.ImplicitNamingStrategy;
import org.hibernate.boot.model.naming.ImplicitPrimaryKeyJoinColumnNameSource;
import org.hibernate.boot.model.naming.ImplicitTenantIdColumnNameSource;
import org.hibernate.boot.model.naming.ImplicitUniqueKeyNameSource;
import org.hibernate.boot.model.naming.NamingHelper;
import org.hibernate.boot.model.source.spi.AttributePath;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.internal.util.StringHelper;

public class ImprovedImplicitNamingStrategy implements ImplicitNamingStrategy {
	/**
	 * The INSTANCE.
	 */
	public static final ImprovedImplicitNamingStrategy INSTANCE = new ImprovedImplicitNamingStrategy();

	/**
	 * Constructor.
	 */
	public ImprovedImplicitNamingStrategy() {
	}

	@Override
	public Identifier determinePrimaryTableName(ImplicitEntityNameSource source) {
		if (source == null) {
			// should never happen, but to be defensive...
			throw new HibernateException("Entity naming information was not provided.");
		}

		String tableName = transformEntityName(source.getEntityNaming());

		if (tableName == null) {
			// todo : add info to error message - but how to know what to write since we
			// failed to interpret the naming source
			throw new HibernateException("Could not determine primary table name for entity");
		}
		return toIdentifier(tableName, source.getBuildingContext());
	}

	protected String transformEntityName(EntityNaming entityNaming) {
		// prefer the JPA entity name, if specified...
		if (StringHelper.isNotEmpty(entityNaming.getJpaEntityName())) {
			return entityNaming.getJpaEntityName();
		}
		else {
			// otherwise, use the Hibernate entity name
			return StringHelper.unqualify(entityNaming.getEntityName());
		}
	}

	@Override
	public Identifier determineJoinTableName(ImplicitJoinTableNameSource source) {
		final String ownerPortion = source.getOwningPhysicalTableName();
		final String ownedPortion;
		if (source.getAssociationOwningAttributePath() != null) {
			ownedPortion = transformAttributePath(source.getAssociationOwningAttributePath());
		}
		else {
			ownedPortion = source.getNonOwningPhysicalTableName();
		}

		return toIdentifier(ownerPortion + "_" + ownedPortion, source.getBuildingContext());
	}

	@Override
	public Identifier determineCollectionTableName(ImplicitCollectionTableNameSource source) {
		final String owningEntity = transformEntityName(source.getOwningEntityNaming());
		final String name = transformAttributePath(source.getOwningAttributePath());
		final String entityName;
		if (owningEntity != null && !"".equals(owningEntity.trim())) {
			entityName = owningEntity + "_" + name;
		}
		else {
			entityName = name;
		}
		return toIdentifier(entityName, source.getBuildingContext());
	}

	@Override
	public Identifier determineIdentifierColumnName(ImplicitIdentifierColumnNameSource source) {
		return toIdentifier(transformAttributePath(source.getIdentifierAttributePath()), source.getBuildingContext());
	}

	@Override
	public Identifier determineDiscriminatorColumnName(ImplicitDiscriminatorColumnNameSource source) {
		return toIdentifier(source.getBuildingContext().getMappingDefaults().getImplicitDiscriminatorColumnName(),
			source.getBuildingContext());
	}

	@Override
	public Identifier determineTenantIdColumnName(ImplicitTenantIdColumnNameSource source) {
		return toIdentifier(source.getBuildingContext().getMappingDefaults().getImplicitTenantIdColumnName(),
			source.getBuildingContext());
	}

	@Override
	public Identifier determineBasicColumnName(ImplicitBasicColumnNameSource source) {
		return toIdentifier(transformAttributePath(source.getAttributePath()), source.getBuildingContext());
	}

	@Override
	public Identifier determineJoinColumnName(ImplicitJoinColumnNameSource source) {
		final String name;

		if (source.getNature() == ImplicitJoinColumnNameSource.Nature.ELEMENT_COLLECTION) {
			name = transformEntityName(source.getEntityNaming()) + '_' + source.getReferencedColumnName().getText();
		}
		else {
			if (source.getAttributePath() == null) {
				name = source.getReferencedTableName().getText();
			}
			else {
				name = transformAttributePath(source.getAttributePath());
			}
		}
		return toIdentifier(name, source.getBuildingContext());
	}

	/**
	 * The determinePrimaryTableName.
	 *
	 * @param source
	 *                 the source.
	 * @return the identifier.
	 */
	@Override
	public Identifier determinePrimaryKeyJoinColumnName(ImplicitPrimaryKeyJoinColumnNameSource source) {
		return source.getReferencedPrimaryKeyColumnName();
	}

	/**
	 * The determinePrimaryTableName.
	 *
	 * @param source
	 *                 the source.
	 * @return the identifier.
	 */
	@Override
	public Identifier determineAnyDiscriminatorColumnName(ImplicitAnyDiscriminatorColumnNameSource source) {
		return toIdentifier(
			transformAttributePath(source.getAttributePath()) + "_"
				+ source.getBuildingContext().getMappingDefaults().getImplicitDiscriminatorColumnName(),
			source.getBuildingContext());
	}

	/**
	 * The determinePrimaryTableName.
	 *
	 * @param source
	 *                 the source.
	 * @return the identifier.
	 */
	@Override
	public Identifier determineAnyKeyColumnName(ImplicitAnyKeyColumnNameSource source) {
		return toIdentifier(
			transformAttributePath(source.getAttributePath()) + "_"
				+ source.getBuildingContext().getMappingDefaults().getImplicitIdColumnName(),
			source.getBuildingContext());
	}

	/**
	 * The determinePrimaryTableName.
	 *
	 * @param source
	 *                 the source.
	 * @return the identifier.
	 */
	@Override
	public Identifier determineMapKeyColumnName(ImplicitMapKeyColumnNameSource source) {
		return toIdentifier(transformAttributePath(source.getPluralAttributePath()) + "_KEY",
			source.getBuildingContext());
	}

	/**
	 * The determinePrimaryTableName.
	 *
	 * @param source
	 *                 the source.
	 * @return the identifier.
	 */
	@Override
	public Identifier determineListIndexColumnName(ImplicitIndexColumnNameSource source) {
		return toIdentifier(transformAttributePath(source.getPluralAttributePath()) + "_ORDER",
			source.getBuildingContext());
	}

	/**
	 * The determinePrimaryTableName.
	 *
	 * @param source
	 *                 the source.
	 * @return the identifier.
	 */
	@Override
	public Identifier determineForeignKeyName(ImplicitForeignKeyNameSource source) {
		return toIdentifier(NamingHelper.INSTANCE.generateHashedFkName("FK", source.getTableName(),
			source.getReferencedTableName(), source.getColumnNames()), source.getBuildingContext());
	}

	/**
	 * The determinePrimaryTableName.
	 *
	 * @param source
	 *                 the source.
	 * @return the identifier.
	 */
	@Override
	public Identifier determineUniqueKeyName(ImplicitUniqueKeyNameSource source) {
		return toIdentifier(
			NamingHelper.INSTANCE.generateHashedConstraintName("UK", source.getTableName(), source.getColumnNames()),
			source.getBuildingContext());
	}

	/**
	 * The determinePrimaryTableName.
	 *
	 * @param source
	 *                 the source.
	 * @return the identifier.
	 */
	@Override
	public Identifier determineIndexName(ImplicitIndexNameSource source) {
		return toIdentifier(
			NamingHelper.INSTANCE.generateHashedConstraintName("IDX", source.getTableName(), source.getColumnNames()),
			source.getBuildingContext());
	}

	/**
	 * For JPA standards we typically need the unqualified name. However, a more
	 * usable impl tends to use the whole path. This method provides an easy hook
	 * for subclasses to accomplish that
	 *
	 * @param attributePath
	 *                        The attribute path
	 * @return The extracted name
	 */
	protected String transformAttributePath(AttributePath attributePath) {
		return attributePath.getProperty();
	}

	/**
	 * Easy hook to build an Identifier using the keyword safe IdentifierHelper.
	 *
	 * @param stringForm
	 *                          The String form of the name
	 * @param buildingContext
	 *                          Access to the IdentifierHelper
	 * @return The identifier
	 */
	protected Identifier toIdentifier(String stringForm, MetadataBuildingContext buildingContext) {

		Identifier i = buildingContext.getMetadataCollector().getDatabase().getJdbcEnvironment().getIdentifierHelper()
			.toIdentifier(stringForm);
		return i;
	}
}
