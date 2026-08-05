package com.igot.scormvalidator.scorm;

import com.igot.scormvalidator.scorm.model.RewireResult;
import com.igot.scormvalidator.scorm.model.ScormProcessingResult;
import com.igot.scormvalidator.scorm.model.ValidationResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Orchestrates the full SCORM validation/rewire pipeline for a single downloaded zip file:
 * extract -&gt; validate -&gt; (rewire if invalid) -&gt; re-zip if repaired.
 */
@Component
@RequiredArgsConstructor
public class ScormPackageProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ScormPackageProcessor.class);

    private final ScormValidationEngine validationEngine;
    private final ScormRewireEngine rewireEngine;

    public ScormProcessingResult process(File zipFile) throws IOException {
        Path tempDir = Files.createTempDirectory("scorm_validate_");
        try {
            try {
                ScormZipUtil.extractZip(zipFile, tempDir.toFile());
            } catch (Exception e) {
                logger.warn("ScormPackageProcessor: unable to extract SCORM package {}", zipFile.getAbsolutePath(), e);
                return unreadablePackageResult(e);
            }

            ValidationResult validation;
            try {
                validation = validationEngine.validate(tempDir.toFile());
            } catch (Exception e) {
                logger.warn("ScormPackageProcessor: validation failed for package {}", zipFile.getAbsolutePath(), e);
                return unreadablePackageResult(e);
            }

            if (validation.isValid()) {
                String launchFile = validation.getMetadata() != null ? validation.getMetadata().getLaunchFile() : null;
                return ScormProcessingResult.builder()
                        .outcome(ScormProcessingResult.Outcome.VALID)
                        .initialValidation(validation)
                        .launchFile(launchFile)
                        .build();
            }

            RewireResult rewireResult = rewireEngine.rewire(tempDir.toFile(), validation);
            if (rewireResult.isSuccess()) {
                File rewiredZip = Files.createTempFile("scorm_rewired_", ".zip").toFile();
                ScormZipUtil.zipDirectory(tempDir.toFile(), rewiredZip);
                String launchFile = rewireResult.getFinalValidation() != null
                        && rewireResult.getFinalValidation().getMetadata() != null
                        ? rewireResult.getFinalValidation().getMetadata().getLaunchFile() : null;
                return ScormProcessingResult.builder()
                        .outcome(ScormProcessingResult.Outcome.ENHANCED)
                        .initialValidation(validation)
                        .rewireResult(rewireResult)
                        .rewiredZipFile(rewiredZip)
                        .launchFile(launchFile)
                        .build();
            }

            return ScormProcessingResult.builder()
                    .outcome(ScormProcessingResult.Outcome.INVALID)
                    .initialValidation(validation)
                    .rewireResult(rewireResult)
                    .build();
        } finally {
            deleteRecursively(tempDir.toFile());
        }
    }

    private ScormProcessingResult unreadablePackageResult(Exception e) {
        return ScormProcessingResult.builder()
                .outcome(ScormProcessingResult.Outcome.INVALID)
                .initialValidation(ValidationResult.builder()
                        .valid(false)
                        .errors(List.of("Unable to read package: " + e.getMessage()))
                        .build())
                .build();
    }

    private void deleteRecursively(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir.toPath())) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    logger.warn("ScormPackageProcessor: failed to delete temp path {}", path, e);
                }
            });
        } catch (IOException e) {
            logger.warn("ScormPackageProcessor: failed to walk temp dir {} for cleanup", dir, e);
        }
    }
}
