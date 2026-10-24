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

import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import net.netshot.netshot.testing.UnitTest;

/**
 * Architecture rules encoding the TARGET layering (see the layering refactor plan).
 *
 * <p>Most rules are strict. The rule which still has violations to remove (cycles between feature slices) is
 * frozen with FreezingArchRule: violations existing when the store was generated are recorded in
 * src/test/resources/archunit_store and tolerated; only NEW violations fail the build.</p>
 *
 * <p>Regenerating the store after a planned package move (violation descriptions contain class names, so a
 * rename makes old entries stale and the moved ones "new"): delete src/test/resources/archunit_store and run
 * "./mvnw test -Dtest=ArchitectureTest" once (the store is recreated since allowStoreCreation=true), then
 * review the diff of the store: it must only shrink or follow renames. Alternatively refreeze existing rules
 * (rewrites the stored violations of rules that already have a store file) with
 * "./mvnw test -Dtest=ArchitectureTest -DargLine=-Darchunit.freeze.refreeze=true".</p>
 */
@UnitTest
@AnalyzeClasses(packages = "net.netshot.netshot", importOptions = ImportOption.DoNotIncludeTests.class)
public final class ArchitectureTest {

	private static final String ROOT = "net.netshot.netshot.";

	/** Feature packages of the target architecture. */
	private static final String[] FEATURES = {
		ROOT + "api..", ROOT + "inventory..", ROOT + "driver..", ROOT + "access..", ROOT + "credentials..",
		ROOT + "vault..", ROOT + "devicescript..", ROOT + "userscript..", ROOT + "compliance..",
		ROOT + "diagnostic..", ROOT + "task..", ROOT + "report..", ROOT + "hook..", ROOT + "aaa..",
		ROOT + "boot..", ROOT + "tools..", ROOT + "cluster..",
	};

	/** Packages allowed to use the JAX-RS client API (jakarta.ws.rs.client..): outgoing HTTP calls. */
	private static final String[] CLIENT_USERS = {
		ROOT + "hook..", ROOT + "vault..", ROOT + "access..", ROOT + "api..",
	};

	// ---------------------------------------------------------------------
	// Rules on package names
	// ---------------------------------------------------------------------

	@ArchTest
	static final ArchRule PLATFORM_DOES_NOT_DEPEND_ON_FEATURES = 
		noClasses().that().resideInAPackage(ROOT + "platform..")
			.should().dependOnClassesThat().resideInAnyPackage(FEATURES)
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule ONLY_BOOT_DEPENDS_ON_API = 
		noClasses().that().resideOutsideOfPackages(ROOT + "boot..", ROOT + "api..")
			.and().doNotHaveFullyQualifiedName(ROOT + "Netshot")
			.should().dependOnClassesThat().resideInAPackage(ROOT + "api..")
			.allowEmptyShould(true);

	/** The composition root hands the services to the API channels, which therefore never reach back to it. */
	@ArchTest
	static final ArchRule API_DOES_NOT_DEPEND_ON_BOOT = 
		noClasses().that().resideInAPackage(ROOT + "api..")
			.should().dependOnClassesThat().resideInAPackage(ROOT + "boot..")
			.allowEmptyShould(true);

	/** API channels are independent: each owns its DTOs, and they only share services and entities. */
	@ArchTest
	static final ArchRule REST_CHANNEL_DOES_NOT_DEPEND_ON_HOOK_CHANNEL = 
		noClasses().that().resideInAPackage(ROOT + "api.rest..")
			.should().dependOnClassesThat().resideInAPackage(ROOT + "api.hook..")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule HOOK_CHANNEL_DOES_NOT_DEPEND_ON_REST_CHANNEL = 
		noClasses().that().resideInAPackage(ROOT + "api.hook..")
			.should().dependOnClassesThat().resideInAPackage(ROOT + "api.rest..")
			.allowEmptyShould(true);

	/** The API channels call the services: they never reach the database (only the pagination window crosses). */
	@ArchTest
	static final ArchRule API_DOES_NOT_USE_THE_DATABASE = 
		noClasses().that().resideInAPackage(ROOT + "api..")
			.should().dependOnClassesThat(
				DescribedPredicate.or(
					JavaClass.Predicates.resideInAPackage("org.jooq.."),
					DescribedPredicate.and(
						JavaClass.Predicates.resideInAPackage(ROOT + "platform.database.."),
						DescribedPredicate.not(JavaClass.Predicates.equivalentTo(net.netshot.netshot.platform.database.Page.class)))))
			.allowEmptyShould(true);

	// ---------------------------------------------------------------------
	// Rules independent of package names (entities, JAX-RS, cycles)
	// ---------------------------------------------------------------------

	/** The serialization views and the XML bindings belong to the API channels and to the cluster wire format. */
	@ArchTest
	static final ArchRule SERIALIZATION_ANNOTATIONS_ONLY_IN_CHANNELS = 
		noClasses().that().resideOutsideOfPackages(ROOT + "api..", ROOT + "cluster..", ROOT + "platform.rest..")
			.should().dependOnClassesThat().haveFullyQualifiedName("com.fasterxml.jackson.annotation.JsonView")
			.orShould().dependOnClassesThat().resideInAPackage("jakarta.xml.bind.annotation..")
			.allowEmptyShould(true);

	/**
	 * Server-side JAX-RS (annotations such as @Path and @GET, the container filters, the ext providers...) stays in the
	 * REST layer. What the outgoing calls use is allowed everywhere the next rule allows it: the client API, the
	 * {@code core} types (Response, MediaType, Form...) and the ProcessingException the client throws.
	 */
	@ArchTest
	static final ArchRule JAX_RS_SERVER_API_ONLY_IN_REST_LAYER = 
		noClasses().that().resideOutsideOfPackages(ROOT + "api..", ROOT + "platform.rest..")
			.should().dependOnClassesThat(
				DescribedPredicate.and(
					JavaClass.Predicates.resideInAPackage("jakarta.ws.rs.."),
					DescribedPredicate.not(JavaClass.Predicates.resideInAPackage("jakarta.ws.rs.client..")),
					DescribedPredicate.not(JavaClass.Predicates.resideInAPackage("jakarta.ws.rs.core..")),
					DescribedPredicate.not(JavaClass.Predicates.equivalentTo(jakarta.ws.rs.ProcessingException.class))))
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule JAX_RS_CLIENT_API_ONLY_FOR_OUTGOING_CALLS = 
		noClasses().that().resideOutsideOfPackages(CLIENT_USERS)
			.should().dependOnClassesThat(
				DescribedPredicate.or(
					JavaClass.Predicates.resideInAPackage("jakarta.ws.rs.client.."),
					JavaClass.Predicates.resideInAPackage("jakarta.ws.rs.core.."),
					JavaClass.Predicates.equivalentTo(jakarta.ws.rs.ProcessingException.class)))
			.allowEmptyShould(true);

	/**
	 * No cycles between top-level slices (net.netshot.netshot.(*)..). ArchUnit's own slices().beFreeOfCycles()
	 * reports an arbitrary subset of cycles which varies between runs, which makes it unusable when frozen; this
	 * rule instead reports each class-to-class dependency crossing two slices that are part of the same cycle.
	 */
	@ArchTest
	static final ArchRule NO_CYCLES_BETWEEN_FEATURE_SLICES = freeze(
		classes().should(new SliceCycleCondition()).allowEmptyShould(true));

	private static String sliceOf(JavaClass javaClass) {
		String name = javaClass.getPackageName();
		if (!name.startsWith(ROOT)) {
			return null;
		}
		String rest = name.substring(ROOT.length());
		int dot = rest.indexOf('.');
		return dot < 0 ? rest : rest.substring(0, dot);
	}

	private static class SliceCycleCondition extends ArchCondition<JavaClass> {
		private final Map<String, Set<String>> reachable = new HashMap<>();

		SliceCycleCondition() {
			super("not depend on a class of another top-level slice involved in a dependency cycle with its own slice");
		}

		@Override
		public void init(Collection<JavaClass> allObjects) {
			reachable.clear();
			for (JavaClass c : allObjects) {
				String from = sliceOf(c);
				if (from == null) {
					continue;
				}
				for (Dependency d : c.getDirectDependenciesFromSelf()) {
					String to = sliceOf(d.getTargetClass());
					if (to != null && !to.equals(from)) {
						reachable.computeIfAbsent(from, _ -> new HashSet<>()).add(to);
					}
				}
			}
			// Transitive closure (few slices)
			boolean changed = true;
			while (changed) {
				changed = false;
				for (Set<String> targets : reachable.values()) {
					for (String t : new ArrayList<>(targets)) {
						if (targets.addAll(reachable.getOrDefault(t, Set.of()))) {
							changed = true;
						}
					}
				}
			}
		}

		@Override
		public void check(JavaClass item, ConditionEvents events) {
			String from = sliceOf(item);
			if (from == null) {
				return;
			}
			Set<String> seen = new HashSet<>();
			for (Dependency d : item.getDirectDependenciesFromSelf()) {
				String to = sliceOf(d.getTargetClass());
				if (to == null || to.equals(from) || !reachable.getOrDefault(to, Set.of()).contains(from)) {
					continue;
				}
				if (seen.add(d.getTargetClass().getName())) {
					events.add(SimpleConditionEvent.violated(item, String.format(
						"Class <%s> (slice %s) depends on <%s> (slice %s) which is part of a dependency cycle",
						item.getName(), from, d.getTargetClass().getName(), to)));
				}
			}
		}
	}

	private ArchitectureTest() {
	}
}
