package com.igot.scormvalidator.scorm;

import com.igot.scormvalidator.scorm.model.RewireResult;
import com.igot.scormvalidator.scorm.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static com.igot.scormvalidator.scorm.ScormValidationEngineTest.manifestReferencing;
import static com.igot.scormvalidator.scorm.ScormValidationEngineTest.writeFile;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScormRewireEngineTest {

    @TempDir
    File tempDir;

    private ScormManifestHandler manifestHandler;
    private ScormValidationEngine validationEngine;
    private ScormRewireEngine rewireEngine;

    @BeforeEach
    void setUp() {
        manifestHandler = new ScormManifestHandler();
        validationEngine = new ScormValidationEngine(manifestHandler);
        rewireEngine = new ScormRewireEngine(manifestHandler, validationEngine);
    }

    @Test
    void rewiresPackageMissingManifestEntirelyByGeneratingOne() throws IOException {
        writeFile(new File(tempDir, "index.html"), "<html><body>Course</body></html>");

        ValidationResult initial = validationEngine.validate(tempDir);
        assertFalse(initial.isValid());

        RewireResult rewireResult = rewireEngine.rewire(tempDir, initial);

        assertTrue(rewireResult.isSuccess());
        assertTrue(rewireResult.getFinalValidation().isValid());
        assertTrue(rewireResult.getRepairs().stream().anyMatch(r -> "CREATED_MANIFEST".equals(r.getCode())));
        assertTrue(new File(tempDir, ScormConstants.MANIFEST_FILENAME).exists());
    }

    @Test
    void rewiresPackageWithBrokenHrefByFindingRealLaunchFile() throws IOException {
        writeFile(new File(tempDir, "index.html"), "<html><body>Course</body></html>");
        writeFile(new File(tempDir, ScormConstants.MANIFEST_FILENAME), manifestReferencing("missing.html"));

        ValidationResult initial = validationEngine.validate(tempDir);
        assertFalse(initial.isValid());

        RewireResult rewireResult = rewireEngine.rewire(tempDir, initial);

        assertTrue(rewireResult.isSuccess());
        assertTrue(rewireResult.getFinalValidation().isValid());
        assertTrue(rewireResult.getRepairs().stream().anyMatch(r -> "FIXED_LAUNCH_FILE".equals(r.getCode())));
    }

    @Test
    void reportsUnrepairableWhenNoManifestAndNoLaunchFileExistAnywhere() throws IOException {
        writeFile(new File(tempDir, "readme.txt"), "no launchable content here");

        ValidationResult initial = validationEngine.validate(tempDir);
        assertFalse(initial.isValid());

        RewireResult rewireResult = rewireEngine.rewire(tempDir, initial);

        assertFalse(rewireResult.isSuccess());
        assertFalse(rewireResult.getFinalValidation().isValid());
        assertTrue(rewireResult.getFinalValidation().getErrors().stream()
                .anyMatch(e -> e.contains("cannot auto-generate a manifest")));
    }
}
