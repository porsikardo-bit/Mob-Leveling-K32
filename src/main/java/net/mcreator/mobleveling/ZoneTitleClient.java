package net.mcreator.mobleveling;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.api.distmarker.Dist;

import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.Minecraft;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Dibuja el titulo y subtitulo de zona / aviso de dias (solo en el cliente).
 */
@Mod.EventBusSubscriber(modid = "mob_leveling", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ZoneTitleClient {

	// ===================== CONFIGURACION =====================
	// Altura del titulo: 0.0 = borde superior, 0.5 = centro, 0.33 = un tercio de la pantalla
	private static final float POSITION = 0.28F;
	private static final long FADE_IN = 400;   // milisegundos
	private static final long STAY = 2600;
	private static final long FADE_OUT = 800;
	// =========================================================

	private static Component title;
	private static Component subtitle;
	private static long startTime;

	public static void show(Component newTitle, Component newSubtitle) {
		title = newTitle;
		subtitle = newSubtitle;
		startTime = System.currentTimeMillis();
	}

	@SubscribeEvent
	public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
		event.registerAboveAll("zone_title", (gui, graphics, partialTick, width, height) -> render(graphics, width, height));
	}

	private static void render(GuiGraphics graphics, int width, int height) {
		if (title == null)
			return;
		long elapsed = System.currentTimeMillis() - startTime;
		if (elapsed >= FADE_IN + STAY + FADE_OUT) {
			title = null;
			return;
		}
		float alpha;
		if (elapsed < FADE_IN)
			alpha = elapsed / (float) FADE_IN;
		else if (elapsed > FADE_IN + STAY)
			alpha = 1.0F - (elapsed - FADE_IN - STAY) / (float) FADE_OUT;
		else
			alpha = 1.0F;
		int a = (int) (alpha * 255.0F);
		if (a < 8)
			return;
		int color = (a << 24) | 0xFFFFFF;

		Font font = Minecraft.getInstance().font;
		PoseStack pose = graphics.pose();
		pose.pushPose();
		pose.translate(width / 2.0F, height * POSITION, 0.0F);

		pose.pushPose();
		pose.scale(4.0F, 4.0F, 4.0F);
		graphics.drawString(font, title, -font.width(title) / 2, -10, color, true);
		pose.popPose();

		if (subtitle != null) {
			pose.pushPose();
			pose.scale(2.0F, 2.0F, 2.0F);
			graphics.drawString(font, subtitle, -font.width(subtitle) / 2, 5, color, true);
			pose.popPose();
		}
		pose.popPose();
	}
}
