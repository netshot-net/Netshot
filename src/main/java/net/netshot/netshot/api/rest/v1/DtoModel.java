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
package net.netshot.netshot.api.rest.v1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
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
import com.fasterxml.jackson.annotation.JsonTypeName;

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
	 * @param accessor the accessor of the record component (null for the type property of a polymorphic type)
	 */
	record Prop(String name, boolean brief, boolean ref, Class<?> target, MethodHandle accessor) {

		/**
		 * Reads the property of a record.
		 *
		 * @param record the record
		 * @return the value
		 */
		Object get(Object record) {
			try {
				return this.accessor.invoke(record);
			}
			catch (Throwable e) {
				throw new IllegalStateException("Unable to read the property " + this.name, e);
			}
		}

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

	/**
	 * The type property Jackson adds to a record (subtype of a polymorphic type whose type is not one of its
	 * components, {@link JsonTypeInfo.As#PROPERTY}): its name and value, null otherwise.
	 */
	private final Map.Entry<String, String> typeId;

	private DtoModel(Map<String, Prop> props, Map<String, Class<?>> subtypes, String typeProperty, Map.Entry<String, String> typeId) {
		this.props = props;
		this.subtypes = subtypes;
		this.typeProperty = typeProperty;
		this.typeId = typeId;
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
				MethodHandle accessor;
				try {
					accessor = MethodHandles.publicLookup().unreflect(component.getAccessor());
				}
				catch (IllegalAccessException e) {
					throw new IllegalArgumentException("Inaccessible property " + name + " of " + type, e);
				}
				props.put(name, new Prop(name, component.isAnnotationPresent(Brief.class) || ALWAYS_BRIEF.contains(name),
					component.isAnnotationPresent(Ref.class), targetOf(component.getGenericType()), accessor));
			}
			return new DtoModel(props, Map.of(), null, typeIdOf(type));
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
		return new DtoModel(Map.of(), subtypes, typeInfo == null ? "type" : typeInfo.property(), null);
	}

	/**
	 * Gets the type property Jackson writes for a record, as the subtype of a polymorphic interface which doesn't
	 * have it as a component ({@link JsonTypeInfo.As#PROPERTY}, named types).
	 *
	 * @param type the record class
	 * @return the name and the value of the property, or null
	 */
	private static Map.Entry<String, String> typeIdOf(Class<?> type) {
		for (Class<?> parent : type.getInterfaces()) {
			JsonTypeInfo typeInfo = parent.getAnnotation(JsonTypeInfo.class);
			if (typeInfo == null || typeInfo.include() != JsonTypeInfo.As.PROPERTY || typeInfo.use() != JsonTypeInfo.Id.NAME) {
				continue;
			}
			String name = null;
			JsonTypeName typeName = type.getAnnotation(JsonTypeName.class);
			if (typeName != null && !typeName.value().isEmpty()) {
				name = typeName.value();
			}
			JsonSubTypes subTypes = parent.getAnnotation(JsonSubTypes.class);
			if (name == null && subTypes != null) {
				for (JsonSubTypes.Type subType : subTypes.value()) {
					if (subType.value() == type && !subType.name().isEmpty()) {
						name = subType.name();
					}
				}
			}
			String property = typeInfo.property().isEmpty() ? typeInfo.use().getDefaultPropertyName() : typeInfo.property();
			return Map.entry(property, name == null ? type.getSimpleName() : name);
		}
		return null;
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
			return new Prop(name, true, false, null, null);
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
	 * Gets the properties of a record model, in their order.
	 *
	 * @return the properties
	 */
	Collection<Prop> props() {
		return this.props.values();
	}

	/**
	 * Gets the type property Jackson adds to the records of this model (see {@link #typeIdOf(Class)}).
	 *
	 * @return the name and value of the property, or null
	 */
	Map.Entry<String, String> typeId() {
		return this.typeId;
	}

	/**
	 * Gets the model of a representation: the model of its own class for a polymorphic type, this one otherwise.
	 *
	 * @param value the representation (a record)
	 * @return the model
	 */
	DtoModel resolve(Object value) {
		return this.isPolymorphic() && value.getClass().isRecord() ? of(value.getClass()) : this;
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
