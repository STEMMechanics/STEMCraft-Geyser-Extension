package au.com.stemmechanics.stemcraftge;

import java.util.List;

/** Resolves exact and wildcard block-override keys. */
final class BlockOverrideMatcher {
    private BlockOverrideMatcher() {
    }

    /**
     * Returns an exact key first, otherwise the first matching wildcard key.
     *
     * @param identifier complete Java block identifier
     * @param patterns override keys in YAML configuration order
     * @return matching override key, or {@code null}
     */
    static String match(String identifier, List<String> patterns) {
        if (patterns.contains(identifier)) {
            return identifier;
        }
        for (String candidate : patterns) {
            if (candidate.indexOf('*') >= 0 && globMatches(candidate, identifier)) {
                return candidate;
            }
        }
        return null;
    }

    /** Matches a string against a glob containing zero or more {@code *} wildcards. */
    static boolean globMatches(String pattern, String value) {
        int patternIndex = 0, valueIndex = 0, starIndex = -1, retryIndex = -1;
        while (valueIndex < value.length()) {
            if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == value.charAt(valueIndex)) {
                patternIndex++;
                valueIndex++;
            } else if (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
                starIndex = patternIndex++;
                retryIndex = valueIndex;
            } else if (starIndex >= 0) {
                patternIndex = starIndex + 1;
                valueIndex = ++retryIndex;
            } else {
                return false;
            }
        }
        while (patternIndex < pattern.length() && pattern.charAt(patternIndex) == '*') {
            patternIndex++;
        }
        return patternIndex == pattern.length();
    }
}
