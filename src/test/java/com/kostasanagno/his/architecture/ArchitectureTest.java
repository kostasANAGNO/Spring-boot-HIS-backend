package com.kostasanagno.his.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Runs every rule of {@link HisArchRules} against the production code. If one fails, the message names the offending
 * class/method; either fix the code or - if the rule is wrong for a good reason - change the rule here and in
 * docs/CONVENTIONS.md in the same commit.
 */
@AnalyzeClasses(packages = "com.kostasanagno.his", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchTests RULES = ArchTests.in(HisArchRules.class);
}
