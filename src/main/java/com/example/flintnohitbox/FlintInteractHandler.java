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
        
        // Cooldown to look legitimate
        if (lastUse.containsKey(uuid)) {
            long time = System.currentTimeMillis();
            if (time - lastUse.get(uuid) < 200) {
                return;
            }
        }
        lastUse.put(uuid, System.currentTimeMillis());
        
        // CANCEL the entity interaction (prevents lighting player on fire)
        event.setCanceled(true);
        
        // Get the block the player is looking at - EXACTLY LIKE LAVA PLACEMENT!
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
        
        // PLACE FIRE EXACTLY LIKE LAVA - DIRECT BLOCK PLACEMENT!
        if (world.isAirBlock(targetPos) && Blocks.fire.canPlaceBlockAt(world, targetPos)) {
            // DIRECT BLOCK SET - LIKE LAVA!
            world.setBlockState(targetPos, Blocks.fire.getDefaultState());
            held.damageItem(1, player);
            
            // Play fire sound (like lava pop sound but fire)
            world.playSoundEffect(
                targetPos.getX() + 0.5D,
                targetPos.getY() + 0.5D,
                targetPos.getZ() + 0.5D,
                "fire.ignite", 1.0F, 1.0F
            );
            
            // Send fake packet for anti-cheat
            sendFakePlacementPacket(player, targetPos, side);
        }
    }
    
    // Send fake packet like vanilla (bypasses anti-cheat)
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
