package ru.hgd.sdlc.registry.domain.package_;

/**
 * Constants defining the release package format structure.
 * All release packages follow this standardized structure for consistency.
 *
 * <p>Package ZIP structure:
 * <pre>
 * release.zip
 * ├── release-manifest.json    # Package metadata
 * ├── flow.ir.json            # Compiled flow IR
 * ├── provenance.json         # Build provenance
 * ├── checksums.sha256        # SHA-256 checksums
 * ├── phases/
 * │   └── {phaseId}.ir.json   # Phase IR files
 * └── skills/
 *     └── {skillId}.ir.json   # Skill IR files
 * </pre>
 */
public final class PackageFormat {

    private PackageFormat() {
        // Utility class - no instantiation
    }

    /**
     * Current format version.
     */
    public static final int FORMAT_VERSION = 1;

    /**
     * File name for the release manifest.
     */
    public static final String FILE_MANIFEST = "release-manifest.json";

    /**
     * File name for the compiled flow IR.
     */
    public static final String FILE_FLOW_IR = "flow.ir.json";

    /**
     * File name for the provenance record.
     */
    public static final String FILE_PROVENANCE = "provenance.json";

    /**
     * File name for the checksum file.
     */
    public static final String FILE_CHECKSUMS = "checksums.sha256";

    /**
     * Directory for phase IR files.
     */
    public static final String DIR_PHASES = "phases/";

    /**
     * Directory for skill IR files.
     */
    public static final String DIR_SKILLS = "skills/";

    /**
     * File extension for IR JSON files.
     */
    public static final String IR_EXTENSION = ".ir.json";

    /**
     * Comment prefix for checksums file.
     */
    public static final String CHECKSUM_COMMENT_PREFIX = "# ";

    /**
     * SHA-256 hash length in hex characters.
     */
    public static final int SHA256_HEX_LENGTH = 64;

    /**
     * Creates a phase file path.
     *
     * @param phaseId the phase ID
     * @return the path within the package (e.g., "phases/setup.ir.json")
     */
    public static String phasePath(String phaseId) {
        return DIR_PHASES + phaseId + IR_EXTENSION;
    }

    /**
     * Creates a skill file path.
     *
     * @param skillId the skill ID
     * @return the path within the package (e.g., "skills/code-gen.ir.json")
     */
    public static String skillPath(String skillId) {
        return DIR_SKILLS + skillId + IR_EXTENSION;
    }

    /**
     * Extracts phase ID from a phase file path.
     *
     * @param path the path (e.g., "phases/setup.ir.json")
     * @return the phase ID, or null if the path is not a valid phase path
     */
    public static String extractPhaseId(String path) {
        if (path == null || !path.startsWith(DIR_PHASES) || !path.endsWith(IR_EXTENSION)) {
            return null;
        }
        return path.substring(DIR_PHASES.length(), path.length() - IR_EXTENSION.length());
    }

    /**
     * Extracts skill ID from a skill file path.
     *
     * @param path the path (e.g., "skills/code-gen.ir.json")
     * @return the skill ID, or null if the path is not a valid skill path
     */
    public static String extractSkillId(String path) {
        if (path == null || !path.startsWith(DIR_SKILLS) || !path.endsWith(IR_EXTENSION)) {
            return null;
        }
        return path.substring(DIR_SKILLS.length(), path.length() - IR_EXTENSION.length());
    }

    /**
     * Checks if a path is a phase file.
     *
     * @param path the path to check
     * @return true if the path is a phase file
     */
    public static boolean isPhasePath(String path) {
        return path != null && path.startsWith(DIR_PHASES) && path.endsWith(IR_EXTENSION);
    }

    /**
     * Checks if a path is a skill file.
     *
     * @param path the path to check
     * @return true if the path is a skill file
     */
    public static boolean isSkillPath(String path) {
        return path != null && path.startsWith(DIR_SKILLS) && path.endsWith(IR_EXTENSION);
    }
}
