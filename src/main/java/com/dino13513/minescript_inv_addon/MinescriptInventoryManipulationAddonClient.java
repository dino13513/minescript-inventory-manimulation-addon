package com.dino13513.minescript_inv_addon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinescriptInventoryManipulationAddonClient implements ClientModInitializer {
    public static final String MOD_ID = "minescript_inv_manimulation_addon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final int PORT = 25566; // python will connect to this

    @Override
    public void onInitializeClient() {
        System.out.println("MinaMod loaded (inventory manip addon)");

        Thread serverThread = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(PORT)) {
                System.out.println("MinaMod TCP server running on " + PORT);

                while (true) {
                    Socket client = server.accept();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        handleCommand(line);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void handleCommand(String msg) {
        try {
            System.out.println("Received: " + msg);
            LOGGER.info("Received command: "+ msg);

            String[] parts = msg.split(" ");
            if (parts.length < 2) return; // not enough arguments

            String cmd = parts[0];
            int slot = Integer.parseInt(parts[1]);
            int slot2 = (parts.length > 2) ? Integer.parseInt(parts[2]) : 0;

            // clamp hotbar slot for TOHOTBAR
            final int hotbarSlot = Math.max(0, Math.min(slot2, 8));

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.player.currentScreenHandler == null) return;

            ScreenHandler handler = client.player.currentScreenHandler;

            // run inventory actions on the main client thread
            client.execute(() -> {
                try {
                    switch (cmd) {
                        case "PRESS":
                            client.interactionManager.clickSlot(
                                    handler.syncId,
                                    slot,
                                    0,
                                    SlotActionType.PICKUP,
                                    client.player
                            );
                            LOGGER.info("pressed slot: "+slot);
                            break;

                        case "SHIFTPRESS":
                            client.interactionManager.clickSlot(
                                    handler.syncId,
                                    slot,
                                    0,
                                    SlotActionType.QUICK_MOVE,
                                    client.player
                            );
                            LOGGER.info("Quick moved slot: "+slot);
                            break;

                        case "MOVE":
                            client.interactionManager.clickSlot(
                                    handler.syncId,
                                    slot,
                                    0,
                                    SlotActionType.PICKUP,
                                    client.player
                            );
                            try {
                                Thread.sleep(50); // small delay between pick up and drop
                            } catch (InterruptedException ignored) {
                            }
                            client.interactionManager.clickSlot(
                                    handler.syncId,
                                    slot2, // use hotbarSlot here if this is the target slot
                                    0,
                                    SlotActionType.PICKUP,
                                    client.player
                            );
                            LOGGER.info("moved slot: "+slot+" to slot: "+slot2);
                            break;

                        case "TOHOTBAR":
                            client.interactionManager.clickSlot(
                                    handler.syncId,
                                    slot,
                                    hotbarSlot, // correctly use final variable
                                    SlotActionType.SWAP,
                                    client.player
                            );
                            LOGGER.info("swapped slot: "+slot+" to hotbar slot: "+hotbarSlot);
                            break;
                        case "DROP":
                            client.interactionManager.clickSlot(
                                    handler.syncId,
                                    slot, // use hotbarSlot here if this is the target slot
                                    1,
                                    SlotActionType.THROW,
                                    client.player
                            );
                            LOGGER.info("dropped slot: "+slot);
                        case "DROPONE":
                            client.interactionManager.clickSlot(
                                    handler.syncId,
                                    slot, // use hotbarSlot here if this is the target slot
                                    0,
                                    SlotActionType.THROW,
                                    client.player
                            );
                            LOGGER.info("dropped slot: "+slot);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
