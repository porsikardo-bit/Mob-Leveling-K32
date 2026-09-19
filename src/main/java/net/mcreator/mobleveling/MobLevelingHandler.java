package net.mcreator.mobleveling;

import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.Tags;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mob Leveling-K32 (Forge 1.20.1)
 *
 * Nivel = 1 + (zona * ZONE_BONUS) + bonus_por_dias + bonus_aleatorio   (maximo MAX_LEVEL)
 * Cada nivel por encima del 1 aumenta vida y dano en PERCENT_PER_LEVEL (10%).
 *
 * Toda la logica corre en el SERVIDOR (o servidor integrado / mundo LAN), asi que
 * funciona igual en solitario, en servidores dedicados y en mundos compartidos.
 * El dibujo de los titulos en pantalla lo hace ZoneTitleClient.
 */
@Mod.EventBusSubscriber(modid = "mob_leveling")
public class MobLevelingHandler {

	// ===================== CONFIGURACION =====================
	private static final double ZONE_SIZE = 1200;         // bloques por zona
	private static final int ZONE_BONUS = 3;              // niveles extra por zona
	private static final int ANNOUNCE_DAY = 3;            // dia del aviso; desde aqui empieza el bonus por dias
	private static final int DAYS_PER_LEVEL = 5;          // +1 nivel cada X dias (despues del dia de aviso)
	private static final int MAX_LEVEL = 100;
	private static final double PERCENT_PER_LEVEL = 0.10; // +10% de vida y dano por nivel

	// Tiempo minimo entre avisos de zona (evita repeticiones al caminar por el borde de una zona)
	private static final int ZONE_COOLDOWN_TICKS = 200;   // 200 ticks = 10 segundos

	// Niveles aleatorios extra: cada "tirada" exitosa suma +1 (hasta el maximo).
	// Con 0.40 -> +0: 60%, +1: 24%, +2: 9.6%, +3: 3.8%, +4: 1.5%, +5: 1.0%
	private static final int MAX_RANDOM_BONUS = 5;
	private static final double RANDOM_BONUS_CHANCE = 0.40;

	// Jefes (vanilla o de otros mods): 0 = no se tocan. 0.25 = reciben solo el 25% del bono.
	private static final double BOSS_SCALE = 0.0;
	// Cualquier mob con esta vida base o mas se considera jefe aunque el mod no lo etiquete.
	private static final double BOSS_HEALTH_THRESHOLD = 150;
	// Excluidos siempre. Agrega aqui los IDs de otros mods, ej: "modid:nombre_del_jefe"
	private static final Set<String> EXCLUDED = Set.of("minecraft:ender_dragon", "minecraft:wither", "minecraft:warden");
	// =========================================================

	private static final String TAG_LEVEL = "mob_leveling_level";
	private static final UUID HEALTH_ID = UUID.fromString("b3f1c2a4-7d5e-4c8a-9f21-5a6e0d3c1b01");
	private static final UUID DAMAGE_ID = UUID.fromString("b3f1c2a4-7d5e-4c8a-9f21-5a6e0d3c1b02");
	private static final Map<UUID, Integer> LAST_ZONE = new HashMap<>();
	private static final Map<UUID, Integer> LAST_ANNOUNCE = new HashMap<>();

	// ---------- Estado guardado en el mundo (aviso del dia 3 solo una vez) ----------
	public static class State extends SavedData {
		public boolean announced = false;

		public static State load(CompoundTag tag) {
			State s = new State();
			s.announced = tag.getBoolean("announced");
			return s;
		}

		@Override
		public CompoundTag save(CompoundTag tag) {
			tag.putBoolean("announced", announced);
			return tag;
		}
	}

	// ---------- Calculos ----------
	private static int zoneAt(ServerLevel level, double x, double z) {
		BlockPos spawn = level.getSharedSpawnPos();
		double dx = x - spawn.getX();
		double dz = z - spawn.getZ();
		return (int) Math.floor(Math.sqrt(dx * dx + dz * dz) / ZONE_SIZE);
	}

	private static long currentDay(MinecraftServer server) {
		return server.overworld().getDayTime() / 24000L + 1;
	}

	private static int computeLevel(ServerLevel level, double x, double z) {
		long day = currentDay(level.getServer());
		int dayBonus = day < ANNOUNCE_DAY ? 0 : 1 + (int) ((day - ANNOUNCE_DAY) / DAYS_PER_LEVEL);
		int lvl = 1 + zoneAt(level, x, z) * ZONE_BONUS + dayBonus;
		return Math.min(lvl, MAX_LEVEL);
	}

	private static boolean isBossLike(Mob mob) {
		if (mob.getType().is(Tags.EntityTypes.BOSSES))
			return true;
		ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
		if (id != null && EXCLUDED.contains(id.toString()))
			return true;
		return mob.getMaxHealth() >= BOSS_HEALTH_THRESHOLD;
	}

	// ---------- Avisos y zonas (se revisa 1 vez por segundo, no cada tick) ----------
	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END)
			return;
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null || server.getTickCount() % 20 != 0)
			return;

		// Aviso del dia 3 (solo una vez por mundo)
		State state = server.overworld().getDataStorage().computeIfAbsent(State::load, State::new, "mob_leveling_state");
		long day = currentDay(server);
		if (!state.announced && day >= ANNOUNCE_DAY) {
			state.announced = true;
			state.setDirty();
			// En un mundo que ya paso del dia 3 al instalar el mod, se avisa sin el sonido fuerte del Wither
			boolean withSound = day == ANNOUNCE_DAY;
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				ZoneTitleNetwork.send(p, Component.translatableWithFallback("mob_leveling.days_title", "THE WORLD GROWS HARDER").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
						Component.translatableWithFallback("mob_leveling.days_subtitle", "Monsters will begin to level up").withStyle(ChatFormatting.GRAY));
				if (withSound)
					p.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 1.0F, 1.0F);
			}
		}

		// Anuncio de zona al entrar / volver a una zona
		int now = server.getTickCount();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			UUID id = p.getUUID();
			int zone = zoneAt(p.serverLevel(), p.getX(), p.getZ());
			Integer previous = LAST_ZONE.get(id);
			if (previous == null) {
				LAST_ZONE.put(id, zone);
				LAST_ANNOUNCE.put(id, now);
				announceZone(p, zone, false);
			} else if (previous != zone) {
				Integer last = LAST_ANNOUNCE.get(id);
				if (last == null || now - last >= ZONE_COOLDOWN_TICKS) {
					LAST_ZONE.put(id, zone);
					LAST_ANNOUNCE.put(id, now);
					announceZone(p, zone, true);
				}
			}
		}
	}

	private static void announceZone(ServerPlayer p, int zone, boolean withSound) {
		Component title = zone == 0
				? Component.translatableWithFallback("mob_leveling.zone_initial", "STARTING ZONE").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
				: Component.translatableWithFallback("mob_leveling.zone", "ZONE %s", zone).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
		Component subtitle = Component.translatableWithFallback("mob_leveling.zone_subtitle", "Base monster level: %s", Math.min(1 + zone * ZONE_BONUS, MAX_LEVEL)).withStyle(ChatFormatting.GOLD);
		ZoneTitleNetwork.send(p, title, subtitle);
		if (withSound)
			p.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.6F, 1.0F);
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		LAST_ZONE.remove(event.getEntity().getUUID());
		LAST_ANNOUNCE.remove(event.getEntity().getUUID());
	}

	// Importante en mundos de un jugador / LAN: el servidor integrado se reinicia dentro del mismo juego.
	@SubscribeEvent
	public static void onServerStopped(ServerStoppedEvent event) {
		LAST_ZONE.clear();
		LAST_ANNOUNCE.clear();
	}

	// ---------- Nivelar mobs al aparecer ----------
	@SubscribeEvent
	public static void onMobJoin(EntityJoinLevelEvent event) {
		if (!(event.getLevel() instanceof ServerLevel level))
			return;
		if (!(event.getEntity() instanceof Mob mob))
			return;
		// Compatible con otros mods: cualquier Enemy o mob de categoria MONSTER
		if (!(mob instanceof Enemy) && mob.getType().getCategory() != MobCategory.MONSTER)
			return;

		// Si ya tiene nivel (recarga de chunk), no se vuelve a nivelar
		CompoundTag data = mob.getPersistentData();
		if (data.contains(TAG_LEVEL))
			return;

		boolean boss = isBossLike(mob);
		if (boss && BOSS_SCALE <= 0)
			return; // los jefes se dejan tal como los penso su creador

		// Bonus aleatorio: cada nivel extra es menos probable que el anterior
		int extra = 0;
		while (extra < MAX_RANDOM_BONUS && mob.getRandom().nextDouble() < RANDOM_BONUS_CHANCE)
			extra++;

		int lvl = Math.min(computeLevel(level, mob.getX(), mob.getZ()) + extra, MAX_LEVEL);
		data.putInt(TAG_LEVEL, lvl);

		double bonus = PERCENT_PER_LEVEL * (lvl - 1) * (boss ? BOSS_SCALE : 1.0);
		if (bonus > 0) {
			applyBonus(mob, Attributes.MAX_HEALTH, HEALTH_ID, bonus);
			applyBonus(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, bonus);
			mob.setHealth(mob.getMaxHealth());
		}

		// Los jefes conservan su nombre original
		if (!boss) {
			mob.setCustomName(Component.translatableWithFallback("mob_leveling.mob_level", "[Lv. %s]", lvl).withStyle(ChatFormatting.RED).append(Component.literal(" "))
					.append(mob.getName().copy().withStyle(ChatFormatting.WHITE)));
			mob.setCustomNameVisible(true);
		}
	}

	private static void applyBonus(Mob mob, Attribute attribute, UUID id, double amount) {
		AttributeInstance inst = mob.getAttribute(attribute);
		if (inst == null || inst.getModifier(id) != null)
			return;
		inst.addPermanentModifier(new AttributeModifier(id, "mob_leveling", amount, AttributeModifier.Operation.MULTIPLY_BASE));
	}
}
