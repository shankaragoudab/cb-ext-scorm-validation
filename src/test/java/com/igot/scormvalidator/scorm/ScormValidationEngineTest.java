package com.igot.scormvalidator.scorm;

import com.igot.scormvalidator.scorm.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScormValidationEngineTest {

    @TempDir
    File tempDir;

    private ScormValidationEngine validationEngine;

    @BeforeEach
    void setUp() {
        validationEngine = new ScormValidationEngine(new ScormManifestHandler());
    }

    @Test
    void validatesAFullyValidPackage() throws IOException {
        writeFile(new File(tempDir, "index.html"), "<html><body>Course</body></html>");
        writeFile(new File(tempDir, ScormConstants.MANIFEST_FILENAME), manifestReferencing("index.html"));

        ValidationResult result = validationEngine.validate(tempDir);

        assertTrue(result.isValid());
        assertTrue(result.isHasManifest());
        assertTrue(result.getMissingFiles().isEmpty());
        assertTrue(result.getErrors().isEmpty());
        assertEquals("index.html", result.getMetadata().getLaunchFile());
    }

    @Test
    void flagsMissingManifestAsInvalid() throws IOException {
        writeFile(new File(tempDir, "index.html"), "<html><body>Course</body></html>");

        ValidationResult result = validationEngine.validate(tempDir);

        assertFalse(result.isValid());
        assertFalse(result.isHasManifest());
        assertTrue(result.getMissingFiles().contains(ScormConstants.MANIFEST_FILENAME));
    }

    @Test
    void flagsMissingReferencedResourceFileAsInvalid() throws IOException {
        writeFile(new File(tempDir, "index.html"), "<html><body>Course</body></html>");
        writeFile(new File(tempDir, ScormConstants.MANIFEST_FILENAME), manifestReferencing("missing.html"));

        ValidationResult result = validationEngine.validate(tempDir);

        assertFalse(result.isValid());
        assertTrue(result.getMissingFiles().stream().anyMatch(m -> m.contains("missing.html")));
    }

    @Test
    void flagsPackageWithNoManifestAndNoHtmlAsInvalid() throws IOException {
        writeFile(new File(tempDir, "readme.txt"), "no launchable content here");

        ValidationResult result = validationEngine.validate(tempDir);

        assertFalse(result.isValid());
        assertFalse(result.isHasManifest());
    }

    static String manifestReferencing(String href) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<manifest identifier=\"course_manifest\" version=\"1\"\n"
                + "  xmlns=\"http://www.imsproject.org/xsd/imscp_rootv1p1p2\"\n"
                + "  xmlns:adlcp=\"http://www.adlnet.org/xsd/adlcp_rootv1p2\"\n"
                + "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + "  <metadata>\n"
                + "    <schema>ADL SCORM</schema>\n"
                + "    <schemaversion>1.2</schemaversion>\n"
                + "  </metadata>\n"
                + "  <organizations default=\"org_1\">\n"
                + "    <organization identifier=\"org_1\">\n"
                + "      <title>Course</title>\n"
                + "      <item identifier=\"item_1\" identifierref=\"resource_1\">\n"
                + "        <title>Course</title>\n"
                + "      </item>\n"
                + "    </organization>\n"
                + "  </organizations>\n"
                + "  <resources>\n"
                + "    <resource identifier=\"resource_1\" type=\"webcontent\" adlcp:scormtype=\"sco\" href=\"" + href + "\">\n"
                + "      <file href=\"" + href + "\"/>\n"
                + "    </resource>\n"
                + "  </resources>\n"
                + "</manifest>";
    }

    static void writeFile(File file, String content) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }
}
