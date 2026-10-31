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
package net.netshot.netshot.api.rest.v1;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * The structure of a v1 representation (a DTO record, or a sealed interface of records with a {@code type}
 * property), as the field selection sees it: the properties, which ones are in the brief view
 * ({@link Brief}), which ones refer to other objects ({@link Ref}), and the structure of the nested objects.
 * The models are built once per class, by reflection.
 */
final class DtoModel {

	/** The properties always in the brief view. */
	static final Set<String> ALWAYS_BRIEF = Set.of("id", "url", "display");

	/**
	 * A property.
	 *
	 * @param name the name of the property
	 * @param brief whether the property is in the brief view
	 * @param ref whether the property refers to other objects
	 * @param target the class of the nested object (or of the elements of the nested list), if it has a model;
	 *        null for a value
	 */
	record Prop(String name, boolean brief, boolean ref, Class<?> target) {

		/**
		 * Gets the model of the nested object.
		 *
		 * @return the model, or null for a value
		 */
		DtoModel model() {
			return this.target == null ? null : DtoModel.of(this.target);
		}
	}

	/** The cache of the models. */
	private static final Map<Class<?>, DtoModel> MODELS = new ConcurrentHashMap<>();

	/** The properties, for a record. */
	private final Map<String, Prop> props;

	/** The models of the subtypes by type name, for a polymorphic type (empty otherwise). */
	private final Map<String, Class<?>> subtypes;

	/** The name of the type property, for a polymorphic type (null otherwise). */
	private final String typeProperty;

	private DtoModel(Map<String, Prop> props, Map<String, Class<?>> subtypes, String typeProperty) {
		this.props = props;
		this.subtypes = subtypes;
		this.typeProperty = typeProperty;
	}

	/**
	 * Tells whether a class has a model: a record, or an interface with subtypes.
	 *
	 * @param type the class
	 * @return true if the class is a structured representation
	 */
	static boolean isModel(Class<?> type) {
		return type.isRecord() || (type.isInterface() && type.isAnnotationPresent(JsonSubTypes.class));
	}

	/**
	 * Gets the model of a class.
	 *
	 * @param type the class (a record, or an interface with {@link JsonSubTypes})
	 * @return the model
	 */
	static DtoModel of(Class<?> type) {
		DtoModel model = MODELS.get(type);
		if (model == null) {
			model = build(type);
			MODELS.putIfAbsent(type, model);
		}
		return model;
	}

	private static DtoModel build(Class<?> type) {
		if (type.isRecord()) {
			Map<String, Prop> props = new LinkedHashMap<>();
			for (RecordComponent component : type.getRecordComponents()) {
				if (component.isAnnotationPresent(JsonIgnore.class)) {
					continue;
				}
				JsonProperty property = component.getAnnotation(JsonProperty.class);
				String name = property != null && !property.value().isEmpty() ? property.value() : component.getName();
				props.put(name, new Prop(name, component.isAnnotationPresent(Brief.class) || ALWAYS_BRIEF.contains(name),
					component.isAnnotationPresent(Ref.class), targetOf(component.getGenericType())));
			}
			return new DtoModel(props, Map.of(), null);
		}
		JsonSubTypes subTypes = type.getAnnotation(JsonSubTypes.class);
		if (subTypes == null) {
			throw new IllegalArgumentException("No model for " + type);
		}
		JsonTypeInfo typeInfo = type.getAnnotation(JsonTypeInfo.class);
		Map<String, Class<?>> subtypes = new LinkedHashMap<>();
		for (JsonSubTypes.Type subType : subTypes.value()) {
			subtypes.put(subType.name(), subType.value());
		}
		return new DtoModel(Map.of(), subtypes, typeInfo == null ? "type" : typeInfo.property());
	}

	/**
	 * Finds the class of the nested model of a property.
	 *
	 * @param type the type of the component
	 * @return the class of the object or of the elements of the collection if it has a model, null otherwise
	 */
	private static Class<?> targetOf(Type type) {
		if (type instanceof Class<?> c) {
			return isModel(c) ? c : null;
		}
		if (type instanceof ParameterizedType p && p.getRawType() instanceof Class<?> raw
			&& Collection.class.isAssignableFrom(raw) && p.getActualTypeArguments().length == 1) {
			return targetOf(p.getActualTypeArguments()[0]);
		}
		return null;
	}

	/**
	 * Tells whether the model is polymorphic.
	 *
	 * @return true for a sealed interface of records
	 */
	boolean isPolymorphic() {
		return this.typeProperty != null;
	}

	/**
	 * Gets the model applying to a node: the model of its type for a polymorphic model.
	 *
	 * @param node the object node
	 * @return the model of the records (this model if it isn't polymorphic)
	 */
	DtoModel resolve(JsonNode node) {
		if (!this.isPolymorphic()) {
			return this;
		}
		JsonNode type = node.get(this.typeProperty);
		Class<?> subtype = type == null ? null : this.subtypes.get(type.asText());
		return subtype == null ? this : of(subtype);
	}

	/**
	 * Gets a property, looking into every subtype for a polymorphic model (for the validation of the paths).
	 *
	 * @param name the name of the property
	 * @return the property, or null if no record of the model has it
	 */
	Prop anyProp(String name) {
		if (!this.isPolymorphic()) {
			return this.props.get(name);
		}
		if (name.equals(this.typeProperty)) {
			return new Prop(name, true, false, null);
		}
		for (Class<?> subtype : this.subtypes.values()) {
			Prop prop = of(subtype).anyProp(name);
			if (prop != null) {
				return prop;
			}
		}
		return null;
	}

	/**
	 * Gets a property of a record model.
	 *
	 * @param name the name of the property
	 * @return the property, or null
	 */
	Prop prop(String name) {
		return this.props.get(name);
	}

	/**
	 * Gets the names of the properties of a record model.
	 *
	 * @return the names
	 */
	Set<String> names() {
		return this.props.keySet();
	}

	/**
	 * Gets the names of the properties of the brief view of a record model.
	 *
	 * @return the names
	 */
	Set<String> briefNames() {
		Set<String> names = new LinkedHashSet<>();
		this.props.values().stream().filter(Prop::brief).forEach(p -> names.add(p.name()));
		return names;
	}
}
