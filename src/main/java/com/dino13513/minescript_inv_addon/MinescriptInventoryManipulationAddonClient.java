package com.dino13513.minescript_inv_addon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.mojang.brigadier.arguments.StringArgumentType;

public class MinescriptInventoryManipulationAddonClient implements ClientModInitializer {
    public static final String MOD_ID = "minescript_inv_maniulation_addon";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final int PORT = 25566;

    private volatile BufferedWriter clientWriter;
    private final Object sendLock = new Object();
    private final BlockingQueue<String> commandQueue = new LinkedBlockingQueue<>();
    private String lastCommand = "";

    private volatile ServerSocket serverSocket;
    private volatile boolean serverRunning = false;

    @Override
    public void onInitializeClient() {
        System.out.println("MinaMod loaded (inventory manipulation addon)");

        startTCPServer();
        startCommandProcessor();
        registerMinatestCommand();
    }

    private void startTCPServer() {
        Thread serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                serverRunning = true;
                System.out.println("MinaMod TCP server running on " + PORT);

                while (serverRunning) {
                    try {
                        Socket client = serverSocket.accept();

                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(client.getInputStream())
                        );

                        clientWriter = new BufferedWriter(
                                new OutputStreamWriter(client.getOutputStream())
                        );

                        String line;
                        while ((line = reader.readLine()) != null) {
                            commandQueue.offer(line);
                        }
                    } catch (IOException e) {
                        if (serverRunning) {
                            e.printStackTrace();
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (serverSocket != null && !serverSocket.isClosed()) {
                        serverSocket.close();
                    }
                } catch (IOException ignored) {}
            }
        });

        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void stopTCPServer() {
        serverRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            System.out.println("TCP server stopped.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void reopenTCPServer() {
        stopTCPServer();
        startTCPServer();
    }

    private void startCommandProcessor() {
        Thread processorThread = new Thread(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            while (true) {
                try {
                    String cmdLine = commandQueue.take(); // blocks until a command is available
                    handleCommand(client, cmdLine);
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
            }
        });

        processorThread.setDaemon(true);
        processorThread.start();
    }

    private void registerMinatestCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    ClientCommandManager.literal("minatest")
                            .then(ClientCommandManager.argument("arg", StringArgumentType.word())
                                    .executes(context -> {
                                        String arg = StringArgumentType.getString(context, "arg");
                                        handleMinatestCommand(arg);
                                        return 1;
                                    })
                            )
            );
        });
    }

    private void handleMinatestCommand(String arg) {
        MinecraftClient client = MinecraftClient.getInstance();

        switch (arg.toLowerCase()) {
            case "port":
                try (Socket socket = new Socket("127.0.0.1", PORT)) {
                    client.inGameHud.getChatHud().addMessage(Text.literal("Port " + PORT + " is OPEN"));
                } catch (Exception e) {
                    client.inGameHud.getChatHud().addMessage(Text.literal("Port " + PORT + " is CLOSED"));
                }
                break;

            case "last":
                if (lastCommand.isEmpty()) {
                    client.inGameHud.getChatHud().addMessage(Text.literal("No last command received yet"));
                } else {
                    client.inGameHud.getChatHud().addMessage(Text.literal("Last command: " + lastCommand));
                }
                break;

            case "run":
                if (client.player != null && client.player.currentScreenHandler != null) {
                    ScreenHandler handler = client.player.currentScreenHandler;
                    client.execute(() -> {
                        try {
                            client.interactionManager.clickSlot(handler.syncId, 0, 0, SlotActionType.PICKUP, client.player);
                            client.inGameHud.getChatHud().addMessage(Text.literal("Test ran: pressed slot 0"));
                        } catch (Exception e) {
                            client.inGameHud.getChatHud().addMessage(Text.literal("Test failed: " + e.getMessage()));
                        }
                    });
                }
                break;

            case "reopen":
                reopenTCPServer();
                client.inGameHud.getChatHud().addMessage(Text.literal("TCP server reopened."));
                break;

            default:
                client.inGameHud.getChatHud().addMessage(Text.literal("Unknown minatest arg: " + arg));
                break;
        }
    }

    private void sendToClient(String msg) {
        try {
            synchronized (sendLock) {
                if (clientWriter != null) {
                    clientWriter.write(msg + "\n");
                    clientWriter.flush();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleCommand(MinecraftClient client, String msg) {
        lastCommand = msg;
        LOGGER.info("Received command: " + msg);
        String[] parts = msg.split(" ");
        if (parts.length < 2) return;

        String cmd = parts[0].toUpperCase();
        int slot = Integer.parseInt(parts[1]);
        int slot2 = (parts.length > 2) ? Integer.parseInt(parts[2]) : 0;
        final int hotbarSlot = Math.max(0, Math.min(slot2, 8));

        if (client.player == null || client.player.currentScreenHandler == null) return;

        ScreenHandler handler = client.player.currentScreenHandler;

        client.execute(() -> {
            try {
                switch (cmd) {
                    case "PRESS":
                        client.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, client.player);
                        LOGGER.info("Pressed slot: " + slot);
                        break;

                    case "SHIFTPRESS":
                        client.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, client.player);
                        LOGGER.info("Quick moved slot: " + slot);
                        break;

                    case "MOVE":
                        client.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.PICKUP, client.player);
                        Thread.sleep(50);
                        client.interactionManager.clickSlot(handler.syncId, slot2, 0, SlotActionType.PICKUP, client.player);
                        LOGGER.info("Moved slot: " + slot + " to slot: " + slot2);
                        break;

                    case "TOHOTBAR":
                        client.interactionManager.clickSlot(handler.syncId, slot, hotbarSlot, SlotActionType.SWAP, client.player);
                        LOGGER.info("Swapped slot: " + slot + " to hotbar slot: " + hotbarSlot);
                        break;

                    case "DROP":
                        client.interactionManager.clickSlot(handler.syncId, slot, 1, SlotActionType.THROW, client.player);
                        LOGGER.info("Dropped slot: " + slot);
                        break;

                    case "DROPONE":
                        client.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.THROW, client.player);
                        LOGGER.info("Dropped one from slot: " + slot);
                        break;

                    case "LOG":
                        LOGGER.info("log command runned log that");
                        client.inGameHud.getChatHud().addMessage(Text.literal("log command logged!"));
                        break;

                    case "INFO":
                        String type = handler.getClass().getSimpleName();
                        int totalslots = handler.slots.size();
                        sendToClient("INFO " + type + " " + totalslots);
                        break;

                    default:
                        LOGGER.warn("Unknown command: " + cmd);
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}