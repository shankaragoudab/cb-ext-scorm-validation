package com.igot.scormvalidator.scorm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    private boolean valid;
    private boolean hasManifest;
    private boolean resumeCapable;
    @Builder.Default
    private List<String> missingFiles = new ArrayList<>();
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    private ScormMetadata metadata;
}
