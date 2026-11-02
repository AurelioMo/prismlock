package io.github.aureliomo.prismlock;

final class CatalogScale {
    static double percent(String kind, int size, double width, double height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid image dimensions");
        double target = "icons".equals(kind)
                ? Math.min(80, Math.max(30, size)) * 4.0 : 132.0;
        return target / Math.max(width, height) * 100;
    }
}
