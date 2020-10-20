package capsule.network.server;

import capsule.items.CapsuleItem;
import capsule.network.LabelEditedMessageToServer;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.ServerWorld;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;

/**
 * The MessageHandlerOnServer is used to process the network message once it has arrived on the Server side.
 * WARNING!  In 1.8 the MessageHandler now runs in its own thread.  This means that if your onMessage code
 * calls any vanilla objects, it may cause crashes or subtle problems that are hard to reproduce.
 * Your onMessage handler should create a task which is later executed by the client or server thread as
 * appropriate - see below.
 * User: The Grey Ghost
 * Date: 15/01/2015
 */
public class LabelEditedMessageToServerHandler implements IMessageHandler<LabelEditedMessageToServer, IMessage> {

    protected static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger(LabelEditedMessageToServerHandler.class);

    /**
     * Called when a message is received of the appropriate type.
     * CALLED BY THE NETWORK THREAD
     *
     * @param message The message
     */
    public IMessage onMessage(final LabelEditedMessageToServer message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            LOGGER.error("LabelEditedMessageToServer received on wrong side:" + ctx.side);
            return null;
        }
        if (!message.isMessageValid()) {
            LOGGER.error("LabelEditedMessageToServer was invalid" + message.toString());
            return null;
        }

        // we know for sure that this handler is only used on the server side, so it is ok to assume
        //  that the ctx handler is a serverhandler, and that ServerWorld exists.
        // Packets received on the client side must be handled differently!  See MessageHandlerOnClient

        final ServerPlayerEntity sendingPlayer = ctx.getServerHandler().player;
        if (sendingPlayer == null) {
            LOGGER.error("ServerPlayerEntity was null when LabelEditedMessageToServer was received");
            return null;
        }

        // This code creates a new task which will be executed by the server during the next tick,
        //  for example see MinecraftServer.updateTimeLightAndEntities(), just under section
        //      this.theProfiler.startSection("jobs");
        //  In this case, the task is to call messageHandlerOnServer.processMessage(message, sendingPlayer)
        final ServerWorld playerServerWorld = sendingPlayer.getServerWorld();
        playerServerWorld.addScheduledTask(() -> processMessage(message, sendingPlayer));

        return null;
    }

    // This message is called from the Server thread.
    //   It spawns a random number of the given projectile at a position above the target location
    void processMessage(LabelEditedMessageToServer message, ServerPlayerEntity sendingPlayer) {
        ItemStack serverStack = sendingPlayer.getHeldItemMainhand();
        if (serverStack.getItem() instanceof CapsuleItem) {
            // of the player didn't swap item during ui opening
            CapsuleItem.setLabel(serverStack, message.getLabel());
        }
    }
}