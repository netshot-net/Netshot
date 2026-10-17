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

/**
 * Architecture rules encoding the TARGET layering (see the layering refactor plan).
 *
 * <p>Most rules are strict. The three rules which still have violations to remove with the planned ORM change
 * (entities using the Session or Database, cycles between feature slices) are frozen with FreezingArchRule:
 * violations existing when the store was generated are recorded in src/test/resources/archunit_store and
 * tolerated; only NEW violations fail the build.</p>
 *
 * <p>Regenerating the store after a planned package move (violation descriptions contain class names, so a
 * rename makes old entries stale and the moved ones "new"): delete src/test/resources/archunit_store and run
 * "./mvnw test -Dtest=ArchitectureTest" once (the store is recreated since allowStoreCreation=true), then
 * review the diff of the store: it must only shrink or follow renames. Alternatively refreeze existing rules
 * (rewrites the stored violations of rules that already have a store file) with
 * "./mvnw test -Dtest=ArchitectureTest -DargLine=-Darchunit.freeze.refreeze=true".</p>
 */
@AnalyzeClasses(packages = "net.netshot.netshot", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

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
	static final ArchRule platformDoesNotDependOnFeatures = 
		noClasses().that().resideInAPackage(ROOT + "platform..")
			.should().dependOnClassesThat().resideInAnyPackage(FEATURES)
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule onlyBootDependsOnApi = 
		noClasses().that().resideOutsideOfPackages(ROOT + "boot..", ROOT + "api..")
			.and().doNotHaveFullyQualifiedName(ROOT + "Netshot")
			.should().dependOnClassesThat().resideInAPackage(ROOT + "api..")
			.allowEmptyShould(true);

	/** The composition root hands the services to the API channels, which therefore never reach back to it. */
	@ArchTest
	static final ArchRule apiDoesNotDependOnBoot = 
		noClasses().that().resideInAPackage(ROOT + "api..")
			.should().dependOnClassesThat().resideInAPackage(ROOT + "boot..")
			.allowEmptyShould(true);

	/** API channels are independent: each owns its DTOs, and they only share services and entities. */
	@ArchTest
	static final ArchRule restChannelDoesNotDependOnHookChannel = 
		noClasses().that().resideInAPackage(ROOT + "api.rest..")
			.should().dependOnClassesThat().resideInAPackage(ROOT + "api.hook..")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule hookChannelDoesNotDependOnRestChannel = 
		noClasses().that().resideInAPackage(ROOT + "api.hook..")
			.should().dependOnClassesThat().resideInAPackage(ROOT + "api.rest..")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule apiDoesNotUseSession = 
		noClasses().that().resideInAPackage(ROOT + "api..")
			.should().dependOnClassesThat().haveFullyQualifiedName("org.hibernate.Session")
			.allowEmptyShould(true);

	// ---------------------------------------------------------------------
	// Rules independent of package names (entities, JAX-RS, cycles)
	// ---------------------------------------------------------------------

	@ArchTest
	static final ArchRule entitiesDoNotUseSession = freeze(
		noClasses().that().areAnnotatedWith("jakarta.persistence.Entity")
			.should().dependOnClassesThat().haveFullyQualifiedName("org.hibernate.Session")
			.orShould().dependOnClassesThat().haveFullyQualifiedName("jakarta.persistence.EntityManager")
			.allowEmptyShould(true));

	@ArchTest
	static final ArchRule entitiesDoNotUseJsonView = 
		noClasses().that().areAnnotatedWith("jakarta.persistence.Entity")
			.or().areAnnotatedWith("jakarta.persistence.MappedSuperclass")
			.or().areAnnotatedWith("jakarta.persistence.Embeddable")
			.should().dependOnClassesThat().haveFullyQualifiedName("com.fasterxml.jackson.annotation.JsonView")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule entitiesDoNotUseJaxb = 
		noClasses().that().areAnnotatedWith("jakarta.persistence.Entity")
			.or().areAnnotatedWith("jakarta.persistence.MappedSuperclass")
			.or().areAnnotatedWith("jakarta.persistence.Embeddable")
			.should().dependOnClassesThat().resideInAPackage("jakarta.xml.bind.annotation..")
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule entitiesDoNotUseDatabase = freeze(
		noClasses().that().areAnnotatedWith("jakarta.persistence.Entity")
			.should().dependOnClassesThat().haveFullyQualifiedName(ROOT + "platform.database.Database")
			.allowEmptyShould(true));

	/**
	 * Server-side JAX-RS (annotations such as @Path and @GET, the container filters, the ext providers...) stays in the
	 * REST layer. What the outgoing calls use is allowed everywhere the next rule allows it: the client API, the
	 * {@code core} types (Response, MediaType, Form...) and the ProcessingException the client throws.
	 */
	@ArchTest
	static final ArchRule jaxRsServerApiOnlyInRestLayer = 
		noClasses().that().resideOutsideOfPackages(ROOT + "api..", ROOT + "platform.rest..")
			.should().dependOnClassesThat(
				DescribedPredicate.and(
					JavaClass.Predicates.resideInAPackage("jakarta.ws.rs.."),
					DescribedPredicate.not(JavaClass.Predicates.resideInAPackage("jakarta.ws.rs.client..")),
					DescribedPredicate.not(JavaClass.Predicates.resideInAPackage("jakarta.ws.rs.core..")),
					DescribedPredicate.not(JavaClass.Predicates.equivalentTo(jakarta.ws.rs.ProcessingException.class))))
			.allowEmptyShould(true);

	@ArchTest
	static final ArchRule jaxRsClientApiOnlyForOutgoingCalls = 
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
	static final ArchRule noCyclesBetweenFeatureSlices = freeze(
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
						reachable.computeIfAbsent(from, k -> new HashSet<>()).add(to);
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
}
