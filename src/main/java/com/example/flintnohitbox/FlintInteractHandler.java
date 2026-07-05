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
        
        // ===================================================
        // Use Minecraft's getLook() method (this is correct!)
        // ===================================================
        
        // Get player's eye position
        Vec3 eyePos = new Vec3(
            player.posX,
            player.posY + player.getEyeHeight(),
            player.posZ
        );
        
        // Use Minecraft's built-in look vector (this works!)
        Vec3 lookVec = player.getLook(1.0F);
        
        // If lookVec is null, use default
        if (lookVec == null) {
            lookVec = new Vec3(0, 0, -1);
        }
        
        // NOW: Step through the ray, ignoring ALL entities!
        double reach = 4.5D;
        BlockPos targetBlock = null;
        EnumFacing targetSide = null;
        
        // Step through each block position
        for (double d = 0.0; d < reach; d += 0.1) {
            double currentX = eyePos.xCoord + lookVec.xCoord * d;
            double currentY = eyePos.yCoord + lookVec.yCoord * d;
            double currentZ = eyePos.zCoord + lookVec.zCoord * d;
            
            BlockPos checkPos = new BlockPos(
                MathHelper.floor_double(currentX),
                MathHelper.floor_double(currentY),
                MathHelper.floor_double(currentZ)
            );
            
            // Check if this position is a block (NOT an entity!)
            if (!world.isAirBlock(checkPos)) {
                // Found a block! Now determine which face we hit
                targetBlock = checkPos;
                
                // Calculate which face was hit (approximate)
                double hitX = currentX - checkPos.getX();
                double hitY = currentY - checkPos.getY();
                double hitZ = currentZ - checkPos.getZ();
                
                // Determine the face based on the largest offset
                if (hitX < 0.1) targetSide = EnumFacing.WEST;
                else if (hitX > 0.9) targetSide = EnumFacing.EAST;
                else if (hitY < 0.1) targetSide = EnumFacing.DOWN;
                else if (hitY > 0.9) targetSide = EnumFacing.UP;
                else if (hitZ < 0.1) targetSide = EnumFacing.NORTH;
                else if (hitZ > 0.9) targetSide = EnumFacing.SOUTH;
                else targetSide = EnumFacing.UP;
                
                break;
            }
        }
        
        // If we found a block, place fire on it
        if (targetBlock != null) {
            BlockPos firePos = targetBlock.offset(targetSide);
            
            // Place fire
            if (world.isAirBlock(firePos) && Blocks.fire.canPlaceBlockAt(world, firePos)) {
                world.setBlockState(firePos, Blocks.fire.getDefaultState());
                held.damageItem(1, player);
                
                world.playSoundEffect(
                    firePos.getX() + 0.5D,
                    firePos.getY() + 0.5D,
                    firePos.getZ() + 0.5D,
                    "fire.ignite", 1.0F, 1.0F
                );
                
                sendFakePlacementPacket(player, firePos, targetSide);
            } else if (Blocks.fire.canPlaceBlockAt(world, targetBlock)) {
                // If can't place on the side, place on the block itself
                world.setBlockState(targetBlock, Blocks.fire.getDefaultState());
                held.damageItem(1, player);
                world.playSoundEffect(
                    targetBlock.getX() + 0.5D,
                    targetBlock.getY() + 0.5D,
                    targetBlock.getZ() + 0.5D,
                    "fire.ignite", 1.0F, 1.0F
                );
                sendFakePlacementPacket(player, targetBlock, EnumFacing.UP);
            }
        } else {
            // FALLBACK: If no block found, place fire at the max distance
            double endX = eyePos.xCoord + lookVec.xCoord * reach;
            double endY = eyePos.yCoord + lookVec.yCoord * reach;
            double endZ = eyePos.zCoord + lookVec.zCoord * reach;
            
            BlockPos endPos = new BlockPos(
                MathHelper.floor_double(endX),
                MathHelper.floor_double(endY),
                MathHelper.floor_double(endZ)
            );
            
            // Try the end position and the block in front of it
            if (world.isAirBlock(endPos) && Blocks.fire.canPlaceBlockAt(world, endPos)) {
                world.setBlockState(endPos, Blocks.fire.getDefaultState());
                held.damageItem(1, player);
                world.playSoundEffect(
                    endPos.getX() + 0.5D,
                    endPos.getY() + 0.5D,
                    endPos.getZ() + 0.5D,
                    "fire.ignite", 1.0F, 1.0F
                );
                sendFakePlacementPacket(player, endPos, EnumFacing.UP);
            } else {
                // Try the block in front of the player
                BlockPos frontPos = player.getPosition().offset(player.getHorizontalFacing(), 2);
                if (world.isAirBlock(frontPos) && Blocks.fire.canPlaceBlockAt(world, frontPos)) {
                    world.setBlockState(frontPos, Blocks.fire.getDefaultState());
                    held.damageItem(1, player);
                    sendFakePlacementPacket(player, frontPos, EnumFacing.UP);
                }
            }
        }
    }
    
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
