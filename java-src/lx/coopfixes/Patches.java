package lx.coopfixes;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.ai.states.LungeState;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.vehicles.BaseVehicle;
import zombie.worldMap.WorldMapVisited;

/** Patches de ZombieBuddy. Los advices solo delegan en Guards (codigo con try/catch). */
public final class Patches {
    private Patches() {}

    @Patch(className = "zombie.vehicles.BaseVehicle", methodName = "update", strictMatch = true)
    public static class VehicleUpdate {
        @Patch.OnEnter(skipOn = true)
        public static boolean onEnter(@Patch.This(readOnly = true) BaseVehicle self) {
            return Guards.vehicleChunk(self);
        }
    }

    /** El juego solo actualiza el reticulo (circulo verde / blanco) del jugador 1: ver OtherReticles. */
    @Patch(className = "zombie.CombatManager", methodName = "update")
    public static class CombatReticleAllPlayers {
        @Patch.OnExit
        public static void onExit(@Patch.This(readOnly = true) zombie.CombatManager self,
                                  @Patch.Argument(value = 0, readOnly = true) boolean doUpdate) {
            OtherReticles.update(self);
        }
    }

    /** Balistica del jugador 2 calculada con su propia camara (ver CurrentPlayerScope). */
    @Patch(className = "zombie.characters.IsoGameCharacter", methodName = "updateBallistics", strictMatch = true)
    public static class BallisticsOwnCamera {
        @Patch.OnEnter
        public static void onEnter(@Patch.This(readOnly = true) IsoGameCharacter self,
                                   @Patch.Local("lxcfPrev") Object prev) {
            prev = CurrentPlayerScope.push(self);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void onExit(@Patch.Local("lxcfPrev") Object prev) {
            CurrentPlayerScope.pop(prev);
        }
    }

    /** Mirada de la cabeza al apuntar, con la camara del propio jugador. */
    @Patch(className = "zombie.characters.IsoPlayer", methodName = "updateInternal2", strictMatch = true)
    public static class PlayerUpdateOwnCamera {
        @Patch.OnEnter
        public static void onEnter(@Patch.This(readOnly = true) IsoPlayer self,
                                   @Patch.Local("lxcfPrev") Object prev) {
            prev = CurrentPlayerScope.push(self);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void onExit(@Patch.Local("lxcfPrev") Object prev) {
            CurrentPlayerScope.pop(prev);
        }
    }

    /**
     * Minimapa/mapa: la niebla de zonas no visitadas se sube a la GPU por filas cambiadas en vez
     * de la textura completa (64 MB en esta partida) cada vez que alguien explora. Ver VisitedTexture.
     */
    @Patch(className = "zombie.worldMap.WorldMapVisited", methodName = "updateTextureData", warmUp = true)
    public static class VisitedRows {
        @Patch.OnEnter
        public static void onEnter(@Patch.This(readOnly = true) WorldMapVisited self,
                                   @Patch.Local("lxcfRows") long rows) {
            rows = VisitedTexture.beforeUpdateData(self);
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void onExit(@Patch.Local("lxcfRows") long rows) {
            VisitedTexture.afterUpdateData(rows);
        }
    }

    @Patch(className = "zombie.worldMap.WorldMapVisited", methodName = "updateVisitedTexture", warmUp = true)
    public static class VisitedUpload {
        @Patch.OnEnter(skipOn = true)
        public static boolean onEnter(@Patch.This(readOnly = true) WorldMapVisited self) {
            return VisitedTexture.upload(self);
        }
    }

    @Patch(className = "zombie.iso.objects.IsoTree", methodName = "isPlayerInsideARoom", warmUp = true)
    public static class TreeInsideRoom {
        @Patch.OnEnter(skipOn = true)
        public static boolean onEnter(@Patch.Argument(value = 0, readOnly = true) IsoPlayer player) {
            return Guards.treeNoRoom(player);
        }
    }

    /**
     * Texture.getSharedTexture(null): vanilla (ISBuildAction.lua:140 al construir con mando)
     * pide una textura con nombre null; el juego atrapa el NPE, lo loguea y devuelve null.
     * Devolvemos null directamente, sin la excepcion en el log. Mismo resultado.
     */
    @Patch(className = "zombie.core.textures.Texture", methodName = "getSharedTexture")
    public static class SharedTextureNull {
        @Patch.OnEnter(skipOn = true)
        public static boolean onEnter(@Patch.Argument(value = 0, readOnly = true) String name) {
            return name == null;
        }
    }

    @Patch(className = "zombie.ai.states.LungeState", methodName = "execute", warmUp = true)
    public static class LungeExecute {
        @Patch.OnEnter
        public static void onEnter(@Patch.Argument(value = 0, readOnly = true) IsoGameCharacter owner) {
            if (owner instanceof IsoZombie) {
                Guards.lungeVector((IsoZombie) owner);
            }
        }
    }
}
