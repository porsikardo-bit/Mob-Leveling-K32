package net.mcreator.mobleveling;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.api.distmarker.Dist;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Solo cliente. Oculta la etiqueta "[Nv. X]" cuando el mob esta TOTALMENTE tapado por bloques.
 * Si alguna parte del mob se ve (por ejemplo, un bloque tapa solo la mitad de arriba o de abajo),
 * la etiqueta se sigue mostrando. Los mobs fuera de tu campo de vision ya no se dibujan.
 */
@Mod.EventBusSubscriber(modid = "mob_leveling", value = Dist.CLIENT)
public class MobLabelClient {

	// ===================== CONFIGURACION =====================
	private static final int CHECK_INTERVAL_TICKS = 5; // cada cuanto se recalcula la visibilidad de un mob (20 ticks = 1 s)
	private static final int GRACE_TICKS = 6;          // tiempo extra que se mantiene la etiqueta al perder de vista (evita parpadeos)
	// =========================================================

	private static final String NAME_KEY = "mob_leveling.mob_level";
	// id del mob -> [ultima comprobacion, ultima vez que se vio]
	private static final Map<Integer, long[]> CACHE = new HashMap<>();

	@SubscribeEvent
	public static void onRenderNameTag(RenderNameTagEvent event) {
		Entity entity = event.getEntity();
		if (!isLevelLabel(entity))
			return;
		if (!isVisible(entity))
			event.setResult(Event.Result.DENY);
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel().isClientSide())
			CACHE.clear();
	}

	// Solo afecta a los mobs que llevan nuestra etiqueta, no a otros nombres de otros mods
	private static boolean isLevelLabel(Entity entity) {
		Component name = entity.getCustomName();
		return name != null && name.getContents() instanceof TranslatableContents contents && NAME_KEY.equals(contents.getKey());
	}

	private static boolean isVisible(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		Level level = mc.level;
		if (level == null || mc.player == null)
			return true;

		long now = level.getGameTime();
		if (CACHE.size() > 256) {
			CACHE.entrySet().removeIf(e -> now - e.getValue()[0] > 100);
		}
		long[] entry = CACHE.computeIfAbsent(entity.getId(), id -> new long[]{-1000, -1000});

		if (now - entry[0] >= CHECK_INTERVAL_TICKS || now < entry[0]) {
			entry[0] = now;
			if (canSeeAnyPart(mc, entity))
				entry[1] = now;
		}
		return now - entry[1] <= GRACE_TICKS;
	}

	// Traza rayos desde la camara hacia varios puntos del cuerpo del mob (centro a 3 alturas y 4 esquinas).
	// Con que un solo rayo llegue sin chocar con un bloque, se considera visible.
	private static boolean canSeeAnyPart(Minecraft mc, Entity entity) {
		Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
		AABB box = entity.getBoundingBox();
		double cx = (box.minX + box.maxX) / 2.0;
		double cz = (box.minZ + box.maxZ) / 2.0;
		double h = box.getYsize();
		double dx = box.getXsize() * 0.35;
		double dz = box.getZsize() * 0.35;

		if (clear(mc, eye, cx, box.minY + h * 0.5, cz))
			return true;
		if (clear(mc, eye, cx, box.minY + h * 0.9, cz))
			return true;
		if (clear(mc, eye, cx, box.minY + h * 0.1, cz))
			return true;
		if (clear(mc, eye, cx - dx, box.minY + h * 0.9, cz - dz))
			return true;
		if (clear(mc, eye, cx + dx, box.minY + h * 0.9, cz + dz))
			return true;
		if (clear(mc, eye, cx - dx, box.minY + h * 0.1, cz + dz))
			return true;
		return clear(mc, eye, cx + dx, box.minY + h * 0.1, cz - dz);
	}

	// VISUAL: los cristales no bloquean la vista, los bloques solidos si
	private static boolean clear(Minecraft mc, Vec3 eye, double x, double y, double z) {
		ClipContext context = new ClipContext(eye, new Vec3(x, y, z), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player);
		return mc.level.clip(context).getType() == HitResult.Type.MISS;
	}
}
