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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.Path;

/**
 * Completes the OpenAPI document of the v1 API with what the annotations don't tell:
 * <ul>
 * <li>the properties of the representations (answers) are all written, null included: they are marked required, and
 * nullable unless their type is primitive (the request types are left as they are: their absent properties have a
 * meaning);</li>
 * <li>the common parameters of the lists (paging, sort, search, selection) and of the statistics.</li>
 * </ul>
 */
final class V1OpenApiEnhancer {

	private V1OpenApiEnhancer() {
	}

	/**
	 * Completes a document.
	 *
	 * @param openApi the document built from the resources
	 * @param resources the resource classes
	 */
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
				Operation operation = operation(openApi, resource, method, httpMethod);
				if (operation == null) {
					continue;
				}
				if (method.getReturnType() == ListResponse.class) {
					addListParameters(operation);
				}
				else if (method.getReturnType() == StatsResponse.class) {
					addStatsParameters(operation);
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
			}
		}
		for (Map.Entry<String, Class<?>> base : polymorphic.entrySet()) {
			Schema<?> schema = schemas.get(base.getKey());
			if (schema != null && schema.getDiscriminator() != null) {
				// The value of the type property of each subtype (the generated client types depend on it)
				for (JsonSubTypes.Type subType : base.getValue().getAnnotation(JsonSubTypes.class).value()) {
					schema.getDiscriminator().mapping(subType.name(), "#/components/schemas/" + schemaName(subType.value()));
				}
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

	private static void addListParameters(Operation operation) {
		add(operation, ListParams.OFFSET, new IntegerSchema(), "Number of items to skip (paging)");
		add(operation, ListParams.LIMIT, new IntegerSchema(), "Number of items of the page");
		add(operation, ListParams.SORT, new StringSchema(), "Sort fields, comma-separated, - for a descending order (name,-changeDate)");
		add(operation, ListParams.SEARCH, new StringSchema(), "Free text search");
		add(operation, ListParams.COUNT, new BooleanSchema(), "false to skip the count of the matching items");
		addSelectionParameters(operation);
		operation.setDescription((operation.getDescription() == null ? "" : operation.getDescription() + " ")
			+ "Any other parameter is a filter on a field: field=value or field__lookup=value (see the API documentation).");
	}

	private static void addSelectionParameters(Operation operation) {
		StringSchema view = new StringSchema();
		view.setEnum(List.of("brief", "full"));
		add(operation, Selection.VIEW, view, "View of the items: brief or full");
		add(operation, Selection.FIELDS, new StringSchema(), "Properties to write, comma-separated (dotted paths for nested ones)");
		add(operation, Selection.OMIT, new StringSchema(), "Properties not to write, comma-separated");
		add(operation, Selection.EXPAND, new StringSchema(), "References to write in their full view, comma-separated");
	}

	private static void addStatsParameters(Operation operation) {
		add(operation, StatsParams.GROUP_BY, new StringSchema(), "Fields to count by (up to two), comma-separated");
		StringSchema interval = new StringSchema();
		interval.setEnum(List.of("hour", "day", "week", "month"));
		add(operation, StatsParams.INTERVAL, interval, "Size of the time buckets");
		add(operation, StatsParams.ON, new StringSchema(), "Date field of the time buckets");
		add(operation, StatsParams.FROM, new StringSchema().format("date-time"), "Start of the range (included)");
		add(operation, StatsParams.TO, new StringSchema().format("date-time"), "End of the range (excluded)");
		add(operation, StatsParams.TIME_ZONE, new StringSchema(), "Time zone of the buckets (IANA name, UTC by default)");
		operation.setDescription((operation.getDescription() == null ? "" : operation.getDescription() + " ")
			+ "Any other parameter is a filter of the list.");
	}
}
