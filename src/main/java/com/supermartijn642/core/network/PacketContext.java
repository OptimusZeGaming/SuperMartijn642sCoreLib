package com.supermartijn642.core.network;

import com.supermartijn642.core.ClientUtils;
import com.supermartijn642.core.CoreSide;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Created 5/30/2021 by SuperMartijn642
 */
public class PacketContext {

    private MessageContext context;

    public PacketContext(MessageContext context){
        this.context = context;
    }

    /**
     * @return the side the packet is received on
     */
    public CoreSide getHandlingSide(){
        return this.context.side == Side.CLIENT ? CoreSide.CLIENT : CoreSide.SERVER;
    }

    /**
     * @return the side the packet is originating from
     */
    public CoreSide getOriginatingSide(){
        return this.context.side == Side.CLIENT ? CoreSide.SERVER : CoreSide.CLIENT;
    }

    public EntityPlayer getSendingPlayer(){
        return this.context.getServerHandler().player;
    }

    /**
     * @return the client world if client-side, or the sending player's world if server-side
     */
    public World getWorld(){
        return this.getHandlingSide() == CoreSide.CLIENT ? ClientUtils.getWorld() : this.getSendingPlayer().world;
    }

    public void queueTask(Runnable task){
        if(this.getHandlingSide() == CoreSide.SERVER)
            this.context.getServerHandler().player.getServer().addScheduledTask(task);
        else
            ClientUtils.queueTask(task);
    }

    @Deprecated
    public MessageContext getUnderlyingContext(){
        return this.context;
    }

}
