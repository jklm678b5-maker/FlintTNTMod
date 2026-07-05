package com.example.flintnohitbox;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemFlintAndSteel;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

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
        
        // Cooldown
        if (lastUse.containsKey(uuid)) {
            long time = System.currentTimeMillis();
            if (time - lastUse.get(uuid) < 200) {
                return;
            }
        }
        lastUse.put(uuid, System.currentTimeMillis());
        
        // CANCEL the entity interaction
        event.setCanceled(true);
        
        // The KEY is using the right parameters!
        // This is what hack clients use to ignore entities
        Vec3 eyePos = new Vec3(
            player.posX,
            player.posY + player.getEyeHeight(),
            player.posZ
        );
        Vec3 lookVec = player.getLook(1.0F);
        Vec3 endPos = eyePos.addVector(
            lookVec.xCoord * 4.5D,
            lookVec.yCoord * 4.5D,
            lookVec.zCoord * 4.5D
        );
        
        // USE THE RIGHT PARAMETERS - This ignores entities!
        // The third parameter 'false' means don't stop on liquids
        // The fourth parameter 'false' means don't ignore non-solid blocks
        // The fifth parameter 'true' is the key - it returns the last block even if it's not solid
        MovingObjectPosition result = world.rayTraceBlocks(eyePos, endPos, false, false, true);
        
        // If we hit a block, place fire there
        if (result != null && result.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            BlockPos hitPos = result.getBlockPos();
            EnumFacing side = result.sideHit;
            BlockPos targetPos = hitPos.offset(side);
            
            // Place fire
            if (world.isAirBlock(targetPos) && Blocks.fire.canPlaceBlockAt(world, targetPos)) {
                world.setBlockState(targetPos, Blocks.fire.getDefaultState());
                held.damageItem(1, player);
                
                world.playSoundEffect(
                    targetPos.getX() + 0.5D,
                    targetPos.getY() + 0.5D,
                    targetPos.getZ() + 0.5D,
                    "fire.ignite", 1.0F, 1.0F
                );
                
                sendFakePlacementPacket(player, targetPos, side);
                return;
            }
        }
        
        // FALLBACK: If ray trace fails, place fire on the block the player is looking at
        // This handles the case where you're looking at a block through an entity
        BlockPos lookPos = getBlockLookingAt(player);
        if (lookPos != null && world.isAirBlock(lookPos) && Blocks.fire.canPlaceBlockAt(world, lookPos)) {
            world.setBlockState(lookPos, Blocks.fire.getDefaultState());
            held.damageItem(1, player);
            world.playSoundEffect(
                lookPos.getX() + 0.5D,
                lookPos.getY() + 0.5D,
                lookPos.getZ() + 0.5D,
                "fire.ignite", 1.0F, 1.0F
            );
            sendFakePlacementPacket(player, lookPos, EnumFacing.UP);
        }
    }
    
    // Helper method to get the block position without using rayTrace (pure math)
    private BlockPos getBlockLookingAt(EntityPlayer player) {
        // This uses pure math to calculate the block position
        // No rayTrace, so no entity interference!
        Vec3 eyePos = new Vec3(
            player.posX,
            player.posY + player.getEyeHeight(),
            player.posZ
        );
        Vec3 lookVec = player.getLook(1.0F);
        
        // Step through the ray manually (ignoring entities completely)
        for (double d = 0.1; d < 4.5; d += 0.05) {
            double x = eyePos.xCoord + lookVec.xCoord * d;
            double y = eyePos.yCoord + lookVec.yCoord * d;
            double z = eyePos.zCoord + lookVec.zCoord * d;
            
            BlockPos checkPos = new BlockPos(
                (int)Math.floor(x),
                (int)Math.floor(y),
                (int)Math.floor(z)
            );
            
            // If we find a block, return it
            if (!player.worldObj.isAirBlock(checkPos)) {
                return checkPos;
            }
        }
        
        return null;
    }
    
    // ANTI-CHEAT BYPASS: Send fake packet
    private void sendFakePlacementPacket(EntityPlayer player, BlockPos pos, EnumFacing side) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null && mc.getNetHandler() != null) {
                C08PacketPlayerBlockPlacement packet = new C08PacketPlayerBlockPlacement(
                    pos,
                    side.ordinal() + 1,
                    player.getHeldItem(),
                    0.5F, 0.5F, 0.5F
                );
                mc.getNetHandler().addToSendQueue(packet);
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        lastUse.clear();
    }
}
