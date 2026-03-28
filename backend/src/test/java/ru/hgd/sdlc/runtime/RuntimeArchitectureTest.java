package ru.hgd.sdlc.runtime;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class RuntimeArchitectureTest {
    private static final JavaClasses RUNTIME_CLASSES = new ClassFileImporter()
            .importPackages("ru.hgd.sdlc.runtime");

    @Test
    void apiDependsOnlyOnRuntimeCommandAndQueryServices() {
        DescribedPredicate<JavaClass> allowedDependencies = new DescribedPredicate<>("allowed runtime api dependencies") {
            @Override
            public boolean test(JavaClass input) {
                if (input == null) {
                    return true;
                }
                String packageName = input.getPackageName();
                String simpleName = input.getSimpleName();

                if (packageName.startsWith("java.")
                        || packageName.startsWith("javax.")
                        || packageName.startsWith("jakarta.")
                        || packageName.startsWith("org.springframework.")
                        || packageName.startsWith("com.fasterxml.")) {
                    return true;
                }
                if (packageName.startsWith("ru.hgd.sdlc.runtime.api")) {
                    return true;
                }
                if (packageName.startsWith("ru.hgd.sdlc.runtime.domain")) {
                    return true;
                }
                if (packageName.startsWith("ru.hgd.sdlc.runtime.application.dto")) {
                    return true;
                }
                if (packageName.startsWith("ru.hgd.sdlc.runtime.application.command")) {
                    return true;
                }
                if (packageName.startsWith("ru.hgd.sdlc.runtime.application.service")) {
                    return "RuntimeCommandService".equals(simpleName)
                            || "RuntimeQueryService".equals(simpleName);
                }
                if (packageName.startsWith("ru.hgd.sdlc.auth.domain")
                        || packageName.startsWith("ru.hgd.sdlc.common")
                        || packageName.startsWith("ru.hgd.sdlc.idempotency.application")) {
                    return true;
                }
                return false;
            }
        };

        classes()
                .that()
                .resideInAPackage("..runtime.api..")
                .should()
                .onlyDependOnClassesThat(allowedDependencies)
                .check(RUNTIME_CLASSES);
    }

    @Test
    void querySideMustNotDependOnCommandPackage() {
        noClasses()
                .that()
                .haveSimpleName("RuntimeQueryService")
                .or()
                .haveSimpleName("GitReviewService")
                .or()
                .haveSimpleName("NodeLogService")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..runtime.application.command..")
                .check(RUNTIME_CLASSES);
    }

    @Test
    void commandSideMustNotDependOnQuerySideServices() {
        noClasses()
                .that()
                .haveSimpleName("RuntimeCommandService")
                .or()
                .haveSimpleName("RunLifecycleService")
                .or()
                .haveSimpleName("RunStepService")
                .or()
                .haveSimpleName("GateDecisionService")
                .should()
                .dependOnClassesThat()
                .haveSimpleName("RuntimeQueryService")
                .orShould()
                .dependOnClassesThat()
                .haveSimpleName("GitReviewService")
                .orShould()
                .dependOnClassesThat()
                .haveSimpleName("NodeLogService")
                .check(RUNTIME_CLASSES);
    }

    @Test
    void applicationServicesMustNotUseDirectCliOrFsApis() {
        noClasses()
                .that()
                .resideInAPackage("..runtime.application.service..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.nio.file.Files")
                .check(RUNTIME_CLASSES);

        noClasses()
                .that()
                .resideInAPackage("..runtime.application.service..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.lang.ProcessBuilder")
                .check(RUNTIME_CLASSES);

        noClasses()
                .that()
                .resideInAPackage("..runtime.application.service..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("java.io.RandomAccessFile")
                .check(RUNTIME_CLASSES);
    }

    @Test
    void writeSideServicesDependOnRuntimeStepTxService() {
        classes()
                .that()
                .haveSimpleName("RunLifecycleService")
                .or()
                .haveSimpleName("RunStepService")
                .or()
                .haveSimpleName("GateDecisionService")
                .should()
                .dependOnClassesThat()
                .haveSimpleName("RuntimeStepTxService")
                .check(RUNTIME_CLASSES);
    }
}
