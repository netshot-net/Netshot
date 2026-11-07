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

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;
import net.netshot.netshot.platform.query.FieldSpec;
import net.netshot.netshot.platform.query.FieldType;
import net.netshot.netshot.platform.query.Lookup;
import net.netshot.netshot.platform.query.QuerySchema;

/**
 * Completes the OpenAPI document of the v1 API with what the annotations don't tell:
 * <ul>
 * <li>the properties of the representations (answers) are all written, null included: they are marked required, and
 * nullable unless their type is primitive (the request types are left as they are: their absent properties have a
 * meaning);</li>
 * <li>the common parameters of the lists (paging, sort, search, selection), of the statistics, and the selection
 * parameters of the operations answering a single representation;</li>
 * <li>the filters of the lists and statistics (a parameter per field and lookup), and the fields they can be sorted,
 * searched or grouped by, from their schema ({@link ListFilters});</li>
 * <li>the optional properties of the requests can be null (null clears them);</li>
 * <li>a Java {@code Object} (any JSON value, as the values of the driver options) is any value, not an empty object;</li>
 * <li>the type value of each subtype of a polymorphic representation;</li>
 * <li>the properties of the brief view of each representation ({@value #BRIEF_EXTENSION}), so that the clients can type
 * the items of a list read in its default view.</li>
 * </ul>
 */
final class V1OpenApiEnhancer {

	/** The extension listing the properties of the brief view of a representation. */
	static final String BRIEF_EXTENSION = "x-netshot-brief";

	private V1OpenApiEnhancer() {
	}

	/**
	 * Completes a document.
	 *
	 * @param openApi the document built from the resources
	 * @param resources the resource classes
	 */
	@SuppressWarnings("rawtypes")
	static void enhance(OpenAPI openApi, Set<Class<?>> resources) {
		Map<String, Class<?>> answers = new LinkedHashMap<>();
		Map<String, Class<?>> polymorphic = new LinkedHashMap<>();
		for (Class<?> resource : resources) {
			for (Method method : resource.getDeclaredMethods()) {
				String httpMethod = httpMethodOf(method);
				if (httpMethod == null) {
					continue;
				}
				collect(method.getGenericReturnType(), answers, polymorphic, new HashSet<>());
				for (Type parameter : method.getGenericParameterTypes()) {
					collectRequest(parameter, polymorphic, new HashSet<>());
				}
				Operation operation = operation(openApi, resource, method, httpMethod);
				if (operation == null) {
					continue;
				}
				ListFilters filters = method.getAnnotation(ListFilters.class);
				QuerySchema querySchema = filters == null ? null : filters.value().schema();
				if (method.getReturnType() == ListResponse.class && method.isAnnotationPresent(Unpaged.class)) {
					addSelectionParameters(operation);
				}
				else if (method.getReturnType() == ListResponse.class) {
					addListParameters(operation, querySchema);
					addFilterParameters(operation, querySchema);
				}
				else if (method.getReturnType() == StatsResponse.class) {
					addStatsParameters(operation, querySchema);
					addFilterParameters(operation, querySchema);
				}
				else if (DtoModel.isModel(method.getReturnType())) {
					// A single representation (read or written): the selection applies to it
					addSelectionParameters(operation);
				}
			}
		}
		Map<String, Schema> schemas = openApi.getComponents() == null ? null : openApi.getComponents().getSchemas();
		if (schemas == null) {
			return;
		}
		for (Map.Entry<String, Class<?>> answer : answers.entrySet()) {
			Schema<?> schema = schemas.get(answer.getKey());
			if (schema != null) {
				complete(schema, answer.getValue());
				if (answer.getValue() != ListResponse.class && DtoModel.isModel(answer.getValue())) {
					schema.addExtension(BRIEF_EXTENSION, List.copyOf(DtoModel.of(answer.getValue()).briefNames()));
				}
			}
		}
		for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
			if (entry.getKey().endsWith("Request")) {
				nullableOptionals(entry.getValue());
			}
			anyValues(entry.getValue(), new HashSet<>());
		}
		for (Map.Entry<String, Schema> entry : schemas.entrySet()) {
			Class<?> base = polymorphic.get(entry.getKey());
			if (base != null && entry.getValue().getDiscriminator() != null) {
				flattenSubtypes(schemas, entry.getValue(), base);
			}
		}
	}

	/**
	 * Collects the record types an answer is made of (by schema name), through generic types, components and subtypes.
	 *
	 * @param type the type
	 * @param answers the records found, by schema name
	 * @param polymorphic the polymorphic types found (with subtypes), by schema name
	 * @param seen the types already walked
	 */
	private static void collect(Type type, Map<String, Class<?>> answers, Map<String, Class<?>> polymorphic, Set<Type> seen) {
		if (!seen.add(type)) {
			return;
		}
		if (type instanceof ParameterizedType parameterized) {
			Class<?> raw = (Class<?>) parameterized.getRawType();
			for (Type argument : parameterized.getActualTypeArguments()) {
				collect(argument, answers, polymorphic, seen);
			}
			if (raw == ListResponse.class && parameterized.getActualTypeArguments()[0] instanceof Class<?> item) {
				answers.put("ListResponse" + schemaName(item), ListResponse.class);
			}
			return;
		}
		if (!(type instanceof Class<?> clazz)) {
			return;
		}
		if (clazz.isRecord() && clazz.getPackageName().startsWith(V1Application.class.getPackageName())
			&& !clazz.isAnnotationPresent(ReadOnlyFrom.class) && !clazz.getSimpleName().endsWith("Request")) {
			if (clazz != ListResponse.class) {
				answers.put(schemaName(clazz), clazz);
			}
			for (RecordComponent component : clazz.getRecordComponents()) {
				collect(component.getGenericType(), answers, polymorphic, seen);
			}
		}
		JsonSubTypes subTypes = clazz.getAnnotation(JsonSubTypes.class);
		if (subTypes != null) {
			polymorphic.put(schemaName(clazz), clazz);
			for (JsonSubTypes.Type subType : subTypes.value()) {
				collect(subType.value(), answers, polymorphic, seen);
			}
		}
	}

	private static String schemaName(Class<?> clazz) {
		io.swagger.v3.oas.annotations.media.Schema schema = clazz.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
		return schema != null && !schema.name().isEmpty() ? schema.name() : clazz.getSimpleName();
	}

	/**
	 * Marks the properties of a representation required, and nullable unless primitive.
	 *
	 * @param schema the schema of the representation
	 * @param record the record of the representation
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void complete(Schema schema, Class<?> record) {
		Map<String, Schema> properties = schema.getProperties();
		if (properties == null && schema.getAllOf() != null) {
			// A subtype of a polymorphic representation: its properties are in an inline part of allOf
			for (Object part : schema.getAllOf()) {
				if (part instanceof Schema partSchema && partSchema.getProperties() != null) {
					complete(partSchema, record);
				}
			}
			return;
		}
		if (properties == null) {
			return;
		}
		List<String> required = new ArrayList<>();
		for (RecordComponent component : record.getRecordComponents()) {
			if (component.isAnnotationPresent(JsonIgnore.class)) {
				continue;
			}
			JsonProperty jsonProperty = component.getAnnotation(JsonProperty.class);
			String name = jsonProperty != null && !jsonProperty.value().isEmpty() ? jsonProperty.value() : component.getName();
			Schema property = properties.get(name);
			if (property == null) {
				continue;
			}
			required.add(name);
			if (!component.getType().isPrimitive()) {
				if (property.get$ref() != null) {
					// A sibling of $ref is ignored (OpenAPI 3.0): wrap the reference to make it nullable
					Schema wrapper = new Schema<>().allOf(List.of(new Schema<>().$ref(property.get$ref())));
					wrapper.setNullable(true);
					wrapper.setReadOnly(property.getReadOnly());
					wrapper.setDescription(property.getDescription());
					properties.put(name, wrapper);
				}
				else {
					property.setNullable(true);
				}
			}
		}
		schema.setRequired(required);
	}

	/**
	 * Collects the polymorphic types (sealed interfaces with subtypes) of a request body, through the components of
	 * its records.
	 *
	 * @param type the type of the body
	 * @param polymorphic the polymorphic types found, by schema name
	 * @param seen the types already walked
	 */
	private static void collectRequest(Type type, Map<String, Class<?>> polymorphic, Set<Type> seen) {
		if (!seen.add(type)) {
			return;
		}
		if (type instanceof ParameterizedType parameterized) {
			for (Type argument : parameterized.getActualTypeArguments()) {
				collectRequest(argument, polymorphic, seen);
			}
			return;
		}
		if (!(type instanceof Class<?> clazz) || !clazz.getPackageName().startsWith(V1Application.class.getPackageName())) {
			return;
		}
		JsonSubTypes subTypes = clazz.getAnnotation(JsonSubTypes.class);
		if (subTypes != null) {
			polymorphic.put(schemaName(clazz), clazz);
			for (JsonSubTypes.Type subType : subTypes.value()) {
				collectRequest(subType.value(), polymorphic, seen);
			}
		}
		if (clazz.isRecord()) {
			for (RecordComponent component : clazz.getRecordComponents()) {
				collectRequest(component.getGenericType(), polymorphic, seen);
			}
		}
	}

	/**
	 * Makes the subtypes of a polymorphic schema self-contained: the reference to the base (an allOf part) is removed
	 * (the base is the union of the subtypes: the reference made the types circular), and the type property of each
	 * subtype is its value only. The discriminator maps the values to the subtypes.
	 *
	 * @param schemas the schemas of the document
	 * @param base the base schema
	 * @param type the sealed interface
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void flattenSubtypes(Map<String, Schema> schemas, Schema base, Class<?> type) {
		String property = base.getDiscriminator().getPropertyName();
		List<Schema> oneOf = new ArrayList<>();
		for (JsonSubTypes.Type subType : type.getAnnotation(JsonSubTypes.class).value()) {
			String name = schemaName(subType.value());
			base.getDiscriminator().mapping(subType.name(), "#/components/schemas/" + name);
			oneOf.add(new Schema<>().$ref("#/components/schemas/" + name));
			Schema schema = schemas.get(name);
			if (schema == null) {
				continue;
			}
			if (schema.getAllOf() != null) {
				for (Object part : schema.getAllOf()) {
					Schema partSchema = (Schema) part;
					if (partSchema.get$ref() == null) {
						schema.setProperties(partSchema.getProperties());
						schema.setRequired(partSchema.getRequired());
					}
				}
				schema.setAllOf(null);
				schema.setType("object");
			}
			if (schema.getProperties() == null) {
				schema.setProperties(new LinkedHashMap<>());
			}
			if (!(schema.getProperties().get(property) instanceof Schema)) {
				// The type property written by Jackson, not declared by the record
				Map<String, Schema> properties = new LinkedHashMap<>();
				properties.put(property, new StringSchema());
				properties.putAll(schema.getProperties());
				schema.setProperties(properties);
			}
			Schema typeSchema = (Schema) schema.getProperties().get(property);
			typeSchema.setNullable(null);
			typeSchema.setEnum(List.of(subType.name()));
			List<String> required = schema.getRequired() == null ? new ArrayList<>() : new ArrayList<>(schema.getRequired());
			// Always written in an answer, always needed in a request
			if (!required.contains(property)) {
				required.add(0, property);
			}
			schema.setRequired(required);
		}
		if (base.getOneOf() == null || base.getOneOf().isEmpty()) {
			base.setOneOf(oneOf);
		}
	}

	/**
	 * Makes the optional properties of a request nullable (an explicit null clears the value).
	 *
	 * @param schema the schema of the request
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void nullableOptionals(Schema schema) {
		if (schema.getAllOf() != null) {
			for (Object part : schema.getAllOf()) {
				if (part instanceof Schema partSchema) {
					nullableOptionals(partSchema);
				}
			}
		}
		Map<String, Schema> properties = schema.getProperties();
		if (properties == null) {
			return;
		}
		List<String> required = schema.getRequired() == null ? List.of() : schema.getRequired();
		for (Map.Entry<String, Schema> property : properties.entrySet()) {
			if (required.contains(property.getKey()) || Boolean.TRUE.equals(property.getValue().getReadOnly())) {
				continue;
			}
			Schema value = property.getValue();
			if (value.get$ref() != null) {
				Schema wrapper = new Schema<>().allOf(List.of(new Schema<>().$ref(value.get$ref())));
				wrapper.setNullable(true);
				wrapper.setDescription(value.getDescription());
				property.setValue(wrapper);
			}
			else {
				value.setNullable(true);
			}
		}
	}

	/**
	 * Turns the schemas of a Java {@code Object} (an object type without any property) into "any value", in a schema and
	 * the schemas it contains.
	 *
	 * @param schema the schema
	 * @param seen the schemas already walked
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static void anyValues(Schema schema, Set<Schema> seen) {
		if (schema == null || !seen.add(schema)) {
			return;
		}
		if (schema.getProperties() != null) {
			for (Object property : schema.getProperties().values()) {
				Schema propertySchema = (Schema) property;
				if (isAnyObject(propertySchema)) {
					propertySchema.setType(null);
					propertySchema.setTypes(null);
				}
				anyValues(propertySchema, seen);
			}
		}
		if (schema.getAdditionalProperties() instanceof Schema additional) {
			if (isAnyObject(additional)) {
				schema.setAdditionalProperties(Boolean.TRUE);
			}
			else {
				anyValues(additional, seen);
			}
		}
		if (schema.getItems() != null) {
			if (isAnyObject(schema.getItems())) {
				schema.getItems().setType(null);
				schema.getItems().setTypes(null);
			}
			anyValues(schema.getItems(), seen);
		}
		if (schema.getAllOf() != null) {
			for (Object part : schema.getAllOf()) {
				anyValues((Schema) part, seen);
			}
		}
	}

	@SuppressWarnings("rawtypes")
	private static boolean isAnyObject(Schema schema) {
		return "object".equals(schema.getType()) && schema.getProperties() == null && schema.getAdditionalProperties() == null
			&& schema.get$ref() == null && schema.getAllOf() == null && schema.getOneOf() == null && schema.getAnyOf() == null;
	}

	private static String httpMethodOf(Method method) {
		for (Annotation annotation : method.getAnnotations()) {
			HttpMethod httpMethod = annotation.annotationType().getAnnotation(HttpMethod.class);
			if (httpMethod != null) {
				return httpMethod.value();
			}
		}
		return null;
	}

	private static Operation operation(OpenAPI openApi, Class<?> resource, Method method, String httpMethod) {
		Path classPath = resource.getAnnotation(Path.class);
		Path methodPath = method.getAnnotation(Path.class);
		String path = ("/" + (classPath == null ? "" : classPath.value()) + "/" + (methodPath == null ? "" : methodPath.value()))
			.replaceAll("/+", "/").replaceAll("\\{([^}:]+):[^}]*}", "{$1}");
		if (path.length() > 1 && path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		PathItem item = openApi.getPaths() == null ? null : openApi.getPaths().get(path);
		if (item == null) {
			return null;
		}
		return item.readOperationsMap().get(PathItem.HttpMethod.valueOf(httpMethod));
	}

	private static void add(Operation operation, String name, Schema<?> schema, String description) {
		List<Parameter> parameters = operation.getParameters() == null ? new ArrayList<>() : new ArrayList<>(operation.getParameters());
		for (Parameter parameter : parameters) {
			if (name.equals(parameter.getName())) {
				return;
			}
		}
		parameters.add(new Parameter().in("query").name(name).required(false).schema(schema).description(description));
		operation.setParameters(parameters);
	}

	private static void addListParameters(Operation operation, QuerySchema querySchema) {
		add(operation, ListParams.OFFSET, new IntegerSchema(), "Number of items to skip (paging)");
		add(operation, ListParams.LIMIT, new IntegerSchema(), "Number of items of the page (0: all the items, unless a maximum page size is set)");
		add(operation, ListParams.SORT, new StringSchema(), "Sort fields, comma-separated, - for a descending order (name,-changeDate)"
			+ fieldList(querySchema, FieldSpec::sortable));
		add(operation, ListParams.SEARCH, new StringSchema(), "Free text search" + fieldList(querySchema, FieldSpec::searchable));
		add(operation, ListParams.COUNT, new BooleanSchema(), "true to count the matching items (in count; not counted by default)");
		addSelectionParameters(operation);
	}

	/**
	 * Declares the filters of a list: a parameter per field and lookup (field=value, field__lookup=value), which can be
	 * repeated.
	 *
	 * @param operation the operation of the list (or of its statistics)
	 * @param querySchema the schema of the list (null if not named, nothing is declared then)
	 */
	private static void addFilterParameters(Operation operation, QuerySchema querySchema) {
		if (querySchema == null) {
			return;
		}
		for (FieldSpec field : querySchema.fields().values()) {
			for (Lookup lookup : Lookup.values()) {
				if (!field.lookups().contains(lookup)) {
					continue;
				}
				String name = lookup == Lookup.EXACT ? field.name() : field.name() + QuerySchema.LOOKUP_SEPARATOR + lookup.suffix();
				Schema<?> value = filterValueSchema(field, lookup);
				String description = "Filter: " + field.name() + " " + lookup.description();
				if (lookup == Lookup.EMPTY) {
					add(operation, name, value, description);
				}
				else {
					// Repeated: any of the values (none of them for a negated lookup)
					add(operation, name, new ArraySchema().items(value), description);
					Parameter parameter = operation.getParameters().getLast();
					parameter.style(Parameter.StyleEnum.FORM).explode(true);
				}
			}
		}
	}

	private static Schema<?> filterValueSchema(FieldSpec field, Lookup lookup) {
		if (lookup == Lookup.EMPTY) {
			return new BooleanSchema();
		}
		if (lookup == Lookup.REGEX || lookup == Lookup.IREGEX) {
			return new StringSchema();
		}
		return switch (field.type()) {
			case STRING -> new StringSchema();
			case INTEGER -> new IntegerSchema().format("int64");
			case BOOLEAN -> new BooleanSchema();
			case ENUM -> {
				StringSchema values = new StringSchema();
				values.setEnum(field.enumValues());
				yield values;
			}
			// A date (midnight UTC) or a date and time with offset
			case DATE -> new StringSchema().description("ISO-8601 date or date and time with offset");
			case INET -> new StringSchema().description(lookup == Lookup.NET ? "IP subnet (address/length)" : "IP address");
		};
	}

	/**
	 * Lists the fields of a schema which have a capability, for a parameter description.
	 *
	 * @param querySchema the schema (null: nothing)
	 * @param capability the capability (sortable...)
	 * @return the text ("" if no schema)
	 */
	private static String fieldList(QuerySchema querySchema, Predicate<FieldSpec> capability) {
		if (querySchema == null) {
			return "";
		}
		List<String> names = querySchema.fields().values().stream().filter(capability).map(FieldSpec::name).toList();
		return names.isEmpty() ? "" : "; fields: " + String.join(", ", names);
	}

	private static void addSelectionParameters(Operation operation) {
		StringSchema view = new StringSchema();
		view.setEnum(List.of("brief", "full"));
		add(operation, Selection.VIEW, view, "View of the items: brief or full");
		add(operation, Selection.FIELDS, new StringSchema(), "Properties to write, comma-separated (dotted paths for nested ones)");
		add(operation, Selection.OMIT, new StringSchema(), "Properties not to write, comma-separated");
		add(operation, Selection.EXPAND, new StringSchema(), "References to write in their full view (written even when the view or the fields don't include them), comma-separated");
	}

	private static void addStatsParameters(Operation operation, QuerySchema querySchema) {
		add(operation, ListParams.SEARCH, new StringSchema(), "Free text search (as in the list)");
		add(operation, StatsParams.GROUP_BY, new StringSchema(), "Fields to count by (up to two), comma-separated"
			+ fieldList(querySchema, FieldSpec::groupable));
		StringSchema interval = new StringSchema();
		interval.setEnum(List.of("hour", "day", "week", "month"));
		add(operation, StatsParams.INTERVAL, interval, "Size of the time buckets");
		add(operation, StatsParams.ON, new StringSchema(), "Date field of the time buckets"
			+ fieldList(querySchema, field -> field.type() == FieldType.DATE));
		add(operation, StatsParams.FROM, new StringSchema().format("date-time"), "Start of the range (included)");
		add(operation, StatsParams.TO, new StringSchema().format("date-time"), "End of the range (excluded)");
		add(operation, StatsParams.TIME_ZONE, new StringSchema(), "Time zone of the buckets (IANA name, UTC by default)");
	}
}
