package dev.dynamicdock;

/** Pure layout math shared by the HUD and preview, including the complete offhand/indicator footprint. */
public final class DockLayout {
    public final float[] x = new float[10];
    public final float left, right, top, bottom, size;
    public DockLayout(float[] scales, float center, boolean offhand, boolean leftHand, boolean indicator,
                      float requestedSize, int width, float availableHeight, boolean frames, boolean shelf, float shadow, float pop) {
        int k = Math.clamp((int) center, 0, 8);
        float f = Math.clamp(center - k, 0, 1);
        x[k] = -80 + 20 * center - f * 10 * (scales[k] + scales[Math.min(k + 1, 8)]);
        for (int i = k + 1; i < 9; i++) x[i] = x[i - 1] + 10 * (scales[i - 1] + scales[i]);
        for (int i = k - 1; i >= 0; i--) x[i] = x[i + 1] - 10 * (scales[i + 1] + scales[i]);
        float mid = (x[0] - 10 * scales[0] + x[8] + 10 * scales[8]) / 2;
        for (int i = 0; i < 9; i++) x[i] -= mid;
        x[9] = leftHand ? x[0] - 10 * scales[0] - 14.5f * scales[9]
                : x[8] + 10 * scales[8] + 14.5f * scales[9];
        left = x[0] - 10 * scales[0] - 1;
        right = x[8] + 10 * scales[8] + 1;
        float extent = Math.max(-left, right) + (shelf ? Math.max(1, 7 * shadow) : 1);
        float highest = shelf ? 22 + 7 * shadow : 0;
        float lowest = shelf ? 7 * shadow : 0;
        for (int i = 0; i < 9; i++) {
            extent = Math.max(extent, Math.abs(x[i]) + 13 * scales[i] + 7);
            highest = Math.max(highest, 3 + Math.max(frames ? 20 : 16, 16 + 1.2f * pop) * scales[i]);
            // The floating selection includes a closing row below the vanilla 23-row sprite.
            lowest = Math.max(lowest, -3 + (frames ? 4 : 2) * scales[i]);
        }
        if (offhand) {
            extent = Math.max(extent, Math.abs(x[9]) + 18 * scales[9] + 7);
            highest = Math.max(highest, frames ? 24 * scales[9] : 3 + (16 + 1.2f * pop) * scales[9]);
        }
        if (indicator) extent = Math.max(extent, Math.max(-left, right) + 26);
        top = -highest;
        bottom = Math.max(0, lowest);
        size = Math.min(requestedSize, Math.min(Math.max(1, width - 8) / (2 * extent),
                Math.max(1, availableHeight) / Math.max(1, highest + bottom)));
    }
    public static float curve(float distance, float radius) {
        return (float) Math.cos(Math.min(distance / radius, 1) * Math.PI / 2);
    }
}
