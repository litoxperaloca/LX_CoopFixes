package lx.coopfixes;

import zombie.CombatManager;
import zombie.characters.IsoPlayer;

/**
 * Bug de 42.21 en pantalla dividida: CombatManager.update() solo hace
 *     updateReticle(IsoPlayer.players[0])
 * es decir, solo el jugador 1 recalcula sus blancos al apuntar. Para el jugador 2:
 *   - el circulo del centro nunca se pone verde (IsoReticle.hasTarget),
 *   - no se actualizan el % de acierto ni los colores del reticulo,
 *   - nunca se marca "tiene blanco" (updateHasTargetFlag), asi que nunca entra en el modo
 *     RANGED_PRECISE_TARGET_FOUND, que es el que hace que el reticulo se "pegue" al zombie.
 * Aca hacemos lo mismo para los jugadores 2..4. updateReticle() es barato si el jugador no
 * esta apuntando con un arma de fuego (sale en las primeras comprobaciones).
 */
public final class OtherReticles {
    private OtherReticles() {}

    private static int errors = 0;
    private static boolean disabled = false;

    public static void update(CombatManager cm) {
        if (disabled || cm == null) return;
        int n = Math.min(IsoPlayer.numPlayers, 4);
        for (int i = 1; i < n; ++i) {
            IsoPlayer p = IsoPlayer.players[i];
            if (p == null) continue;
            Object token = CurrentPlayerScope.NONE;
            try {
                if (p.isDead() || !p.isLocalPlayer() || !p.isWeaponReady()) continue;
                token = CurrentPlayerScope.push(p);
                cm.updateReticle(p);
            } catch (Throwable t) {
                if (++errors <= 3) {
                    System.err.println("[LX_CoopFixes] updateReticle jugador " + (i + 1) + ": " + t);
                }
                if (errors >= 30) {
                    disabled = true;
                    System.err.println("[LX_CoopFixes] reticulo de jugadores 2-4 desactivado por errores (el juego sigue normal)");
                }
            } finally {
                CurrentPlayerScope.pop(token);
            }
        }
    }
}
