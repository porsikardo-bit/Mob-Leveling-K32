package net.mcreator.mobleveling;

import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;

import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * Envia al cliente el titulo/subtitulo para que el mod lo dibuje (ver ZoneTitleClient).
 * Asi podemos colocarlo mas arriba que el titulo normal de Minecraft, que no se puede mover.
 */
@Mod.EventBusSubscriber(modid = "mob_leveling", bus = Mod.EventBusSubscriber.Bus.MOD)
public class ZoneTitleNetwork {
	private static final String PROTOCOL = "1";
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation("mob_leveling", "zone_title"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

	@SubscribeEvent
	public static void onCommonSetup(FMLCommonSetupEvent event) {
		CHANNEL.registerMessage(0, Message.class, Message::encode, Message::decode, Message::handle);
	}

	public static void send(ServerPlayer player, Component title, Component subtitle) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Message(title, subtitle));
	}

	public static class Message {
		private final Component title;
		private final Component subtitle;

		public Message(Component title, Component subtitle) {
			this.title = title;
			this.subtitle = subtitle;
		}

		public static void encode(Message message, FriendlyByteBuf buffer) {
			buffer.writeComponent(message.title);
			buffer.writeComponent(message.subtitle);
		}

		public static Message decode(FriendlyByteBuf buffer) {
			return new Message(buffer.readComponent(), buffer.readComponent());
		}

		public static void handle(Message message, Supplier<NetworkEvent.Context> contextSupplier) {
			NetworkEvent.Context context = contextSupplier.get();
			context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ZoneTitleClient.show(message.title, message.subtitle)));
			context.setPacketHandled(true);
		}
	}
}
