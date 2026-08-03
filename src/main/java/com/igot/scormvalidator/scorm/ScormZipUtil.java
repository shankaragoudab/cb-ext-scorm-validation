package com.igot.scormvalidator.scorm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Zip/filesystem helpers for the SCORM validation & rewire engine. Plain static utility class,
 * matching the convention used by {@link com.igot.scormvalidator.util.ProjectUtil}.
 */
public final class ScormZipUtil {

    private ScormZipUtil() {
    }

    /**
     * Extracts {@code zipFile} into {@code destDir}, guarding against zip-slip: every entry's
     * resolved path must remain inside {@code destDir}.
     */
    public static void extractZip(File zipFile, File destDir) throws IOException {
        if (!destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("Unable to create destination directory: " + destDir);
        }
        String destDirCanonical = destDir.getCanonicalPath();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(destDir, entry.getName());
                String outFileCanonical = outFile.getCanonicalPath();
                if (!outFileCanonical.equals(destDirCanonical)
                        && !outFileCanonical.startsWith(destDirCanonical + File.separator)) {
                    throw new IOException("Zip entry is outside of the target directory (zip-slip attempt): "
                            + entry.getName());
                }

                if (entry.isDirectory()) {
                    if (!outFile.exists() && !outFile.mkdirs()) {
                        throw new IOException("Unable to create directory: " + outFile);
                    }
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs()) {
                        throw new IOException("Unable to create directory: " + parent);
                    }
                    try (OutputStream os = new FileOutputStream(outFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Zips the contents of {@code sourceDir} into {@code outputZip}. Entry names are relative to
     * {@code sourceDir} with {@code /} separators; only regular files are added (directories are
     * implied by their files' entry names), matching the Node reference's {@code addDirToZip}.
     */
    public static void zipDirectory(File sourceDir, File outputZip) throws IOException {
        Path sourcePath = sourceDir.toPath();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZip))) {
            List<Path> files;
            try (Stream<Path> stream = Files.walk(sourcePath)) {
                files = stream.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
            }
            for (Path path : files) {
                String zipEntryName = sourcePath.relativize(path).toString().replace(File.separatorChar, '/');
                zos.putNextEntry(new ZipEntry(zipEntryName));
                Files.copy(path, zos);
                zos.closeEntry();
            }
        }
    }

    /**
     * Recursively searches {@code dir} for a file matching {@code filename} (case-insensitive).
     */
    public static File findFileCaseInsensitive(File dir, String filename) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        String lowerTarget = filename.toLowerCase(Locale.ROOT);
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findFileCaseInsensitive(child, filename);
                if (found != null) {
                    return found;
                }
            } else if (child.getName().toLowerCase(Locale.ROOT).equals(lowerTarget)) {
                return child;
            }
        }
        return null;
    }

    /**
     * Tries each of {@link ScormConstants#LAUNCH_CANDIDATES} in order; falls back to the first
     * {@code .html}/{@code .htm} file found anywhere in the tree. Returns {@code null} if nothing
     * launchable exists at all.
     */
    public static File findLaunchFile(File dir) {
        for (String candidate : ScormConstants.LAUNCH_CANDIDATES) {
            File found = findFileCaseInsensitive(dir, candidate);
            if (found != null) {
                return found;
            }
        }
        return findFirstHtmlFile(dir);
    }

    private static File findFirstHtmlFile(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findFirstHtmlFile(child);
                if (found != null) {
                    return found;
                }
            } else if (child.getName().toLowerCase(Locale.ROOT).matches(".*\\.html?$")) {
                return child;
            }
        }
        return null;
    }

    /**
     * Resolves an {@code href} taken from the manifest relative to {@code manifestFile}'s parent
     * directory. Strips any {@code ?query}/{@code #fragment} suffix and URL-decodes the
     * remainder (SCORM hrefs sometimes carry query strings or {@code %20}-style escapes).
     */
    public static File resolveRelative(File manifestFile, String href) {
        if (manifestFile == null || href == null) {
            return null;
        }
        String cleaned = href;
        int queryIdx = cleaned.indexOf('?');
        if (queryIdx >= 0) {
            cleaned = cleaned.substring(0, queryIdx);
        }
        int hashIdx = cleaned.indexOf('#');
        if (hashIdx >= 0) {
            cleaned = cleaned.substring(0, hashIdx);
        }
        String decoded;
        try {
            decoded = URLDecoder.decode(cleaned, StandardCharsets.UTF_8);
        } catch (Exception e) {
            decoded = cleaned;
        }
        File parent = manifestFile.getParentFile();
        return new File(parent, decoded);
    }
}
