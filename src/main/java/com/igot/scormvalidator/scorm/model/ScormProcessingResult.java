package com.igot.scormvalidator.scorm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScormProcessingResult {

    public enum Outcome {
        VALID, ENHANCED, INVALID
    }

    private Outcome outcome;
    private ValidationResult initialValidation;
    private RewireResult rewireResult;
    /**
     * Only populated when {@link Outcome#ENHANCED}. Caller is responsible for deleting this
     * temp file once it has been uploaded.
     */
    private File rewiredZipFile;
    private String launchFile;
}
