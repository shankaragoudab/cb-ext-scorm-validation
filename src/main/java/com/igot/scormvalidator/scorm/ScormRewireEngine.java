package com.igot.scormvalidator.scorm;

import com.igot.scormvalidator.scorm.model.RepairAction;
import com.igot.scormvalidator.scorm.model.RewireResult;
import com.igot.scormvalidator.scorm.model.ValidationResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.igot.scormvalidator.scorm.ScormConstants.MANIFEST_FILENAME;

/**
 * Attempts to auto-repair ("rewire") a SCORM package that failed {@link ScormValidationEngine}
 * validation: structural manifest/file fixes only (no HTML shim/tracker injection — that is
 * explicitly out of scope for this service).
 */
@Component
@RequiredArgsConstructor
public class ScormRewireEngine {

    private static final Logger logger = LoggerFactory.getLogger(ScormRewireEngine.class);

    private final ScormManifestHandler manifestHandler;
    private final ScormValidationEngine validationEngine;

    public RewireResult rewire(File extractedDir, ValidationResult initialValidation) {
        List<RepairAction> repairs = new ArrayList<>();

        File manifestFile = ScormZipUtil.findFileCaseInsensitive(extractedDir, MANIFEST_FILENAME);
        Document doc;

        if (manifestFile == null) {
            File launchFile = ScormZipUtil.findLaunchFile(extractedDir);
            if (launchFile == null) {
                return unrepairable(
                        "No imsmanifest.xml found and no HTML launch file exists anywhere in the package; "
                                + "cannot auto-generate a manifest", false);
            }

            manifestFile = new File(extractedDir, MANIFEST_FILENAME);
            String relativeHref = relativeTo(extractedDir, launchFile);
            Document minimal = manifestHandler.buildMinimal(relativeHref);
            try {
                manifestHandler.write(minimal, manifestFile);
            } catch (Exception e) {
                logger.warn("ScormRewireEngine: failed to write generated manifest", e);
                return failedRewire(repairs, "Unable to write generated manifest: " + e.getMessage());
            }
            repairs.add(RepairAction.builder()
                    .code("CREATED_MANIFEST")
                    .description("Created missing imsmanifest.xml referencing launch file " + relativeHref)
                    .build());

            try {
                doc = manifestHandler.parse(manifestFile);
            } catch (Exception e) {
                return failedRewire(repairs, "Unable to re-parse generated manifest: " + e.getMessage());
            }
        } else {
            Document parsed;
            try {
                parsed = manifestHandler.parse(manifestFile);
            } catch (Exception parseError) {
                File launchFile = ScormZipUtil.findLaunchFile(extractedDir);
                if (launchFile == null) {
                    return failedRewire(repairs, "Manifest is corrupt and no HTML launch file exists to rebuild from: "
                            + parseError.getMessage());
                }
                String relativeHref = relativeTo(manifestFile.getParentFile(), launchFile);
                Document minimal = manifestHandler.buildMinimal(relativeHref);
                try {
                    manifestHandler.write(minimal, manifestFile);
                    parsed = manifestHandler.parse(manifestFile);
                } catch (Exception e) {
                    return failedRewire(repairs, "Unable to rebuild corrupt manifest: " + e.getMessage());
                }
                repairs.add(RepairAction.builder()
                        .code("REBUILT_MANIFEST")
                        .description("Rebuilt corrupt imsmanifest.xml referencing launch file " + relativeHref)
                        .build());
            }
            doc = parsed;
        }

        manifestHandler.ensureAdlcpNamespace(doc).ifPresent(repairs::add);

        String preferredVersion = initialValidation != null && initialValidation.getMetadata() != null
                ? initialValidation.getMetadata().getVersion() : null;
        manifestHandler.ensureMetadata(doc, preferredVersion).ifPresent(repairs::add);

        ScormManifestHandler.ScormTypeFixResult fixResult =
                manifestHandler.ensureScormTypeAndFixLaunchFile(doc, manifestFile, extractedDir);
        repairs.addAll(fixResult.getRepairs());

        try {
            manifestHandler.write(doc, manifestFile);
        } catch (Exception e) {
            logger.warn("ScormRewireEngine: failed to write repaired manifest", e);
            return failedRewire(repairs, "Unable to write repaired manifest: " + e.getMessage());
        }

        ValidationResult finalValidation = validationEngine.validate(extractedDir);
        return RewireResult.builder()
                .success(finalValidation.isValid())
                .repairs(repairs)
                .finalValidation(finalValidation)
                .build();
    }

    private String relativeTo(File baseDir, File target) {
        return baseDir.toPath().toAbsolutePath().normalize()
                .relativize(target.toPath().toAbsolutePath().normalize())
                .toString().replace(File.separatorChar, '/');
    }

    private RewireResult unrepairable(String errorMessage, boolean hasManifest) {
        return RewireResult.builder()
                .success(false)
                .repairs(new ArrayList<>())
                .finalValidation(ValidationResult.builder()
                        .valid(false)
                        .hasManifest(hasManifest)
                        .missingFiles(List.of(MANIFEST_FILENAME))
                        .errors(List.of(errorMessage))
                        .build())
                .build();
    }

    private RewireResult failedRewire(List<RepairAction> repairs, String errorMessage) {
        return RewireResult.builder()
                .success(false)
                .repairs(repairs)
                .finalValidation(ValidationResult.builder()
                        .valid(false)
                        .hasManifest(true)
                        .errors(List.of(errorMessage))
                        .build())
                .build();
    }
}
