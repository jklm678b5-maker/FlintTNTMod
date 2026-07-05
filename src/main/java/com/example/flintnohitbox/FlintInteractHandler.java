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
        
        // Cooldown to look legitimate
        if (lastUse.containsKey(uuid)) {
            long time = System.currentTimeMillis();
            if (time - lastUse.get(uuid) < 200) {
                return;
            }
        }
        lastUse.put(uuid, System.currentTimeMillis());
        
        // Get what player is looking at
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
        
        // Cancel the entity interaction
        event.setCanceled(true);
        
        // TRY PLACING FIRE ON THE SIDE OF THE BLOCK (not under feet)
        BlockPos targetPos = hitPos.offset(side);
        
        // If the target position has a player standing on it, try the block next to it
        if (isPlayerStandingOn(world, targetPos)) {
            // Try placing on the side instead
            targetPos = hitPos;
        }
        
        // Try placing fire
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
            // FALLBACK: Place fire on the block the player is looking at
            // (this works better for anti-cheat)
            BlockPos fallbackPos = hitPos.offset(EnumFacing.UP);
            if (world.isAirBlock(fallbackPos) && Blocks.fire.canPlaceBlockAt(world, fallbackPos)) {
                world.setBlockState(fallbackPos, Blocks.fire.getDefaultState());
                held.damageItem(1, player);
                world.playSoundEffect(
                    fallbackPos.getX() + 0.5D,
                    fallbackPos.getY() + 0.5D,
                    fallbackPos.getZ() + 0.5D,
                    "fire.ignite", 1.0F, 1.0F
                );
                sendFakePlacementPacket(player, fallbackPos, EnumFacing.UP);
            }
        }
    }
    
    // Check if a player is standing on this block
    private boolean isPlayerStandingOn(World world, BlockPos pos) {
        // Check if any entity (player) is standing on this block
        // In 1.8.9, we check around the block
        return false; // Simplified - we'll just use fallback
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
}
