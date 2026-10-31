package lx.coopfixes;

import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;

/**
 * Bug de 42.21 en pantalla dividida: varias funciones de apuntado convierten la posicion
 * del reticulo a coordenadas del mundo con IsoUtils.XToIso(x, y, z) (version de 3
 * argumentos), que usa la camara del "jugador actual" GLOBAL (IsoPlayer.instance) y no
 * la del duenio del arma:
 *   - BallisticsController.update() -> posicion de apuntado y rayo balistico (circulo
 *     verde, a donde apunta el arma, seguimiento del blanco).
 *   - IsoPlayer.updateInternal2() -> hacia donde mira la cabeza al apuntar.
 * Para el jugador 2 eso usaba la camara del jugador 1: el rayo nunca encontraba al zombie.
 *
 * Mientras corren esos metodos fijamos el "jugador actual" al jugador que se esta
 * actualizando y lo restauramos al salir (tambien si hay excepcion). Si ya era ese
 * jugador (el caso normal del jugador 1) no se toca nada.
 */
public final class CurrentPlayerScope {
    private CurrentPlayerScope() {}

    /** Token: no hubo cambio. */
    public static final Object NONE = new Object();
    /** Token: habia que restaurar a null. */
    public static final Object WAS_NULL = new Object();

    private static int swaps = 0;

    public static Object push(IsoGameCharacter chr) {
        try {
            if (!(chr instanceof IsoPlayer)) return NONE;
            IsoPlayer p = (IsoPlayer) chr;
            if (!p.isLocalPlayer()) return NONE;
            IsoPlayer prev = IsoPlayer.getInstance();
            if (prev == p) return NONE;
            IsoPlayer.setInstance(p);
            if (swaps < 3) {
                swaps++;
                System.out.println("[LX_CoopFixes] apuntado del jugador " + (p.getIndex() + 1)
                        + " calculado con su propia camara (fix split-screen activo)");
            }
            return prev == null ? WAS_NULL : prev;
        } catch (Throwable t) {
            return NONE;
        }
    }

    public static void pop(Object token) {
        try {
            if (token == null || token == NONE) return;
            IsoPlayer.setInstance(token == WAS_NULL ? null : (IsoPlayer) token);
        } catch (Throwable t) {
            // nada
        }
    }
}
