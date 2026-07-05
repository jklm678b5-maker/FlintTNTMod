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

import java.lang.reflect.Field;
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
        
        // FIX: Force the game to ignore entities by using rayTrace with custom parameters
        // We use a trick: temporarily pretend entities don't exist!
        
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
        
        // Use the world.rayTraceBlocks method - this ONLY checks blocks, NOT entities!
        MovingObjectPosition result = world.rayTraceBlocks(eyePos, endPos, false, true, false);
        
        if (result == null || result.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            // If we didn't hit a block, try to place fire at a fixed position
            // This is a fallback for when you're aiming through an entity
            BlockPos playerPos = player.getPosition();
            BlockPos belowPlayer = playerPos.offset(EnumFacing.DOWN);
            
            if (world.isAirBlock(belowPlayer) && Blocks.fire.canPlaceBlockAt(world, belowPlayer)) {
                world.setBlockState(belowPlayer, Blocks.fire.getDefaultState());
                held.damageItem(1, player);
                world.playSoundEffect(
                    belowPlayer.getX() + 0.5D,
                    belowPlayer.getY() + 0.5D,
                    belowPlayer.getZ() + 0.5D,
                    "fire.ignite", 1.0F, 1.0F
                );
                sendFakePlacementPacket(player, belowPlayer, EnumFacing.UP);
            }
            return;
        }
        
        BlockPos hitPos = result.getBlockPos();
        EnumFacing side = result.sideHit;
        BlockPos targetPos = hitPos.offset(side);
        
        // Place fire on the block
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
        } else {
            // If can't place on the side, try placing on the block itself
            if (Blocks.fire.canPlaceBlockAt(world, hitPos)) {
                world.setBlockState(hitPos, Blocks.fire.getDefaultState());
                held.damageItem(1, player);
                world.playSoundEffect(
                    hitPos.getX() + 0.5D,
                    hitPos.getY() + 0.5D,
                    hitPos.getZ() + 0.5D,
                    "fire.ignite", 1.0F, 1.0F
                );
                sendFakePlacementPacket(player, hitPos, EnumFacing.UP);
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
