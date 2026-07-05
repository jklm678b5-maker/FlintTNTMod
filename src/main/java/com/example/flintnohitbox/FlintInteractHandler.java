package com.example.flintnohitbox;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemFlintAndSteel;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
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
        
        // Calculate the EXACT block the player is looking at - IGNORES ALL ENTITIES!
        BlockPos targetPos = getTargetBlock(player, 4.5D);
        
        if (targetPos == null) {
            return;
        }
        
        // Place fire on the block (ignoring entities completely)
        if (world.isAirBlock(targetPos) && Blocks.fire.canPlaceBlockAt(world, targetPos)) {
            // Place fire
            world.setBlockState(targetPos, Blocks.fire.getDefaultState());
            held.damageItem(1, player);
            
            // Play fire sound
            world.playSoundEffect(
                targetPos.getX() + 0.5D,
                targetPos.getY() + 0.5D,
                targetPos.getZ() + 0.5D,
                "fire.ignite", 1.0F, 1.0F
            );
            
            // Send fake packet for anti-cheat
            sendFakePlacementPacket(player, targetPos, EnumFacing.UP);
        }
    }
    
    // This method calculates the block position the player is looking at
    // WITHOUT using rayTrace that gets blocked by entities!
    private BlockPos getTargetBlock(EntityPlayer player, double range) {
        // Get player's eye position
        Vec3 eyePos = new Vec3(
            player.posX,
            player.posY + player.getEyeHeight(),
            player.posZ
        );
        
        // Get player's look direction
        float pitch = player.rotationPitch;
        float yaw = player.rotationYaw;
        
        // Calculate look vector from yaw and pitch
        float pitchRad = -pitch * 0.017453292F;
        float yawRad = -yaw * 0.017453292F;
        
        double x = MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        double y = MathHelper.sin(pitchRad);
        double z = -MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
        
        Vec3 lookVec = new Vec3(x, y, z);
        
        // Step through the ray to find the block - IGNORES ENTITIES!
        for (double d = 0; d < range; d += 0.1D) {
            double currentX = eyePos.xCoord + lookVec.xCoord * d;
            double currentY = eyePos.yCoord + lookVec.yCoord * d;
            double currentZ = eyePos.zCoord + lookVec.zCoord * d;
            
            BlockPos checkPos = new BlockPos(
                MathHelper.floor_double(currentX),
                MathHelper.floor_double(currentY),
                MathHelper.floor_double(currentZ)
            );
            
            // Check if this position is a block
            if (!player.worldObj.isAirBlock(checkPos)) {
                // Found a block - return it
                return checkPos;
            }
        }
        
        // If we didn't find a block, check the block at the max range
        double endX = eyePos.xCoord + lookVec.xCoord * range;
        double endY = eyePos.yCoord + lookVec.yCoord * range;
        double endZ = eyePos.zCoord + lookVec.zCoord * range;
        
        BlockPos endPos = new BlockPos(
            MathHelper.floor_double(endX),
            MathHelper.floor_double(endY),
            MathHelper.floor_double(endZ)
        );
        
        // Check if the end position is a block
        if (!player.worldObj.isAirBlock(endPos)) {
            return endPos;
        }
        
        // If we still don't have a block, use the block above the end position
        // This covers the case where you're aiming at a block's face
        BlockPos abovePos = endPos.offset(EnumFacing.UP);
        if (!player.worldObj.isAirBlock(abovePos)) {
            return abovePos;
        }
        
        // Check the block below the end position
        BlockPos belowPos = endPos.offset(EnumFacing.DOWN);
        if (!player.worldObj.isAirBlock(belowPos)) {
            return belowPos;
        }
        
        return endPos;
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
