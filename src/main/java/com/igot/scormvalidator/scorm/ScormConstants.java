package com.igot.scormvalidator.scorm;

import java.util.List;

/**
 * Constants shared by the SCORM validation/rewire engine. Mirrors the shape of
 * {@link com.igot.scormvalidator.util.Constants} but scoped to package-processing concerns only.
 */
public final class ScormConstants {

    private ScormConstants() {
    }

    public static final String MANIFEST_FILENAME = "imsmanifest.xml";
    public static final String ADLCP_NAMESPACE_URI = "http://www.adlnet.org/xsd/adlcp_rootv1p2";
    public static final String IMSCP_NAMESPACE_URI = "http://www.imsproject.org/xsd/imscp_rootv1p1p2";
    public static final String XSI_NAMESPACE_URI = "http://www.w3.org/2001/XMLSchema-instance";
    public static final String SCORM_TYPE_ATTR = "adlcp:scormtype";
    public static final String SCO_VALUE = "sco";
    public static final String DEFAULT_SCORM_VERSION = "1.2";

    /**
     * Ordered fallback candidates used when no launchable file can be determined from the
     * manifest itself; first match wins.
     */
    public static final List<String> LAUNCH_CANDIDATES = List.of(
            "index_lms.html", "index_lms.htm",
            "story.html", "story.htm",
            "index.html", "index.htm",
            "launch.html", "launch.htm",
            "default.html", "default.htm"
    );
}
