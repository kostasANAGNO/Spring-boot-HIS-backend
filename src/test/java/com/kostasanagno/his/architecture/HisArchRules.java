package com.kostasanagno.his.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.kostasanagno.his.audit.Audited;
import com.kostasanagno.his.entity.IdentifiedEntity;
import com.kostasanagno.his.mapper.CentralMapperConfig;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToOne;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The architecture rules of this project (see docs/CONVENTIONS.md). Each one is a build failure, not a suggestion.
 * <p>
 * They run against the real code in {@link ArchitectureTest} and against deliberately wrong sample classes in
 * {@link ArchitectureRulesBiteTest}, so a rule that silently matches nothing is noticed.
 * <p>
 * Package patterns are deliberately generic ({@code ..controller..}) so the same rule applies to the samples.
 */
public final class HisArchRules {

    /**
     * Controllers that do NOT touch patient-related data and are therefore not audited. A NEW controller must either
     * carry {@code @Audited} on its endpoints or be added here on purpose - that is the point of the list.
     */
    static final Set<String> CONTROLLERS_WITHOUT_PATIENT_DATA = Set.of(
            "AuthController",        // login is audited by AuthenticationAuditListener, register carries @Audited itself
            "DoctorController", "SpecialtyController", "ClinicController", "WardController", "RoomController",
            "BedController", "ChargeItemController", "MedicationController");

    private HisArchRules() {
    }

    // ---- layers and dependency directions ------------------------------------------------------

    @ArchTest
    public static final ArchRule CONTROLLERS_USE_SERVICE_INTERFACES_ONLY = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAnyPackage("..repository..", "..service.impl..")
            .because("controllers are thin: they depend on service interfaces, never on repositories or implementations");

    @ArchTest
    public static final ArchRule SERVICE_INTERFACES_DO_NOT_KNOW_THEIR_IMPLEMENTATIONS = noClasses()
            .that().resideInAPackage("..service")
            .should().dependOnClassesThat().resideInAPackage("..service.impl..");

    @ArchTest
    public static final ArchRule SERVICES_DO_NOT_KNOW_CONTROLLERS = noClasses()
            .that().resideInAPackage("..service..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    public static final ArchRule REPOSITORIES_DO_NOT_KNOW_UPPER_LAYERS = noClasses()
            .that().resideInAPackage("..repository..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..", "..mapper..");

    @ArchTest
    public static final ArchRule DTOS_DO_NOT_KNOW_UPPER_LAYERS = noClasses()
            .that().resideInAPackage("..dto..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..repository..", "..controller..", "..mapper..");

    @ArchTest
    public static final ArchRule MAPPERS_ONLY_MAP = noClasses()
            .that().resideInAPackage("..mapper..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..repository..", "..controller..");

    @ArchTest
    public static final ArchRule EXCEPTIONS_DO_NOT_KNOW_UPPER_LAYERS = noClasses()
            .that().resideInAPackage("..exception..")
            .should().dependOnClassesThat().resideInAnyPackage("..service..", "..repository..", "..controller..");

    @ArchTest
    public static final ArchRule ENTITIES_ARE_PLAIN_PERSISTENCE_CLASSES = classes()
            .that().resideInAPackage("..entity..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "..entity..", "java..", "jakarta..", "org.hibernate..", "org.springframework.data..", "lombok..")
            .because("entities know nothing about DTOs, services, security or the web");

    @ArchTest
    public static final ArchRule TRANSACTIONS_ARE_DEMARCATED_IN_SERVICES_ONLY = noClasses()
            .that().resideInAPackage("..controller..")
            .should().beAnnotatedWith(Transactional.class)
            .orShould().dependOnClassesThat().areAssignableTo(Transactional.class)
            .because("the transaction boundary is the service method");

    // ---- what controllers expose and who may call it -------------------------------------------

    @ArchTest
    public static final ArchRule CONTROLLERS_DO_NOT_EXPOSE_ENTITIES = methods()
            .that().arePublic().and().areDeclaredInClassesThat().resideInAPackage("..controller..")
            .should(notTakeOrReturnEntities())
            .because("responses are DTOs; an entity would leak columns and lazy associations");

    @ArchTest
    public static final ArchRule EVERY_ENDPOINT_IS_AUTHORIZED = methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
            .and(areEndpoints()).and(areNotThePublicLogin())
            .should().beAnnotatedWith(PreAuthorize.class)
            .because("everything under /api needs an explicit role rule; only login is public");

    @ArchTest
    public static final ArchRule ENDPOINTS_ON_PATIENT_DATA_ARE_AUDITED = methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
            .and(areDeclaredInAControllerWithPatientData()).and(areEndpoints()).and(areNotThePublicLogin())
            .should().beAnnotatedWith(Audited.class)
            .because("who read or changed which patient's data must be traceable");

    @ArchTest
    public static final ArchRule CONTROLLERS_LIVE_UNDER_API = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().haveSimpleNameEndingWith("Controller")
            .andShould().resideInAPackage("..controller..")
            .andShould(beMappedUnderApi());

    // ---- injection and state -------------------------------------------------------------------

    @ArchTest
    public static final ArchRule NO_FIELD_INJECTION = noFields()
            .that().areDeclaredInClassesThat().resideOutsideOfPackage("..mapper..")   // MapStruct's generated code
            .should().beAnnotatedWith(Autowired.class)
            .orShould().beAnnotatedWith(Value.class)
            .because("constructor injection only (@RequiredArgsConstructor); externalised values go in a @ConfigurationProperties record");

    @ArchTest
    public static final ArchRule BEANS_ARE_STATELESS = fields()
            .that().areNotStatic()
            .and().areDeclaredInClassesThat(areHandWrittenSpringBeans())
            .should().beFinal()
            .because("singleton beans must not hold mutable state");

    // ---- services, data and mappers ------------------------------------------------------------

    @ArchTest
    public static final ArchRule SERVICE_IMPLEMENTATIONS_ARE_TRANSACTIONAL_AND_HAVE_AN_INTERFACE = classes()
            .that().resideInAPackage("..service.impl..").and().areAnnotatedWith(Service.class)
            .should().beAnnotatedWith(Transactional.class)
            .andShould(implementAnInterfaceFromTheServicePackage());

    @ArchTest
    public static final ArchRule DTOS_ARE_RECORDS = classes()
            .that().resideInAPackage("..dto..")
            .should().beAssignableTo(Record.class);

    @ArchTest
    public static final ArchRule ENTITIES_HAVE_SAFE_EQUALS_AND_HASHCODE = classes()
            .that().areAnnotatedWith(Entity.class)
            .should().beAssignableTo(IdentifiedEntity.class)
            .because("IdentifiedEntity provides the id based, Hibernate-proxy safe equals/hashCode");

    @ArchTest
    public static final ArchRule TO_ONE_ASSOCIATIONS_ARE_LAZY = fields()
            .that().areAnnotatedWith(ManyToOne.class).or().areAnnotatedWith(OneToOne.class)
            .should(beFetchedLazily())
            .because("eager to-one associations cause hidden N+1 queries; fetch with @EntityGraph where needed");

    @ArchTest
    public static final ArchRule MAPPERS_USE_THE_CENTRAL_CONFIGURATION = classes()
            .that().resideInAPackage("..mapper..").and().areInterfaces()
            .and().doNotHaveSimpleName(CentralMapperConfig.class.getSimpleName())
            .should(useTheCentralMapperConfig())
            .because("unmapped target properties must break the build");

    @ArchTest
    public static final ArchRule REPOSITORIES_ARE_INTERFACES_NAMED_REPOSITORY = classes()
            .that().resideInAPackage("..repository..").and().areInterfaces()
            .should().haveSimpleNameEndingWith("Repository");

    // ---- general hygiene -----------------------------------------------------------------------

    @ArchTest
    public static final ArchRule NO_STANDARD_STREAMS = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    public static final ArchRule NO_JAVA_UTIL_LOGGING = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    public static final ArchRule NO_GENERIC_EXCEPTIONS = GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

    // ---- the building blocks -------------------------------------------------------------------

    private static final List<Class<? extends java.lang.annotation.Annotation>> MAPPING_ANNOTATIONS = List.of(
            GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class,
            RequestMapping.class);

    /** Services, controllers and components we wrote (MapStruct's generated implementations are excluded). */
    private static DescribedPredicate<JavaClass> areHandWrittenSpringBeans() {
        return describe("are hand-written Spring beans", type ->
                (type.isAnnotatedWith(Service.class) || type.isAnnotatedWith(RestController.class)
                        || type.isAnnotatedWith(Component.class))
                        && !type.getPackageName().endsWith(".mapper"));
    }

    private static DescribedPredicate<JavaMethod> areEndpoints() {
        return describe("are HTTP endpoints", method -> MAPPING_ANNOTATIONS.stream().anyMatch(method::isAnnotatedWith));
    }

    /** {@code POST /api/auth/login} is the one endpoint that is open to everybody. */
    private static DescribedPredicate<JavaMethod> areNotThePublicLogin() {
        return describe("are not the public login", method ->
                !(method.getOwner().getSimpleName().equals("AuthController") && method.getName().equals("login")));
    }

    private static DescribedPredicate<JavaMethod> areDeclaredInAControllerWithPatientData() {
        return describe("are declared in a controller that handles patient-related data", method ->
                !CONTROLLERS_WITHOUT_PATIENT_DATA.contains(method.getOwner().getSimpleName()));
    }

    private static ArchCondition<JavaMethod> notTakeOrReturnEntities() {
        return new ArchCondition<>("not take or return JPA entities") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Stream.concat(method.getParameterTypes().stream(), Stream.of(method.getReturnType()))
                        .flatMap(type -> type.getAllInvolvedRawTypes().stream())
                        .filter(type -> type.isAnnotatedWith(Entity.class) || type.isAnnotatedWith(MappedSuperclass.class))
                        .forEach(type -> events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " exposes the entity " + type.getName())));
            }
        };
    }

    private static ArchCondition<JavaClass> beMappedUnderApi() {
        return new ArchCondition<>("be mapped under /api/") {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                boolean underApi = controller.tryGetAnnotationOfType(RequestMapping.class)
                        .map(mapping -> mapping.value().length > 0 && mapping.value()[0].startsWith("/api/"))
                        .orElse(false);
                events.add(new SimpleConditionEvent(controller, underApi,
                        controller.getName() + (underApi ? " is" : " is not") + " mapped under /api/"));
            }
        };
    }

    private static ArchCondition<JavaClass> implementAnInterfaceFromTheServicePackage() {
        return new ArchCondition<>("implement an interface from the service package") {
            @Override
            public void check(JavaClass implementation, ConditionEvents events) {
                boolean implementsOne = implementation.getAllRawInterfaces().stream()
                        .anyMatch(type -> type.getPackageName().endsWith(".service"));
                events.add(new SimpleConditionEvent(implementation, implementsOne,
                        implementation.getName() + (implementsOne ? " implements" : " does not implement")
                                + " a service interface"));
            }
        };
    }

    private static ArchCondition<JavaField> beFetchedLazily() {
        return new ArchCondition<>("be fetched lazily") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                FetchType fetch = field.tryGetAnnotationOfType(ManyToOne.class).map(ManyToOne::fetch)
                        .or(() -> field.tryGetAnnotationOfType(OneToOne.class).map(OneToOne::fetch))
                        .orElse(FetchType.LAZY);
                events.add(new SimpleConditionEvent(field, fetch == FetchType.LAZY,
                        field.getFullName() + " is fetched " + fetch));
            }
        };
    }

    private static ArchCondition<JavaClass> useTheCentralMapperConfig() {
        return new ArchCondition<>("be a @Mapper(config = CentralMapperConfig.class)") {
            @Override
            public void check(JavaClass mapper, ConditionEvents events) {
                boolean ok = mapper.tryGetAnnotationOfType(Mapper.class)
                        .map(annotation -> annotation.config() == CentralMapperConfig.class)
                        .orElse(false);
                events.add(new SimpleConditionEvent(mapper, ok,
                        mapper.getName() + (ok ? " uses" : " does not use") + " CentralMapperConfig"));
            }
        };
    }
}
