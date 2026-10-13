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
package net.netshot.netshot;

import java.io.ByteArrayOutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonXmlBindJsonProvider;
import com.fasterxml.jackson.jakarta.rs.xml.JacksonXmlBindXMLProvider;
import com.fasterxml.jackson.jakarta.rs.yaml.JacksonXmlBindYAMLProvider;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.xml.bind.annotation.XmlRootElement;
import net.netshot.netshot.api.RestService.RsLightDevice;
import net.netshot.netshot.api.RestService.RsLightExemptedDevice;
import net.netshot.netshot.compliance.SoftwareRule;
import net.netshot.netshot.compliance.rules.TextRule;
import net.netshot.netshot.inventory.device.Device;
import net.netshot.netshot.platform.rest.RestViews.DefaultView;
import net.netshot.netshot.platform.rest.RestViews.RestApiView;


/**
 * Probe: how do DTO records behave under the REST serialization setup of
 * the web application (default view + disabled DEFAULT_VIEW_INCLUSION)?
 * This test documents findings; it changes no production code.
 */
class DtoSerializationProbeTest {

	// ------------------------------------------------------------------
	// Provider setup, copied from rest/NetshotWebApplication (constructor).
	// ------------------------------------------------------------------

	private static final JacksonXmlBindJsonProvider JSON;
	private static final JacksonXmlBindXMLProvider XML;
	private static final JacksonXmlBindYAMLProvider YAML;

	static {
		JSON = new JacksonXmlBindJsonProvider();
		JSON.setDefaultView(RestApiView.class);
		JSON.setMapper(JsonMapper.builder()
			.disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
			.build());
		XML = new JacksonXmlBindXMLProvider();
		XML.setDefaultView(RestApiView.class);
		XML.setMapper(XmlMapper.builder()
			.disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
			.build());
		YAML = new JacksonXmlBindYAMLProvider();
		YAML.setDefaultView(RestApiView.class);
		YAML.setMapper(YAMLMapper.builder()
			.disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
			.build());
	}

	// ------------------------------------------------------------------
	// Fake "resource" whose method annotations are handed to writeTo, as
	// Jersey does. The real resource methods carry @JsonView(RestApiView).
	// ------------------------------------------------------------------

	@Path("/probe")
	public static class ProbeResource {
		@GET
		@Path("/annotated-single")
		@Produces(MediaType.APPLICATION_JSON)
		@JsonView(RestApiView.class)
		public Object annotatedSingle() {
			return null;
		}

		@GET
		@Path("/legacy-list")
		@Produces(MediaType.APPLICATION_JSON)
		@JsonView(RestApiView.class)
		public List<RsLightDevice> legacyList() {
			return null;
		}

		@GET
		@Path("/dto-list")
		@Produces(MediaType.APPLICATION_JSON)
		@JsonView(RestApiView.class)
		public List<ClassLevelDefaultDto> dtoList() {
			return null;
		}

		@GET
		@Path("/plain")
		@Produces(MediaType.APPLICATION_JSON)
		@JsonView(RestApiView.class)
		public PlainDto plain() {
			return null;
		}

		@GET
		@Path("/plain-noview")
		@Produces(MediaType.APPLICATION_JSON)
		public PlainDto plainNoView() {
			return null;
		}

		@GET
		@Path("/classdefault")
		@Produces(MediaType.APPLICATION_JSON)
		@JsonView(RestApiView.class)
		public ClassLevelDefaultDto classDefault() {
			return null;
		}

		@GET
		@Path("/componentview")
		@Produces(MediaType.APPLICATION_JSON)
		@JsonView(RestApiView.class)
		public ComponentViewDto componentView() {
			return null;
		}

		@GET
		@Path("/composed")
		@Produces(MediaType.APPLICATION_JSON)
		@JsonView(RestApiView.class)
		public ComposedDto composed() {
			return null;
		}

		@GET
		@Path("/device-list")
		@Produces(MediaType.APPLICATION_JSON)
		@JsonView(RestApiView.class)
		public List<Device> deviceList() {
			return null;
		}

		@GET
		@Path("/named-polymorphic")
		@Produces(MediaType.APPLICATION_JSON)
		@JsonView(RestApiView.class)
		public List<NamedTaskDto> namedPolymorphic() {
			return null;
		}

		@GET
		@Path("/untyped-list")
		@Produces(MediaType.APPLICATION_JSON)
		@JsonView(RestApiView.class)
		public List<Object> untypedList() {
			return null;
		}

		@GET
		@Path("/named-single")
		@Produces(MediaType.APPLICATION_JSON)
		@JsonView(RestApiView.class)
		public Object namedSingle() {
			return null;
		}

		@GET
		@Path("/polymorphic")
		@Produces(MediaType.APPLICATION_JSON)
		@JsonView(RestApiView.class)
		public List<TaskDto> polymorphic() {
			return null;
		}
	}

	private static Method method(String name) {
		try {
			return ProbeResource.class.getMethod(name);
		}
		catch (NoSuchMethodException e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static String write(MessageBodyWriter provider, MediaType mt, String resourceMethod, Object value) throws Exception {
		Method m = method(resourceMethod);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Annotation[] annotations = m.getAnnotations();
		provider.writeTo(value, m.getReturnType(), m.getGenericReturnType(), annotations, mt, null, out);
		return out.toString(StandardCharsets.UTF_8).trim();
	}

	private static String json(String m, Object v) throws Exception {
		return write(JSON, MediaType.APPLICATION_JSON_TYPE, m, v);
	}

	private static String xml(String m, Object v) throws Exception {
		return write(XML, MediaType.APPLICATION_XML_TYPE, m, v);
	}

	private static String yaml(String m, Object v) throws Exception {
		return write(YAML, MediaType.valueOf("application/yaml"), m, v);
	}

	// ------------------------------------------------------------------
	// DTO candidates
	// ------------------------------------------------------------------

	/** Q1: no annotation at all. */
	public record PlainDto(long id, String name, Device.Status status, Boolean eol) {
	}

	/** Q2: class-level DefaultView. */
	@JsonView(DefaultView.class)
	public record ClassLevelDefaultDto(long id, String name, Device.Status status, Boolean eol) {
	}

	/** Q2: class-level RestApiView. */
	@JsonView(RestApiView.class)
	public record ClassLevelRestApiDto(long id, String name, Device.Status status, Boolean eol) {
	}

	/** Q2: view on some components only. */
	public record ComponentViewDto(@JsonView(DefaultView.class) long id, String name,
		@JsonView(RestApiView.class) Device.Status status, Boolean eol) {
	}

	/** Q3: composition. */
	@JsonView(DefaultView.class)
	public record ComposedDto(@JsonUnwrapped ClassLevelDefaultDto device, Date expirationDate) {
	}

	/** Q3: composition without class-level view on the outer record. */
	public record ComposedNoViewDto(@JsonUnwrapped ClassLevelDefaultDto device, Date expirationDate) {
	}

	/** Q4: Jackson XML root element. */
	@JacksonXmlRootElement(localName = "rootNamedDto")
	@JsonView(DefaultView.class)
	public record RootNamedDto(long id, String name) {
	}

	/** Q4: JAXB root element on a record. */
	@XmlRootElement(name = "jaxbRoot")
	@JsonView(DefaultView.class)
	public record JaxbRootDto(long id, String name) {
	}

	/** Q4: JAXB root element, no name given. */
	@XmlRootElement
	@JsonView(DefaultView.class)
	public record JaxbDefaultRootDto(long id, String name) {
	}

	/** Q5: polymorphism. */
	@JsonView(DefaultView.class)
	@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
	@JsonSubTypes({
		@JsonSubTypes.Type(TakeSnapshotTaskDto.class),
		@JsonSubTypes.Type(RunScriptTaskDto.class),
	})
	public sealed interface TaskDto permits TakeSnapshotTaskDto, RunScriptTaskDto {
		long id();
	}

	/** Q5: first subtype. */
	@JsonView(DefaultView.class)
	public record TakeSnapshotTaskDto(long id, String comments) implements TaskDto {
	}

	/** Q5: second subtype. */
	@JsonView(DefaultView.class)
	public record RunScriptTaskDto(long id, String script) implements TaskDto {
	}

	/** Q3: full light device shape, equivalent to RestService.RsLightDevice. */
	@JsonView(DefaultView.class)
	public record LightDeviceDto(long id, String name, String family, String mgmtAddress, Device.Status status,
		String driver, Boolean eol, Boolean eos, Boolean configCompliant, SoftwareRule.ConformanceLevel softwareLevel,
		Device.NetworkClass networkClass) {
	}

	/** Q3: composition equivalent to RestService.RsLightExemptedDevice. */
	@JsonView(DefaultView.class)
	public record ExemptedDeviceDto(@JsonUnwrapped LightDeviceDto device, Date expirationDate) {
	}

	/** Q5: explicit subtype names, to reproduce today's discriminator values. */
	@JsonView(DefaultView.class)
	@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
	@JsonSubTypes({
		@JsonSubTypes.Type(value = SnapshotDto.class, name = "TakeSnapshotTask"),
		@JsonSubTypes.Type(value = ScriptDto.class, name = "RunScriptTask"),
	})
	public sealed interface NamedTaskDto permits SnapshotDto, ScriptDto {
	}

	/** Q5: first named subtype. */
	@JsonView(DefaultView.class)
	public record SnapshotDto(long id, String comments) implements NamedTaskDto {
	}

	/** Q5: second named subtype. */
	@JsonView(DefaultView.class)
	public record ScriptDto(long id, String script) implements NamedTaskDto {
	}

	private static final Device.Status ST = Device.Status.INPRODUCTION;
	private static final Date DATE = new Date(1700000000000L);

	private static RsLightExemptedDevice legacyExempted() {
		return new RsLightExemptedDevice(1, "r1", "Cisco", "1.2.3.4", ST, "CiscoIOS12", false, null, true,
			SoftwareRule.ConformanceLevel.UNKNOWN, Device.NetworkClass.UNKNOWN, DATE);
	}

	private static ExemptedDeviceDto dtoExempted() {
		return new ExemptedDeviceDto(new LightDeviceDto(1, "r1", "Cisco", "1.2.3.4", ST, "CiscoIOS12", false, null, true,
			SoftwareRule.ConformanceLevel.UNKNOWN, Device.NetworkClass.UNKNOWN), DATE);
	}

	// ------------------------------------------------------------------
	// Q1
	// ------------------------------------------------------------------

	@Test
	void q1PlainRecordIsEmptyUnderRestApiView() throws Exception {
		PlainDto dto = new PlainDto(1, "r1", ST, false);
		// Whether or not the resource method carries @JsonView(RestApiView): all components dropped
		for (String m : new String[] { "plain", "plainNoView" }) {
			assertEquals("{}", json(m, dto));
			assertEquals("<PlainDto/>", xml(m, dto));
			assertEquals("--- {}", yaml(m, dto));
		}
	}

	// ------------------------------------------------------------------
	// Q2
	// ------------------------------------------------------------------

	@Test
	void q2ClassLevelViewSerializesAllComponents() throws Exception {
		String expectedJson = "{\"id\":1,\"name\":\"r1\",\"status\":\"INPRODUCTION\",\"eol\":false}";
		String expectedYaml = "---\nid: 1\nname: \"r1\"\nstatus: \"INPRODUCTION\"\neol: false";
		ClassLevelDefaultDto a = new ClassLevelDefaultDto(1, "r1", ST, false);
		assertEquals(expectedJson, json("classDefault", a));
		assertEquals("<ClassLevelDefaultDto><id>1</id><name>r1</name><status>INPRODUCTION</status><eol>false</eol></ClassLevelDefaultDto>",
			xml("classDefault", a));
		assertEquals(expectedYaml, yaml("classDefault", a));
		// RestApiView (the active view) as class-level view works as well
		ClassLevelRestApiDto b = new ClassLevelRestApiDto(1, "r1", ST, false);
		assertEquals(expectedJson, json("classDefault", b));
		assertEquals(expectedYaml, yaml("classDefault", b));
	}

	@Test
	void q2ComponentLevelViewsAreHonoured() throws Exception {
		// Only the components annotated with a view compatible with RestApiView are written
		ComponentViewDto dto = new ComponentViewDto(1, "r1", ST, false);
		assertEquals("{\"id\":1,\"status\":\"INPRODUCTION\"}", json("componentView", dto));
		assertEquals("<ComponentViewDto><id>1</id><status>INPRODUCTION</status></ComponentViewDto>", xml("componentView", dto));
		assertEquals("---\nid: 1\nstatus: \"INPRODUCTION\"", yaml("componentView", dto));
	}

	// ------------------------------------------------------------------
	// Q3
	// ------------------------------------------------------------------

	@Test
	void q3UnwrappedCompositionIsFlat() throws Exception {
		ClassLevelDefaultDto inner = new ClassLevelDefaultDto(1, "r1", ST, false);
		String j = json("composed", new ComposedDto(inner, DATE));
		assertEquals("{\"id\":1,\"name\":\"r1\",\"status\":\"INPRODUCTION\",\"eol\":false,\"expirationDate\":1700000000000}", j);
		assertEquals("<ComposedDto><id>1</id><name>r1</name><status>INPRODUCTION</status><eol>false</eol>"
			+ "<expirationDate>1700000000000</expirationDate></ComposedDto>", xml("composed", new ComposedDto(inner, DATE)));
		assertEquals("---\nid: 1\nname: \"r1\"\nstatus: \"INPRODUCTION\"\neol: false\nexpirationDate: 1700000000000",
			yaml("composed", new ComposedDto(inner, DATE)));
	}

	@Test
	void q3OuterRecordNeedsItsOwnView() throws Exception {
		// The class-level view of the outer record is mandatory: @JsonUnwrapped does not inherit it from the inner one
		ComposedNoViewDto dto = new ComposedNoViewDto(new ClassLevelDefaultDto(1, "r1", ST, false), DATE);
		assertEquals("{}", json("composed", dto));
		assertEquals("<ComposedNoViewDto/>", xml("composed", dto));
	}

	@Test
	void q3CompositionEqualsLegacySubclass() throws Exception {
		RsLightExemptedDevice legacy = legacyExempted();
		ExemptedDeviceDto dto = dtoExempted();
		// JSON and YAML: byte-for-byte identical (same field order, nulls written, dates as epoch millis)
		assertEquals(json("composed", legacy), json("composed", dto));
		assertEquals(yaml("composed", legacy), yaml("composed", dto));
		assertTrue(json("composed", dto).contains("\"eos\":null,\"configCompliant\":true"));
		assertTrue(json("composed", dto).endsWith("\"expirationDate\":1700000000000}"));
		// XML: identical except the root element, which is the (simple) class name
		String legacyXml = xml("composed", legacy);
		String dtoXml = xml("composed", dto);
		assertTrue(legacyXml.startsWith("<RsLightExemptedDevice><id>1</id>"));
		assertTrue(dtoXml.startsWith("<ExemptedDeviceDto><id>1</id>"));
		assertEquals(legacyXml.replace("RsLightExemptedDevice", "X"), dtoXml.replace("ExemptedDeviceDto", "X"));
		// null Boolean is written as an empty element in both
		assertTrue(dtoXml.contains("<eos/>"));
	}

	// ------------------------------------------------------------------
	// Q4
	// ------------------------------------------------------------------

	@Test
	void q4XmlRootNames() throws Exception {
		// Default root name: simple class name, for a record as for a class (a nested record is not prefixed by its outer class)
		assertTrue(xml("composed", new JaxbDefaultRootDto(1, "r1")).startsWith("<JaxbDefaultRootDto>"));
		// @XmlRootElement(name=...) is IGNORED under the application's providers (also for existing classes), because
		// setMapper(...) replaces the mapper that the provider would have configured with the Jakarta XmlBind introspector
		assertEquals("<JaxbRootDto><id>1</id><name>r1</name></JaxbRootDto>", xml("composed", new JaxbRootDto(1, "r1")));
		// @JacksonXmlRootElement is honoured
		assertEquals("<rootNamedDto><id>1</id><name>r1</name></rootNamedDto>", xml("composed", new RootNamedDto(1, "r1")));
		// ... whereas a provider left with its default mapper would honour @XmlRootElement
		JacksonXmlBindXMLProvider defaultProvider = new JacksonXmlBindXMLProvider();
		defaultProvider.setDefaultView(RestApiView.class);
		Method m = method("composed");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		defaultProvider.writeTo(new JaxbRootDto(1, "r1"), JaxbRootDto.class, JaxbRootDto.class, m.getAnnotations(),
			MediaType.APPLICATION_XML_TYPE, null, out);
		assertEquals("<jaxbRoot><id>1</id><name>r1</name></jaxbRoot>", out.toString(StandardCharsets.UTF_8).trim());
	}

	@Test
	void q4ListAndSingleRootsAreComparableToLegacy() throws Exception {
		// Single object
		LightDeviceDto light = new LightDeviceDto(1, "r1", "Cisco", "1.2.3.4", ST, "CiscoIOS12", false, null, true,
			SoftwareRule.ConformanceLevel.UNKNOWN, Device.NetworkClass.UNKNOWN);
		RsLightDevice legacy = new RsLightDevice(1, "r1", "Cisco", "1.2.3.4", ST, "CiscoIOS12", false, null, true,
			SoftwareRule.ConformanceLevel.UNKNOWN, Device.NetworkClass.UNKNOWN);
		assertEquals(xml("composed", legacy).replace("RsLightDevice", "X"), xml("composed", light).replace("LightDeviceDto", "X"));
		// List: root is "List" (generic return type declared on the method), item elements are <item>, for classes as for records
		String legacyList = xml("legacyList", new ArrayList<>(List.of(legacy)));
		assertTrue(legacyList.startsWith("<List><item><id>1</id>"), legacyList);
		String dtoList = xml("dtoList", new ArrayList<>(List.of(new ClassLevelDefaultDto(1, "r1", ST, false))));
		assertEquals("<List><item><id>1</id><name>r1</name><status>INPRODUCTION</status><eol>false</eol></item></List>", dtoList);
		assertEquals("[{\"id\":1,\"name\":\"r1\",\"status\":\"INPRODUCTION\",\"eol\":false}]",
			json("dtoList", new ArrayList<>(List.of(new ClassLevelDefaultDto(1, "r1", ST, false)))));
		// An entity list has the same <List>/<item> structure; the single entity root is its simple class name
		Device dev = newDevice();
		assertTrue(xml("annotatedSingle", dev).startsWith("<Device>"));
		assertTrue(xml("deviceList", new ArrayList<>(List.of(dev))).startsWith("<List><item>"));
	}

	// ------------------------------------------------------------------
	// Q5
	// ------------------------------------------------------------------

	@Test
	void q5SimpleNameUsesTheRecordNames() throws Exception {
		List<TaskDto> list = new ArrayList<>(List.of(new TakeSnapshotTaskDto(1, "c"), new RunScriptTaskDto(2, "s")));
		// Id.SIMPLE_NAME works on a sealed interface + records, but yields the record simple names
		assertEquals("[{\"type\":\"TakeSnapshotTaskDto\",\"id\":1,\"comments\":\"c\"},{\"type\":\"RunScriptTaskDto\",\"id\":2,\"script\":\"s\"}]",
			json("polymorphic", list));
		// ... and the "type" property is written first
		assertEquals("<List><item type=\"TakeSnapshotTaskDto\"><id>1</id><comments>c</comments></item>"
			+ "<item type=\"RunScriptTaskDto\"><id>2</id><script>s</script></item></List>", xml("polymorphic", list));
		assertEquals("---\n- !<TakeSnapshotTaskDto>\n  id: 1\n  comments: \"c\"\n- !<RunScriptTaskDto>\n  id: 2\n  script: \"s\"",
			yaml("polymorphic", list));
	}

	@Test
	void q5ExplicitNamesReproduceLegacyDiscriminators() throws Exception {
		List<NamedTaskDto> list = new ArrayList<>(List.of(new SnapshotDto(1, "c"), new ScriptDto(2, "s")));
		assertEquals("[{\"type\":\"TakeSnapshotTask\",\"id\":1,\"comments\":\"c\"},{\"type\":\"RunScriptTask\",\"id\":2,\"script\":\"s\"}]",
			json("namedPolymorphic", list));
		assertTrue(xml("namedPolymorphic", list).contains("<item type=\"TakeSnapshotTask\"><id>1</id>"));
		assertTrue(yaml("namedPolymorphic", list).contains("- !<TakeSnapshotTask>\n  id: 1"));
		// Compare with the legacy entity (Rule, Id.SIMPLE_NAME on a class hierarchy)
		TextRule task = new TextRule("r", null);
		String legacyJson = json("annotatedSingle", task);
		assertTrue(legacyJson.startsWith("{\"type\":\"TextRule\","), legacyJson);
		assertTrue(xml("annotatedSingle", task).startsWith("<TextRule type=\"TextRule\">"), xml("annotatedSingle", task));
		assertTrue(yaml("annotatedSingle", task).startsWith("--- !<TextRule>\n"), yaml("annotatedSingle", task));
		// Same shape as the DTO output: type attribute / YAML tag (single object)
		assertTrue(yaml("namedSingle", new SnapshotDto(1, "c")).startsWith("--- !<TakeSnapshotTask>\n"));
		assertEquals("{\"type\":\"TakeSnapshotTask\",\"id\":1,\"comments\":\"c\"}", json("namedSingle", new SnapshotDto(1, "c")));
		assertTrue(xml("namedSingle", new SnapshotDto(1, "c")).startsWith("<SnapshotDto type=\"TakeSnapshotTask\">"));
		// Gotcha: a list whose declared element type is not the polymorphic base (List<Object>) loses the type id
		assertEquals("[{\"id\":1,\"comments\":\"c\"}]", json("untypedList", new ArrayList<>(List.of(new SnapshotDto(1, "c")))));
	}

	// ------------------------------------------------------------------
	// Q6
	// ------------------------------------------------------------------

	@Test
	void q6DatesAreEpochMillisLikeEntities() throws Exception {
		assertTrue(json("composed", dtoExempted()).contains("\"expirationDate\":1700000000000"));
		assertTrue(xml("composed", dtoExempted()).contains("<expirationDate>1700000000000</expirationDate>"));
		assertTrue(yaml("composed", dtoExempted()).contains("expirationDate: 1700000000000"));
		// Entity Date field: also epoch millis (not ISO-8601)
		Device dev = newDevice();
		Matcher matcher = Pattern.compile("\"createdDate\":(\\d{13})").matcher(json("annotatedSingle", dev));
		assertTrue(matcher.find());
		assertTrue(xml("annotatedSingle", dev).matches(".*<createdDate>\\d{13}</createdDate>.*"));
	}

	// ------------------------------------------------------------------
	// Q7
	// ------------------------------------------------------------------

	@Test
	void q7SwaggerSchemasOfRecords() throws Exception {
		// Without a view: records are plain object schemas named after the simple class name
		Map<String, Schema> plain = ModelConverters.getInstance().readAll(PlainDto.class);
		assertEquals(Set.of("PlainDto"), plain.keySet());
		assertEquals(List.of("id", "name", "status", "eol"), new ArrayList<>(plain.get("PlainDto").getProperties().keySet()));
		// @JsonUnwrapped is flattened in the schema (the outer schema holds the inner properties, no "device" property)
		Map<String, Schema> composed = ModelConverters.getInstance().readAll(ComposedDto.class);
		assertEquals(List.of("id", "name", "status", "eol", "expirationDate"),
			new ArrayList<>(composed.get("ComposedDto").getProperties().keySet()));
		assertEquals("date-time", ((Schema<?>) composed.get("ComposedDto").getProperties().get("expirationDate")).getFormat());
		// Polymorphism: parent schema with discriminator, children use allOf
		Map<String, Schema> poly = ModelConverters.getInstance().readAll(TaskDto.class);
		assertEquals(Set.of("TaskDto", "TakeSnapshotTaskDto", "RunScriptTaskDto"), poly.keySet());
		assertEquals("type", poly.get("TaskDto").getDiscriminator().getPropertyName());
		assertNotNull(poly.get("TakeSnapshotTaskDto").getAllOf());
		// Legacy subclass equivalent: a single flat schema
		Map<String, Schema> legacy = ModelConverters.getInstance().readAll(RsLightExemptedDevice.class);
		assertEquals(Set.of("RsLightExemptedDevice"), legacy.keySet());
		assertEquals(12, legacy.get("RsLightExemptedDevice").getProperties().size());
		Map<String, Schema> dto = ModelConverters.getInstance().readAll(ExemptedDeviceDto.class);
		assertEquals(new ArrayList<>(legacy.get("RsLightExemptedDevice").getProperties().keySet()),
			new ArrayList<>(dto.get("ExemptedDeviceDto").getProperties().keySet()));
	}

	@Test
	void q7SwaggerHonoursJsonViewOnResourceMethods() throws Exception {
		OpenAPI api = new Reader(new SwaggerConfiguration()).read(Set.of(ProbeResource.class));
		Map<String, Schema> schemas = api.getComponents().getSchemas();
		// A @JsonView(RestApiView) method renames the component with a "_RestApiView" suffix (this is the case today
		// for RsLightDevice etc.), and a method without view uses the plain name
		assertTrue(schemas.containsKey("PlainDto_RestApiView"));
		assertTrue(schemas.containsKey("PlainDto"));
		assertTrue(schemas.containsKey("ClassLevelDefaultDto_RestApiView"));
		assertTrue(schemas.containsKey("RsLightDevice_RestApiView"));
		assertTrue(schemas.containsKey("TaskDto_RestApiView"));
		assertTrue(schemas.containsKey("TakeSnapshotTaskDto_RestApiView"));
		assertEquals("#/components/schemas/PlainDto_RestApiView",
			api.getPaths().get("/probe/plain").getGet().getResponses().get("default").getContent().get("application/json").getSchema().get$ref());
		// ... but the view is NOT used to filter properties as the runtime does: the un-annotated PlainDto (empty JSON
		// at runtime) is documented with all its properties, and un-viewed components are listed too
		assertEquals(4, schemas.get("PlainDto_RestApiView").getProperties().size());
		assertEquals(4, schemas.get("ComponentViewDto_RestApiView").getProperties().size());
		// @JsonUnwrapped stays flat when a view is applied
		assertEquals(5, schemas.get("ComposedDto_RestApiView").getProperties().size());
	}

	private static Device newDevice() throws Exception {
		Constructor<Device> ctor = Device.class.getDeclaredConstructor();
		ctor.setAccessible(true);
		Device dev = ctor.newInstance();
		dev.setName("x");
		return dev;
	}
}
