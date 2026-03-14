package ru.hgd.sdlc.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SemverUtil {
    private static final Pattern SEMVER = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    private SemverUtil() {
    }

    public static String initial() {
        return "1.0.0";
    }

    public static String incrementPatch(String version) {
        if (version == null) {
            throw new ValidationException("Version is required");
        }
        Matcher matcher = SEMVER.matcher(version.trim());
        if (!matcher.matches()) {
            throw new ValidationException("Invalid semver: " + version);
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        return major + "." + minor + "." + (patch + 1);
    }
}
