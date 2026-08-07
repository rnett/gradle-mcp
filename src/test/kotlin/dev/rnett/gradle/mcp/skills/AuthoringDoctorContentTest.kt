package dev.rnett.gradle.mcp.skills

import dev.rnett.gradle.mcp.findProjectRoot
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Content gate for the Gradle Build Doctor workflow in `authoring-gradle-builds`.
 *
 * Minimal sanity assertions per the `add-gradle-build-doctor` change:
 * - SKILL.md exposes `### Build Health Assessment (Doctor)` and links the single
 *   reference at `references/build-health-assessment.md`.
 * - `### Performance Audit` is gone (replaced in place by the doctor).
 * - The doctor reference exists, points to the embedded best-practices corpus
 *   (`references/best-practices/_index.md`) as its PRIMARY read/apply source, and
 *   mentions `gradle_docs` as the authoritative/current supplement.
 * - Exactly one doctor reference file exists (no `doctor*.md` split).
 * - The doctor reference explicitly names migration guides and release notes as sources.
 * - The doctor reference instructs highlighting deprecations surfaced by probes.
 * - Taxonomy, evidence tags, dependency web fallback, and forward-compat calibration (added in revision 2).
 *
 * Note: `references/best-practices/_index.md` is a build-generated packaged artifact;
 * its absence from the source tree is expected and is NOT asserted here.
 */
class AuthoringDoctorContentTest {

    private val skillsDir = findProjectRoot().resolve("src/main/skills").toFile()
    private val skillDir = skillsDir.resolve("authoring-gradle-builds")
    private val skillFile = skillDir.resolve("SKILL.md")
    private val doctorReference = skillDir.resolve("references/build-health-assessment.md")

    @Test
    fun `the skill body exposes the Build Health Assessment workflow and links the doctor reference path`() {
        val content = skillFile.readText()

        assertTrue(
            "### Build Health Assessment" in content,
            "SKILL.md must contain the `### Build Health Assessment` heading"
        )
        assertTrue(
            "references/build-health-assessment.md" in content,
            "SKILL.md must link the exact doctor reference path `references/build-health-assessment.md`"
        )
    }

    @Test
    fun `the skill body no longer contains the replaced Performance Audit workflow`() {
        val content = skillFile.readText()

        assertTrue(
            "### Performance Audit" !in content,
            "SKILL.md must not contain the replaced `### Performance Audit` heading"
        )
    }

    @Test
    fun `doctor reference exists, points to the embedded corpus as PRIMARY, and mentions gradle_docs`() {
        assertTrue(
            doctorReference.exists(),
            "Doctor reference must exist at references/build-health-assessment.md"
        )

        val content = doctorReference.readText()
        assertTrue(
            "references/best-practices/_index.md" in content,
            "Doctor reference must point to the embedded best-practices corpus (`references/best-practices/_index.md`)"
        )
        assertTrue(
            "PRIMARY" in content,
            "Doctor reference must mark the embedded best-practices corpus as PRIMARY"
        )
        assertTrue(
            "gradle_docs" in content,
            "Doctor reference must mention `gradle_docs` as a source"
        )
    }

    @Test
    fun `exactly one doctor reference file exists at the fixed path`() {
        val referencesDir = skillDir.resolve("references")

        assertTrue(
            doctorReference.exists(),
            "Doctor reference must exist at the fixed path references/build-health-assessment.md"
        )

        val secondDoctorFiles = referencesDir.walkTopDown()
            .filter { it.isFile && it.extension == "md" && it.name != doctorReference.name && it.name.contains("doctor") }
            .map { it.relativeTo(skillsDir) }
            .toList()

        assertTrue(
            secondDoctorFiles.isEmpty(),
            "No second doctor reference split is permitted (found: $secondDoctorFiles)"
        )
    }

    @Test
    fun `doctor reference explicitly names migration guides and release notes as sources`() {
        val content = doctorReference.readText()

        assertTrue(
            "migration guides" in content,
            "Doctor reference must explicitly name migration guides as a source"
        )
        assertTrue(
            "release notes" in content,
            "Doctor reference must explicitly name release notes as a source"
        )
        assertTrue(
            "references/upgrading-and-release-notes.md" in content,
            "Doctor reference must name the skill's upgrading-and-release-notes reference as the migration-guide/release-notes source"
        )
        assertTrue(
            "gradle_docs(query=\"tag:release-notes\")" in content,
            "Doctor reference must name gradle_docs release notes for the wrapper's version"
        )
    }

    @Test
    fun `doctor reference instructs highlighting deprecations surfaced by probes`() {
        val content = doctorReference.readText()

        assertTrue(
            "gradle help --warning-mode all" in content,
            "Doctor reference must keep the deprecation-warning probe"
        )
        assertTrue(
            "record it as a finding" in content,
            "Doctor reference must instruct recording each surfaced deprecation as a finding or equivalent flag"
        )
        assertTrue(
            "highlight it in the report" in content,
            "Doctor reference must instruct highlighting deprecations surfaced by probes in the report"
        )
    }

    @Test
    fun `doctor reference defines finding taxonomy with build script errors vs recommendations and report classes`() {
        val content = doctorReference.readText()

        assertTrue(
            "Build script errors / mistakes" in content,
            "Doctor reference must define the Build script errors / mistakes finding class"
        )
        assertTrue(
            "Best practice / recommendation compliance" in content,
            "Doctor reference must define the Best practice / recommendation compliance finding class"
        )
        assertTrue(
            "Future Breakage" in content,
            "Doctor reference must define the Future Breakage sub-type for forward-compat calibration"
        )
        assertTrue(
            "A. Build Script Errors" in content,
            "Doctor reference must define report class A. Build Script Errors"
        )
        assertTrue(
            "B. Forward-Compat" in content,
            "Doctor reference must define report class B. Forward-Compat & Risks"
        )
        assertTrue(
            "C. Recommendations" in content,
            "Doctor reference must define report class C. Recommendations"
        )
        assertTrue(
            "D. Healthy Areas" in content,
            "Doctor reference must define report class D. Healthy Areas"
        )
        assertTrue(
            "Fix:" in content,
            "Doctor reference must use Fix: directive for build script errors"
        )
        assertTrue(
            "Consider:" in content,
            "Doctor reference must use Consider: framing for compliance findings"
        )
    }

    @Test
    fun `doctor reference defines evidence tags direct observed web`() {
        val content = doctorReference.readText()

        assertTrue(
            "`direct`" in content,
            "Doctor reference must define evidence tag direct (from build files themselves)"
        )
        assertTrue(
            "`observed`" in content,
            "Doctor reference must define evidence tag observed (from minimal probes)"
        )
        assertTrue(
            "`web`" in content,
            "Doctor reference must define evidence tag web (version lookups)"
        )
    }
    @Test
    fun `doctor reference is not a full code review of the build logic`() {
        val content = doctorReference.readText()

        assertTrue(
            "not a full code review of the build logic" in content,
            "Doctor reference must state it is not a full code review of the build logic"
        )
    }
    @Test
    fun `doctor reference defines forward-compat calibration high only if next major`() {
        val content = doctorReference.readText()

        assertTrue(
            "High only if removal lands in the next Gradle major" in content,
            "Doctor reference must calibrate forward-compat severity: High only if removal lands in the next Gradle major"
        )
        assertTrue(
            "Future Breakage (Gradle 10)" in content,
            "Doctor reference must label Future Breakage with the next major (e.g., Future Breakage (Gradle 10))"
        )
    }
}
