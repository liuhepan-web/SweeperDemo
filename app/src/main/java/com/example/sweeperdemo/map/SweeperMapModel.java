package com.example.sweeperdemo.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Renderable occupancy grid plus optional path overlays.
 */
public final class SweeperMapModel {

    public final int width;
    public final int height;
    public final int originX;
    public final int originY;
    public final int pileX;
    public final int pileY;
    public final boolean hasPile;
    public final byte[] pixels;
    public final List<float[]> path;
    public final List<float[]> navPath;
    public final String hint;

    /**
     * Create a map model.
     *
     * @param width   grid width
     * @param height  grid height
     * @param originX map origin x
     * @param originY map origin y
     * @param pileX   dock x
     * @param pileY   dock y
     * @param hasPile whether dock is valid
     * @param pixels  occupancy bytes, length width * height
     * @param path    cleaning path points in map pixels
     * @param navPath navigation path points
     * @param hint    short source description
     */
    public SweeperMapModel(int width, int height, int originX, int originY,
            int pileX, int pileY, boolean hasPile, byte[] pixels,
            List<float[]> path, List<float[]> navPath, String hint) {
        this.width = width;
        this.height = height;
        this.originX = originX;
        this.originY = originY;
        this.pileX = pileX;
        this.pileY = pileY;
        this.hasPile = hasPile;
        this.pixels = pixels;
        this.path = path == null ? Collections.emptyList() : path;
        this.navPath = navPath == null ? Collections.emptyList() : navPath;
        this.hint = hint == null ? "" : hint;
    }

    /**
     * Whether occupancy pixels can be drawn.
     *
     * @return true if grid is valid
     */
    public boolean hasGrid() {
        return width > 0 && height > 0 && pixels != null && pixels.length >= width * height;
    }

    /**
     * Merge a new path onto the current occupancy grid.
     *
     * @param newPath cleaning path
     * @param isNav   whether this is a navigation path
     * @return new model
     */
    public SweeperMapModel withPath(List<float[]> newPath, boolean isNav) {
        List<float[]> nextPath = isNav ? this.path : copy(newPath);
        List<float[]> nextNav = isNav ? copy(newPath) : this.navPath;
        return new SweeperMapModel(width, height, originX, originY, pileX, pileY, hasPile,
                pixels, nextPath, nextNav, hint);
    }

    /**
     * Replace occupancy while keeping existing paths if sizes still match.
     *
     * @param grid occupancy model
     * @return merged model
     */
    public static SweeperMapModel merge(SweeperMapModel previous, SweeperMapModel grid) {
        if (grid == null) {
            return previous;
        }
        if (previous == null) {
            return grid;
        }
        List<float[]> path = grid.path.isEmpty() ? previous.path : grid.path;
        List<float[]> nav = grid.navPath.isEmpty() ? previous.navPath : grid.navPath;
        return new SweeperMapModel(grid.width, grid.height, grid.originX, grid.originY,
                grid.pileX, grid.pileY, grid.hasPile, grid.pixels, path, nav, grid.hint);
    }

    private static List<float[]> copy(List<float[]> src) {
        if (src == null || src.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(src);
    }
}
