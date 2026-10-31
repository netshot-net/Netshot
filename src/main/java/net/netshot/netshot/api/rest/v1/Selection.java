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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.ws.rs.core.MultivaluedMap;
import net.netshot.netshot.platform.error.NetshotException;

/**
 * What a client asked to see of a representation:
 * <ul>
 * <li>{@code view=brief|full}: the brief view (the {@link Brief} properties, with {@code id}, {@code url} and
 * {@code display}), or every property;</li>
 * <li>{@code fields=name,mgmtDomain.name}: only these properties (and {@code id}), dotted paths for the nested
 * objects; it replaces the view;</li>
 * <li>{@code omit=attributes,accesses}: every property but these;</li>
 * <li>{@code expand=mgmtDomain}: the full form of these referenced objects ({@link Ref}), which are otherwise
 * written in their brief view.</li>
 * </ul>
 * The parameters can be repeated or hold comma-separated values. The paths are checked against the
 * {@link DtoModel} of the representation (400 for an unknown property). The selection is applied by
 * {@link V1EntityProvider} to the JSON tree of the response, whatever the format.
 */
public final class Selection {

	/** The views. */
	public enum View {
		/** The brief view. */
		BRIEF,
		/** All the properties. */
		FULL
	}

	/** Name of the view parameter. */
	public static final String VIEW = "view";

	/** Name of the fields parameter. */
	public static final String FIELDS = "fields";

	/** Name of the omit parameter. */
	public static final String OMIT = "omit";

	/** Name of the expand parameter. */
	public static final String EXPAND = "expand";

	/** The query parameters of the selection. */
	public static final Set<String> PARAMETERS = Set.of(VIEW, FIELDS, OMIT, EXPAND);

	/** The name of the request attribute holding the selection to apply to the response. */
	static final String ATTRIBUTE = Selection.class.getName();

	/**
	 * A tree of property paths: {@code a.b,a.c,d} is {@code {a: {b: {}, c: {}}, d: {}}}.
	 */
	private static final class PathTree {
		private final Map<String, PathTree> children = new LinkedHashMap<>();

		PathTree child(String name) {
			return this.children.get(name);
		}

		boolean isLeaf() {
			return this.children.isEmpty();
		}

		void add(List<String> path) {
			PathTree tree = this;
			for (String name : path) {
				tree = tree.children.computeIfAbsent(name, _ -> new PathTree());
			}
		}
	}

	/** The model of the representation. */
	private final DtoModel model;

	/** The view. */
	private final View view;

	/** The selected fields (null if not restricted). */
	private final PathTree fields;

	/** The omitted fields. */
	private final PathTree omit;

	/** The expanded references. */
	private final PathTree expand;

	private Selection(DtoModel model, View view, PathTree fields, PathTree omit, PathTree expand) {
		this.model = model;
		this.view = view;
		this.fields = fields;
		this.omit = omit;
		this.expand = expand;
	}

	/**
	 * Reads the selection of the request.
	 *
	 * @param params the query parameters
	 * @param type the class of the representation (of the items, for a list)
	 * @param defaultView the view when the request doesn't tell
	 * @return the selection
	 * @throws NetshotException if a parameter is invalid
	 */
	public static Selection of(MultivaluedMap<String, String> params, Class<?> type, View defaultView) {
		DtoModel model = DtoModel.of(type);
		View view = defaultView;
		String viewText = single(params, VIEW);
		if (viewText != null) {
			try {
				view = View.valueOf(viewText.trim().toUpperCase(Locale.ROOT));
			}
			catch (IllegalArgumentException e) {
				throw NetshotException.invalidParameter(VIEW, "Invalid view '%s': brief or full is expected".formatted(viewText));
			}
		}
		PathTree fields = params.containsKey(FIELDS) ? paths(params, FIELDS, model, false) : null;
		PathTree omit = paths(params, OMIT, model, false);
		PathTree expand = paths(params, EXPAND, model, true);
		return new Selection(model, view, fields, omit, expand);
	}

	/**
	 * Builds the selection writing everything (full view, no restriction).
	 *
	 * @param type the class of the representation
	 * @return the selection
	 */
	public static Selection full(Class<?> type) {
		return new Selection(DtoModel.of(type), View.FULL, null, new PathTree(), new PathTree());
	}

	/**
	 * Tells whether a property (top level path) is part of the selection: to skip loading what won't be
	 * written.
	 *
	 * @param name the name of the property
	 * @return true if the property will be written
	 */
	public boolean includes(String name) {
		if (this.fields != null) {
			return this.fields.child(name) != null;
		}
		PathTree omitted = this.omit.child(name);
		if (omitted != null && omitted.isLeaf()) {
			return false;
		}
		DtoModel.Prop prop = this.model.anyProp(name);
		return this.view == View.FULL || (prop != null && prop.brief());
	}

	/**
	 * Tells whether a reference (top level property) is written in its full view (expanded, or a nested field
	 * selected): to load the full referenced object only then.
	 *
	 * @param name the name of the property
	 * @return true if the referenced object is written in full
	 */
	public boolean expands(String name) {
		if (!this.includes(name)) {
			return false;
		}
		PathTree selected = this.fields == null ? null : this.fields.child(name);
		return this.expand.child(name) != null || (selected != null && !selected.isLeaf());
	}

	/**
	 * Tells whether the selection writes no other top level property than the given ones (to choose a lighter
	 * query when it is enough).
	 *
	 * @param available the properties a lighter query fills
	 * @return true if every written property is available
	 */
	public boolean onlyIncludes(Set<String> available) {
		return this.model.names().stream().filter(this::includes).allMatch(available::contains);
	}

	private static String single(MultivaluedMap<String, String> params, String name) {
		List<String> values = params.get(name);
		if (values == null || values.isEmpty()) {
			return null;
		}
		if (values.size() > 1) {
			throw NetshotException.invalidParameter(name, "The parameter '%s' can only be given once".formatted(name));
		}
		return values.get(0);
	}

	/**
	 * Reads and checks the paths of a parameter.
	 *
	 * @param params the query parameters
	 * @param parameter the parameter name
	 * @param root the model of the representation
	 * @param refs whether the last element of each path must be a reference ({@code expand})
	 * @return the tree of the paths
	 */
	private static PathTree paths(MultivaluedMap<String, String> params, String parameter, DtoModel root, boolean refs) {
		PathTree tree = new PathTree();
		List<String> values = params.get(parameter);
		if (values == null) {
			return tree;
		}
		for (String value : values) {
			for (String item : value.split(",")) {
				String pathText = item.trim();
				if (pathText.isEmpty()) {
					continue;
				}
				List<String> path = List.of(pathText.split("\\."));
				DtoModel model = root;
				for (int i = 0; i < path.size(); i++) {
					DtoModel.Prop prop = model == null ? null : model.anyProp(path.get(i));
					if (prop == null) {
						throw NetshotException.invalidParameter(parameter,
							"Unknown property '%s' in parameter '%s'".formatted(pathText, parameter));
					}
					boolean last = i == path.size() - 1;
					if (refs && last && !prop.ref()) {
						throw NetshotException.invalidParameter(parameter,
							"The property '%s' isn't a reference to another object and can't be expanded".formatted(pathText));
					}
					model = prop.model();
				}
				tree.add(path);
			}
		}
		return tree;
	}

	/**
	 * Applies the selection to the JSON tree of a representation (in place).
	 *
	 * @param node the representation (an object, or an array of objects)
	 * @return the node
	 */
	public JsonNode apply(JsonNode node) {
		prune(node, this.model, this.view, this.fields, this.omit, this.expand);
		return node;
	}

	private static void prune(JsonNode node, DtoModel model, View view, PathTree fields, PathTree omit, PathTree expand) {
		if (node == null || model == null) {
			return;
		}
		if (node.isArray()) {
			for (JsonNode element : node) {
				prune(element, model, view, fields, omit, expand);
			}
			return;
		}
		if (!(node instanceof ObjectNode object)) {
			return;
		}
		DtoModel record = model.resolve(object);
		Set<String> keep = null;
		if (fields != null && !fields.isLeaf()) {
			keep = new LinkedHashSet<>(fields.children.keySet());
			keep.add("id");
		}
		else if (view == View.BRIEF) {
			keep = record.briefNames();
		}
		if (keep != null && model.isPolymorphic()) {
			keep.add("type");
		}
		Iterator<Map.Entry<String, JsonNode>> it = object.properties().iterator();
		while (it.hasNext()) {
			Map.Entry<String, JsonNode> entry = it.next();
			String name = entry.getKey();
			PathTree omitted = omit == null ? null : omit.child(name);
			if ((keep != null && !keep.contains(name)) || (omitted != null && omitted.isLeaf())) {
				it.remove();
				continue;
			}
			DtoModel.Prop prop = record.prop(name);
			if (prop == null || prop.target() == null) {
				continue;
			}
			PathTree childFields = fields == null ? null : fields.child(name);
			PathTree childExpand = expand == null ? null : expand.child(name);
			boolean expanded = childExpand != null || (childFields != null && !childFields.isLeaf());
			View childView = prop.ref() && !expanded ? View.BRIEF : View.FULL;
			prune(entry.getValue(), prop.model(), childView,
				childFields == null || childFields.isLeaf() ? null : childFields,
				omitted, childExpand);
		}
	}
}
