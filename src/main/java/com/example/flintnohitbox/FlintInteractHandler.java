package com.example.flintnohitbox;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemFlintAndSteel;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlintInteractHandler {
    
    private static Map<UUID, Long> lastUse = new HashMap<>();
    
    @SubscribeEvent
    public void onEntityInteract(EntityInteractEvent event) {
        EntityPlayer player = event.entityPlayer;
        World world = player.worldObj;
        ItemStack held = player.getHeldItem();
        UUID uuid = player.getUniqueID();
        
        // Check if holding flint and steel
        if (held == null || !(held.getItem() instanceof ItemFlintAndSteel)) {
            return;
        }
        
        // ANTI-CHEAT BYPASS: Small cooldown to look legitimate
        if (lastUse.containsKey(uuid)) {
            long time = System.currentTimeMillis();
            if (time - lastUse.get(uuid) < 200) { // 0.2 second cooldown
                return;
            }
        }
        lastUse.put(uuid, System.currentTimeMillis());
        
        // Get what player is looking at (like vanilla)
        double reach = 4.5D;
        Vec3 eyePos = new Vec3(
            player.posX,
            player.posY + player.getEyeHeight(),
            player.posZ
        );
        Vec3 lookVec = player.getLook(1.0F);
        Vec3 endPos = eyePos.addVector(
            lookVec.xCoord * reach,
            lookVec.yCoord * reach,
            lookVec.zCoord * reach
        );
        
        MovingObjectPosition result = world.rayTraceBlocks(eyePos, endPos, false, true, false);
        
        if (result == null || result.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        
        BlockPos hitPos = result.getBlockPos();
        EnumFacing side = result.sideHit;
        BlockPos targetPos = hitPos.offset(side);
        
        // Cancel the entity interaction
        event.setCanceled(true);
        
        // PLACE FIRE (ignoring player hitbox - like lava!)
        if (world.isAirBlock(targetPos) && Blocks.fire.canPlaceBlockAt(world, targetPos)) {
            world.setBlockState(targetPos, Blocks.fire.getDefaultState());
            held.damageItem(1, player);
            
            // Play vanilla fire sound
            world.playSoundEffect(
                targetPos.getX() + 0.5D,
                targetPos.getY() + 0.5D,
                targetPos.getZ() + 0.5D,
                "fire.ignite", 1.0F, 1.0F
            );
            
            // ANTI-CHEAT BYPASS: Send a fake block placement packet (client-side)
            sendFakePlacementPacket(player, targetPos, side);
        }
    }
    
    // ANTI-CHEAT BYPASS: Send fake packet to make it look like vanilla block placement
    private void sendFakePlacementPacket(EntityPlayer player, BlockPos pos, EnumFacing side) {
        try {
            // Get the Minecraft instance and the NetHandler
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.getNetHandler() != null) {
                net.minecraft.network.play.client.C08PacketPlayerBlockPlacement packet = 
                    new net.minecraft.network.play.client.C08PacketPlayerBlockPlacement(
                        pos,
                        side.ordinal(),
                        player.getHeldItem(),
                        0.5F, 0.5F, 0.5F
                    );
                mc.getNetHandler().sendPacket(packet);
            }
        } catch (Exception e) {
            // Ignore - packet might fail but that's okay
        }
    }
}
