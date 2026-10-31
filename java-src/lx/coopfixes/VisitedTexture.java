package lx.coopfixes;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import zombie.core.textures.TextureID;
import zombie.worldMap.WorldMapVisited;

/**
 * Tirones con el minimapa / mapa abierto (sobre todo en pantalla dividida, explorando).
 *
 * WorldMapVisited guarda la "niebla" de zonas no visitadas en UNA textura que cubre todo el
 * mundo cargado. Con mapas modded lejanos (Fort Benning, Trelai...) el mundo de esta partida
 * mide 501x501 celdas -> textura de 4096x4096 RGBA = 64 MB. Cada vez que un jugador entra en
 * un bloque de 32x32 casillas sin explorar, el juego vuelve a subir los 64 MB COMPLETOS con
 * glTexImage2D (updateVisitedTexture). Con dos jugadores explorando pasa cada pocos segundos,
 * y en una GT 610 cada subida congela el cuadro.
 *
 * Arreglo: anotamos que filas cambiaron (updateTextureData, hilo principal) y en updateVisitedTexture
 * (hilo de render) subimos solo esas filas con glTexSubImage2D (unos KB en vez de 64 MB).
 *
 * Seguridad: ante cualquier duda (primera subida de cada textura, muchas filas, campos que no
 * existen en otra version del juego, cualquier excepcion) devolvemos false y el juego hace su
 * subida completa original. Mismo resultado visual, solo cambia cuanto se copia.
 */
public final class VisitedTexture {
    private VisitedTexture() {}

    /** Valor de "nada que anotar" para el @Local de updateTextureData. */
    public static final long NONE = Long.MIN_VALUE;

    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_RGBA = 6408;
    private static final int GL_UNSIGNED_BYTE = 5121;
    /** WorldMapVisited.TEXTURE_PAD: la fila de la textura es 1 + fila de datos. */
    private static final int PAD = 1;

    private static Field fChanged, fChangeY1, fChangeY2, fTextureId, fTextureW, fTextureH, fTextureBuffer;
    private static volatile boolean disabled;
    private static volatile boolean dataHookSeen;
    /** Algo fallo al anotar filas: la proxima subida la hace el juego completa. */
    private static volatile boolean forceFull;
    private static int errors;
    private static boolean announced;

    private static final Object LOCK = new Object();
    // filas de la TEXTURA pendientes de subir (protegidas por LOCK)
    private static int dirtyY1 = Integer.MAX_VALUE;
    private static int dirtyY2 = Integer.MIN_VALUE;
    /** TextureID que ya recibio su subida completa inicial (solo hilo de render). */
    private static Object fullTexture;

    static {
        try {
            Class<?> c = WorldMapVisited.class;
            fChanged = field(c, "changed", boolean.class);
            fChangeY1 = field(c, "changeY1", int.class);
            fChangeY2 = field(c, "changeY2", int.class);
            fTextureId = field(c, "textureId", TextureID.class);
            fTextureW = field(c, "textureW", int.class);
            fTextureH = field(c, "textureH", int.class);
            fTextureBuffer = field(c, "textureBuffer", ByteBuffer.class);
        } catch (Throwable t) {
            disabled = true;
            System.err.println("[LX_CoopFixes] mapa: no se pudo preparar la subida parcial de la niebla ("
                    + t + "); se usa la del juego");
        }
    }

    private static Field field(Class<?> c, String name, Class<?> type) throws NoSuchFieldException {
        Field f = c.getDeclaredField(name);
        if (f.getType() != type) {
            throw new NoSuchFieldException(name + " es " + f.getType().getName() + ", se esperaba " + type.getName());
        }
        f.setAccessible(true);
        return f;
    }

    /** Hilo principal, al entrar en updateTextureData: que filas va a reescribir. */
    public static long beforeUpdateData(WorldMapVisited self) {
        if (disabled || self == null) return NONE;
        try {
            dataHookSeen = true;
            if (!fChanged.getBoolean(self)) return NONE;
            int y1 = fChangeY1.getInt(self);
            int y2 = fChangeY2.getInt(self);
            if (y1 > y2) return NONE;
            return ((long) y1 << 32) | (y2 & 0xFFFFFFFFL);
        } catch (Throwable t) {
            fail("filas", t);
            return NONE;
        }
    }

    /** Hilo principal, al salir de updateTextureData: datos ya escritos y ANTES de que renderMain marque textureChanged. */
    public static void afterUpdateData(long packed) {
        if (packed == NONE) return;
        int y1 = (int) (packed >> 32) + PAD;
        int y2 = (int) packed + PAD;
        synchronized (LOCK) {
            if (y1 < dirtyY1) dirtyY1 = y1;
            if (y2 > dirtyY2) dirtyY2 = y2;
        }
    }

    /**
     * Hilo de render, en lugar de updateVisitedTexture.
     * @return true si ya subimos lo necesario (se salta el original); false = subida completa del juego.
     */
    public static boolean upload(WorldMapVisited self) {
        if (disabled || self == null || !dataHookSeen) return false;
        try {
            Object tex = fTextureId.get(self);
            if (!(tex instanceof TextureID)) return false;

            int y1, y2;
            synchronized (LOCK) {
                y1 = dirtyY1;
                y2 = dirtyY2;
                dirtyY1 = Integer.MAX_VALUE;
                dirtyY2 = Integer.MIN_VALUE;
            }
            // Primera subida de esta textura (partida nueva/recargada): la hace el juego, completa.
            if (tex != fullTexture) {
                fullTexture = tex;
                forceFull = false;
                return false;
            }
            // Alguna anotacion de filas fallo: no sabemos que cambio, subida completa del juego.
            if (forceFull) {
                forceFull = false;
                return false;
            }
            // Nada pendiente: esas filas ya se subieron en una pasada anterior.
            if (y1 > y2) return true;

            int w = fTextureW.getInt(self);
            int h = fTextureH.getInt(self);
            Object b = fTextureBuffer.get(self);
            if (!(b instanceof ByteBuffer) || w <= 0 || h <= 0) return false;
            ByteBuffer buf = (ByteBuffer) b;
            if (y1 < 0) y1 = 0;
            if (y2 > h - 1) y2 = h - 1;
            int rows = y2 - y1 + 1;
            if (rows <= 0) return true;
            if (rows * 2L > h) return false; // cambio enorme: mejor la subida completa
            long start = (long) y1 * w * 4L;
            long end = (long) (y2 + 1) * w * 4L;
            if (end > buf.capacity()) return false;

            // Vista propia del buffer: no tocamos position/limit del original (lo usa el hilo principal).
            ByteBuffer view = buf.duplicate();
            view.limit((int) end);
            view.position((int) start);
            ((TextureID) tex).bind();
            GL11.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, y1, w, rows, GL_RGBA, GL_UNSIGNED_BYTE, view);

            if (!announced) {
                announced = true;
                System.out.println("[LX_CoopFixes] mapa: niebla de zonas no visitadas actualizada por filas ("
                        + w + "x" + h + ", " + rows + " fila(s) en vez de la textura completa)");
            }
            return true;
        } catch (Throwable t) {
            fail("subida", t);
            return false;
        }
    }

    private static void fail(String where, Throwable t) {
        forceFull = true;
        int n = ++errors;
        if (n <= 3) {
            System.err.println("[LX_CoopFixes] mapa (" + where + "): " + t);
        }
        if (n >= 10) {
            disabled = true;
            System.err.println("[LX_CoopFixes] mapa: subida parcial desactivada por errores (el juego sigue con la suya)");
        }
    }
}
