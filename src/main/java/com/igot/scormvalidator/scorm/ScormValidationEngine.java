package com.igot.scormvalidator.scorm;

import com.igot.scormvalidator.scorm.model.ScormMetadata;
import com.igot.scormvalidator.scorm.model.ValidationResult;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.igot.scormvalidator.scorm.ScormConstants.MANIFEST_FILENAME;
import static com.igot.scormvalidator.scorm.ScormConstants.SCORM_TYPE_ATTR;
import static com.igot.scormvalidator.scorm.ScormConstants.SCO_VALUE;

/**
 * Validates an extracted SCORM package tree. Stricter than the Node reference implementation:
 * it also verifies that every resource {@code href} actually resolves to a file on disk.
 */
@Component
@RequiredArgsConstructor
public class ScormValidationEngine {

    private static final Logger logger = LoggerFactory.getLogger(ScormValidationEngine.class);
    private static final int MAX_JS_FILES_SCANNED = 10;

    private final ScormManifestHandler manifestHandler;

    public ValidationResult validate(File extractedDir) {
        File manifestFile = ScormZipUtil.findFileCaseInsensitive(extractedDir, MANIFEST_FILENAME);
        if (manifestFile == null) {
            return ValidationResult.builder()
                    .valid(false)
                    .hasManifest(false)
                    .resumeCapable(false)
                    .missingFiles(List.of(MANIFEST_FILENAME))
                    .errors(List.of("No imsmanifest.xml found in the package"))
                    .build();
        }

        Document doc;
        try {
            doc = manifestHandler.parse(manifestFile);
        } catch (Exception e) {
            return ValidationResult.builder()
                    .valid(false)
                    .hasManifest(true)
                    .resumeCapable(false)
                    .errors(List.of("Manifest XML is malformed: " + e.getMessage()))
                    .build();
        }

        List<String> errors = new ArrayList<>();
        List<String> missingFiles = new ArrayList<>();

        ScormMetadata.ScormMetadataBuilder metadataBuilder = ScormMetadata.builder()
                .version(manifestHandler.getSchemaVersion(doc))
                .title(manifestHandler.getTitle(doc));

        if (!manifestHandler.hasOrganizations(doc)) {
            errors.add("Missing <organizations> block in manifest");
        }

        List<Element> resourceElements = manifestHandler.getResourceElements(doc);
        boolean resumeCapable = false;
        if (resourceElements.isEmpty()) {
            errors.add("Missing <resources> block in manifest");
        } else {
            String firstHref = null;
            String firstScoHref = null;
            for (Element resource : resourceElements) {
                String href = resource.hasAttribute("href") ? resource.getAttribute("href") : null;
                boolean hasHref = href != null && !href.isBlank();
                if (hasHref && firstHref == null) {
                    firstHref = href;
                }

                boolean isSco = SCO_VALUE.equalsIgnoreCase(resource.getAttribute(SCORM_TYPE_ATTR));
                if (isSco) {
                    resumeCapable = true;
                    if (hasHref && firstScoHref == null) {
                        firstScoHref = href;
                    }
                }

                if (hasHref) {
                    File resolved = ScormZipUtil.resolveRelative(manifestFile, href);
                    if (resolved == null || !resolved.exists()) {
                        missingFiles.add("Referenced file not found: " + href);
                    }
                }
            }
            metadataBuilder.launchFile(firstScoHref != null ? firstScoHref : firstHref);
        }

        if (!resumeCapable) {
            resumeCapable = scanForResumeIndicators(extractedDir);
        }

        boolean valid = errors.isEmpty() && missingFiles.isEmpty();

        return ValidationResult.builder()
                .valid(valid)
                .hasManifest(true)
                .resumeCapable(resumeCapable)
                .missingFiles(missingFiles)
                .errors(errors)
                .metadata(metadataBuilder.build())
                .build();
    }

    private boolean scanForResumeIndicators(File extractedDir) {
        List<File> jsFiles = new ArrayList<>();
        collectJsFiles(extractedDir, jsFiles, MAX_JS_FILES_SCANNED);
        for (File jsFile : jsFiles) {
            try {
                String content = Files.readString(jsFile.toPath(), StandardCharsets.UTF_8);
                if (content.contains("cmi.suspend_data") || content.contains("cmi.location")) {
                    return true;
                }
            } catch (IOException e) {
                logger.debug("ScormValidationEngine: skipping unreadable JS file {}", jsFile, e);
            }
        }
        return false;
    }

    private void collectJsFiles(File dir, List<File> accumulator, int limit) {
        if (dir == null || !dir.isDirectory() || accumulator.size() >= limit) {
            return;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (accumulator.size() >= limit) {
                return;
            }
            if (child.isDirectory()) {
                collectJsFiles(child, accumulator, limit);
            } else if (child.getName().toLowerCase(Locale.ROOT).endsWith(".js")) {
                accumulator.add(child);
            }
        }
    }
}
