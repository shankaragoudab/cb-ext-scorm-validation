package com.igot.scormvalidator.scorm.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScormMetadata {
    private String version;
    private String title;
    private String launchFile;
}
