package io.github.aureliomo.prismlock;

/** Semantic-version helpers used by the GitHub updater. */
final class Versions {
    private Versions() {
    }

    static String normalize(String value) {
        if (value == null) return "";
        String version = value.trim();
        if (version.startsWith("v") || version.startsWith("V")) version = version.substring(1);
        int metadata = version.indexOf('+');
        if (metadata >= 0) version = version.substring(0, metadata);
        int prerelease = version.indexOf('-');
        if (prerelease >= 0) version = version.substring(0, prerelease);
        return version.trim();
    }

    static int[] parts(String value) {
        String version = normalize(value);
        String[] fields = version.split("\\.");
        int[] result = new int[3];
        for (int index = 0; index < result.length && index < fields.length; index++) {
            String field = fields[index];
            int end = 0;
            while (end < field.length() && Character.isDigit(field.charAt(end))) end++;
            if (end == 0) continue;
            try {
                result[index] = Integer.parseInt(field.substring(0, end));
            } catch (NumberFormatException ignored) {
                result[index] = 0;
            }
        }
        return result;
    }

    static boolean isNewer(String candidate, String current) {
        int[] next = parts(candidate);
        int[] installed = parts(current);
        for (int index = 0; index < next.length; index++) {
            if (next[index] != installed[index]) return next[index] > installed[index];
        }
        return false;
    }
}
