package lx.coopfixes;

import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.Vector2;
import zombie.vehicles.BaseVehicle;

/**
 * Guardas minimas contra bugs del juego (42.21) que aparecen en tus logs.
 * Cada guarda solo actua en el caso exacto que hace fallar al juego; en el resto
 * devuelve "no intervenir" y el codigo vanilla corre igual que siempre.
 * Nunca lanza excepciones (todo envuelto en try/catch).
 */
public final class Guards {
    private Guards() {}

    private static int vehicleFixes = 0;

    private static void note(String msg) {
        System.out.println("[LX_CoopFixes] " + msg);
    }

    /**
     * BaseVehicle.update(): si this.chunk es null (el chunk anterior se descargo; pasa en
     * pantalla dividida al manejar) vanilla hace this.chunk.vehicles.remove(this) -> NPE y
     * el juego se cierra / se cuelga. Aca re-registramos el vehiculo en el chunk donde esta
     * ahora (lo mismo que haria vanilla un instante despues). Si no hay chunk cargado bajo el
     * vehiculo, se saltea su update SOLO este tick.
     *
     * @return true = saltear el update de este tick.
     */
    public static boolean vehicleChunk(BaseVehicle v) {
        try {
            if (v == null || v.chunk != null) return false;
            IsoGridSquare sq = v.getCurrentSquare();
            IsoChunk ch = sq == null ? null : sq.getChunk();
            if (ch == null) return true;
            if (!ch.vehicles.contains(v)) ch.vehicles.add(v);
            v.chunk = ch;
            IsoChunk.addFromCheckedVehicles(v);
            if (vehicleFixes++ < 20) note("vehiculo sin chunk re-registrado (evitado crash BaseVehicle.update)");
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * IsoTree.isPlayerInsideARoom(): vanilla hace player.getSquare().getRoom().getRectsBounds()
     * sin chequear null -> NPE cada frame en algunas casas (Rosewood, Fort Benning), miles de
     * lineas de error en el log. Si no hay habitacion, la respuesta correcta es "false".
     *
     * @return true = saltear (el metodo devuelve false).
     */
    public static boolean treeNoRoom(IsoPlayer player) {
        try {
            if (player == null) return true;
            IsoGridSquare sq = player.getSquare();
            return sq == null || sq.getRoom() == null;
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * LungeState.execute(): si el zombie esta exactamente sobre su objetivo, el vector al
     * objetivo es (0,0) y 42.21 lanza "Forward Direction cannot be zero length vector".
     * Usamos la direccion actual del zombie (no cambia hacia donde mira) y el estado sigue.
     */
    public static void lungeVector(IsoZombie z) {
        try {
            if (z == null) return;
            Vector2 v = z.vectorToTarget;
            if (v == null) return;
            if (v.x * v.x + v.y * v.y > 1.0E-8F) return;
            Vector2 f = z.getForwardDirection();
            float fx = f == null ? 0F : f.x;
            float fy = f == null ? 0F : f.y;
            if (fx * fx + fy * fy <= 1.0E-8F) { fx = 1F; fy = 0F; }
            v.x = fx;
            v.y = fy;
        } catch (Throwable t) {
            // nada
        }
    }
}
