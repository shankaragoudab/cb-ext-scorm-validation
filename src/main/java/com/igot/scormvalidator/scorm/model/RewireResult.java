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
public class RewireResult {
    private boolean success;
    @Builder.Default
    private List<RepairAction> repairs = new ArrayList<>();
    private ValidationResult finalValidation;
}
