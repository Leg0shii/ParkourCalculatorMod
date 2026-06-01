package de.legoshi.parkourcalc.core.render;

import de.legoshi.parkourcalc.core.ports.BoxRenderer;

/** Emits the cached path geometry: faces (triangles) and lines (edges) into separate buffers. */
public interface PathGeometrySource {
    void emitFaces(BoxRenderer faces);

    void emitLines(BoxRenderer lines);
}
