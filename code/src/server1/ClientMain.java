package server1;

import com.google.gson.Gson;

import java.awt.*;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static server1.Message.fmtHM;
import static server1.Message.fmtUser;
import static server1.server.*;

public class ClientMain {
    private Socket socket;
    private volatile boolean connected = true;
    private volatile boolean running = true;
    private static final BufferedReader STDIN = new BufferedReader(new InputStreamReader(System.in));
    private BufferedReader in;
    private PrintWriter out;
    private final Gson gson = new Gson();
    private String username;
    BlockingQueue<Message> queue = new LinkedBlockingQueue<>();

    Set<String> pendingChatRequests = ConcurrentHashMap.newKeySet();
    private final Map<String, Boolean> activeChats = new ConcurrentHashMap<>(); // created chats
    private final Map<String, BlockingQueue<String>> chatQueues = new ConcurrentHashMap<>(); // msgs
    private final Set<String> openSessions = ConcurrentHashMap.newKeySet(); // created and active chats

    private final Map<String, BlockingQueue<Message>> groupQueues = new ConcurrentHashMap<>();
    private final Set<String> openGroupSessions = ConcurrentHashMap.newKeySet();
    BlockingQueue<Message> groupEvents = new LinkedBlockingQueue<>();


    public ClientMain(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        waitForServerReady();
    }
    public ClientMain() throws IOException {}

    public String checkNickname() throws IOException {
        while (true) {
            System.out.print(" [+] Enter username: ");
            String user = STDIN.readLine();
            if (user == null) throw new IOException("stdin closed");
            Message reg = new Message("register", user, null, null, System.currentTimeMillis(), null);
            out.println(gson.toJson(reg));

            String line = in.readLine();
            if (line == null) throw new IOException("server disconnected");

            Message m = gson.fromJson(line, Message.class);
            if ("error".equals(m.type)) {
                System.out.printf("\n \u001B[31m[%s] %s%n \u001B[0m\n", m.type, m.content);
            } else if ("system".equals(m.type)) {
                System.out.printf("\n [+]\u001B[32m %s%n \u001B[0m \n", m.content);
                return user;
            }
        }
    }
    private void setNick(String nick) { this.username = nick; }
    private void waitForServerReady() throws IOException {
        String line = in.readLine();
        if (line == null) throw new IOException("server disconnected");

        Message msg = gson.fromJson(line, Message.class);
        if (!"system".equals(msg.type) || !"READY".equals(msg.content)) {
            throw new IOException("invalid server handshake");
        }
    }
    private void resetState() {
        openSessions.clear();
        openGroupSessions.clear();
        pendingChatRequests.clear();
        activeChats.clear();
        chatQueues.clear();
        groupQueues.clear();
        groupEvents.clear();
        queue.clear();
    }

    private void menu() {
        log("");
        log(" - " + Message.Colors.PURPLE + "COMMAND LIST " + Message.Colors.RESET + "- (shortcuts available for all commands)\n");
        log(" - List all users/groups:     " + Message.Colors.YELLOW  + " /list users/groups" + Message.Colors.RESET);
        log(" - Create a chat with a user: " + Message.Colors.YELLOW  + " /chat <user>" + Message.Colors.RESET);
        log(" - Create/Join a group:       " + Message.Colors.YELLOW  + " /group create/join <group>" + Message.Colors.RESET);
        log(" - Quit a chat/group/exit:    " + Message.Colors.YELLOW  + " /quit or /exit" + Message.Colors.RESET);
        log(" - Command list:              " + Message.Colors.YELLOW  + " /help" + Message.Colors.RESET);
        log(" - Shortcut example: " + Message.Colors.GREEN  + "  /list -> /l or /ls \n" + Message.Colors.RESET);
    }
    public void start() throws IOException {
        resetState();
        connected = true;
        running = true;

        /*socket = new Socket("localhost", 9999);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true); */
        // waitForServerReady();
        this.setNick(checkNickname());
        Thread reader = new Thread(() -> {
            try {
                String line;
                while (running && (line = in.readLine()) != null) {
                        Message msg = gson.fromJson(line, Message.class);
                        queue.offer(msg);
                        switch (msg.type) {
                            case "chat_request" -> {
                                logl("+ " + Message.Colors.YELLOW + "[CHAT REQUEST] " + Message.Colors.RESET + msg.content + "\n\n > ");
                                pendingChatRequests.add(msg.from);
                                chatQueues.computeIfAbsent(msg.from, k -> new LinkedBlockingQueue<>());
                            }
                            case "chat_accept"  -> {
                                logl("+ " + Message.Colors.GREEN + "[CHAT REQUEST] " + Message.Colors.RESET + msg.content + "\n\n > ");
                                activeChats.put(msg.from, true);
                                chatQueues.computeIfAbsent(msg.from, k -> new LinkedBlockingQueue<>());
                                // startChatSession(msg.from);
                            }
                            case "chat" -> {
                                boolean sessionOpen = openSessions.contains(msg.from);
                                String ts = msg.timestamp == null ? "?" : fmtHM(msg.timestamp);
                                if (sessionOpen) { logl("[" + fmtUser(msg) + "][" + ts + "]: " + msg.content + "\n > "); System.out.flush();
                                } else {
                                    chatQueues.computeIfAbsent(msg.from, k -> new LinkedBlockingQueue<>()).offer("[" + ts + "]: " + msg.content);
                                    int unread = chatQueues.computeIfAbsent(msg.from, k -> new LinkedBlockingQueue<>()).size();
                                    logl("+ " + Message.Colors.YELLOW + "[CHAT] " + Message.Colors.RESET + unread + " unread msg(s) from " + msg.from + "\n > ");
                                }
                            }
                            case "chat_error" -> {
                                logerr("User doesn't exist.");
                                logl("\n > ");
                            }
                            case "chat_notify" -> logl(msg.content + "\n > ");
                            case "chat_close" -> {
                                logl("[-] " + msg.from + " closed the chat. \n > ");
                                activeChats.remove(msg.from);
                                chatQueues.remove(msg.from);
                                pendingChatRequests.remove(msg.from); // cleanup if any
                                openSessions.remove(msg.from);
                            }
                            case "group_joined" -> {
                                // logl("+ " + Message.Colors.GREEN + "[GROUP] " + Message.Colors.RESET + "Successfully joined " + msg.to + " \n > ");
                                groupEvents.offer(msg);
                                groupQueues.computeIfAbsent(msg.to, k -> new LinkedBlockingQueue<>());
                            }
                            case "group_notify" -> logl(Message.Colors.GREEN + "[i] " + Message.Colors.RESET + msg.content + ". \n > ");
                            case "group_msg" -> {
                                String group = msg.to;
                                if (username.equals(msg.from)) continue;
                                boolean sessionOpen = openGroupSessions.contains(group);
                                String ts = msg.timestamp == null ? "?" : fmtHM(msg.timestamp);
                                if (sessionOpen) {
                                    logl("[" + fmtUser(msg) + "][" + ts + "]: " + msg.content + "\n > ");
                                    System.out.flush();
                                } else {
                                    groupQueues.computeIfAbsent(group, k -> new LinkedBlockingQueue<>()).offer(msg);
                                    int unread = groupQueues.get(group).size();
                                    logl("+ " + Message.Colors.YELLOW + "[GROUP] " + Message.Colors.RESET + unread + " unread msg(s) in " + group + "\n > "); }
                            }
                            case "list" -> {} // nothing bc its already handled
                            case "group_error" -> groupEvents.offer(msg);
                            // default -> { log("log: " + msg.to +  ", " +  msg.from + ", " + msg.content); }
                        }
                    }
                connected = false;
            } catch (IOException e) {
                connected = false;
                System.err.println(" [-] Disconnected: " + e.getMessage() + ". Type any letter and send it.");
            } finally { connected = false; running = false; }
        });
        reader.setDaemon(true);
        reader.start();

        boolean continueP = true;
        try (Scanner sca = new Scanner(System.in, StandardCharsets.UTF_8)) {
            menu();
            while (continueP && connected) {
                    logl(" > "); System.out.flush();
                    String input = sca.nextLine(); if (input == null) break;
                    input = input.trim(); if (input.isEmpty()) continue;
                    if (!connected) break;

                    if (input.equalsIgnoreCase("/exit") || input.equalsIgnoreCase("/quit") || input.equalsIgnoreCase("/q")) {
                        continueP=false; continue; }
                    if (input.equalsIgnoreCase("/help") || input.equalsIgnoreCase("/h")) { menu(); continue; }

                    if (input.startsWith("/list") || input.startsWith("/l")) {
                        String[] parts = input.trim().split("\\s+");
                        if (parts.length < 2) { logerr("   Invalid argument \n"); continue; }
                        String cmd = parts[0].toLowerCase();
                        String target = parts[1].trim().toLowerCase();
                        boolean validCmd = cmd.equals("/list") || cmd.equals("/l") || cmd.equals("/ls");

                        if (!validCmd) { logerr("   Invalid argument\n"); continue; }
                        switch (target) {
                            case "g", "groups" -> handleListCommand("groups");
                            case "u", "users"  -> handleListCommand("users");
                            default -> logerr("   Invalid argument\n");
                        } continue;

                    } else if (input.startsWith("/msg")) {
                        log("msg n");

                    } else if (input.startsWith("/chat") || input.startsWith("/w") || input.startsWith("/c")) {
                        String[] parts = input.trim().split("\\s+");
                        if (parts.length != 2) { logerr("   Invalid argument \n"); continue; }
                        String cmd = parts[0].toLowerCase();
                        String target = parts[1].trim();
                        boolean validCmd =  cmd.equals("/chat") || cmd.equals("/c") || cmd.equals("/w");
                        if (!validCmd) { logerr("   Invalid command\n"); continue; }
                        int code = handleChatCommand(target, sca);
                        if (code == 1) continueP = false;

                    } else if (input.startsWith("/group") || input.startsWith("/g")) {
                        String[] parts = input.trim().split("\\s+");
                        String group; int code;
                        if (parts.length < 3) { logerr("   Not enough arguments \n"); continue; }
                        String cmd = parts[0].toLowerCase();
                        boolean validCmd =  cmd.equals("/group") || cmd.equals("/g");
                        if (!validCmd) { logerr("   Invalid command\n"); continue; }

                        String cmdType = parts[1].trim().toLowerCase();
                        if (cmdType.equalsIgnoreCase("create") || cmdType.equalsIgnoreCase("c")) {
                            group = parts[2].trim().toLowerCase();
                            code = handleGroupCommand(group, 1, sca);
                        } else if (cmdType.equalsIgnoreCase("join") || cmdType.equalsIgnoreCase("j")) {
                            group = parts[2].trim().toLowerCase();
                            code = handleGroupCommand(group, 2, sca);
                        } else { logerr(("   Invalid argument\n")); continue; }
                        if (code == 1) continueP = false;

                    } else { logerr("   Invalid command\n"); }
                }
        } catch (Exception e) { System.err.println("Error in client loop: " + e.getMessage());
        } finally {
            running = false;
            // try { reader.join(); } catch (InterruptedException ignored) {}
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        }
    }

    private int handleGroupCommand(String group, int type, Scanner sca) {
        if (group == null || group.isBlank()) { logerr("Invalid target"); return 0; }
        int code = 0;
        if (type == 1) {
            out.println(gson.toJson(new Message("create_group", username, group, null, System.currentTimeMillis(), null)));
            if (!waitForGroupJoin(group, 2000)) {
                logerr("   Failed to create/join group " + group);
                return 0; }
            openGroupSessions.add(group);
            try { code = startGroupSession(group, sca);
            } finally { openGroupSessions.remove(group); }

        } else if (type == 2) {
            out.println(gson.toJson(new Message("join_group", username, group, null, System.currentTimeMillis(), null)));
            log("   Trying to join " + group + "...");
            if (!waitForGroupJoin(group, 2000)) {
                // logerr("   Group " + group + " does not exist or join timed out. \n");
                return 0; }
            openGroupSessions.add(group);
            try {code = startGroupSession(group, sca);
            } finally { openGroupSessions.remove(group);}
        }
        return code;
    }
    private boolean waitForGroupJoin(String group, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) return false;
            Message msg;
            try { msg = groupEvents.poll(remaining, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) { return false; }

            if (msg == null) return false;
            if ("group_error".equals(msg.type)) {
                logerr("   " + msg.content + "\n");
                return false; }
            if ("group_joined".equals(msg.type)){ return true; }
            if (!group.equals(msg.to)) {
                groupEvents.offer(msg); continue; }
        }
    }
    private int startGroupSession(String group, Scanner sc) {
        log("\n[+] " + Message.Colors.GREEN + "ENTERING GROUP " + group + Message.Colors.RESET +  ", /q to quit, /leave to leave.");
        // activeGroups.add(group);
        // openGroupSessions.add(group);
        groupQueues.computeIfAbsent(group, k -> new LinkedBlockingQueue<>());
        int code = 0; boolean inGroup = true;

        while (inGroup) {
            BlockingQueue<Message> q = groupQueues.get(group);
            Message m;
            while ((m = q.poll()) != null) {
                String ts = m.timestamp == null ? "?" : fmtHM(m.timestamp);
                log(" > [" + fmtUser(m) + "][" + ts + "]: " + m.content);
            }

            System.out.print(" > "); System.out.flush();
            String line;
            try {
                if (!sc.hasNextLine()) break;
                line = sc.nextLine();
            } catch (NoSuchElementException e) { break; }

            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.equalsIgnoreCase("/q")) {
                log(" - Temporarily Exiting group " + group + " (still joined)\n");
                inGroup = false;
            } else if (line.equalsIgnoreCase("/leave") || line.equalsIgnoreCase("/exit")) {
                Message leave = new Message("leave_group", username, group, System.currentTimeMillis(), null);
                out.println(gson.toJson(leave));
                log("[-] Left group " + group + "\n");
                // activeGroups.remove(group);
                groupQueues.remove(group);
                if (line.equalsIgnoreCase("/exit")) code = 1;
                inGroup = false;
            } else {
                Message gm = new Message("group_msg", username, group, line, System.currentTimeMillis(), null);
                out.println(gson.toJson(gm)); }
        }
        openGroupSessions.remove(group);
        return code;
    }


    private int handleChatCommand(String target, Scanner sca) {
        if (target == null || target.isBlank() || target.equalsIgnoreCase(username)) { logerr("Invalid target"); return 0; }
        boolean isReply = pendingChatRequests.contains(target);
        boolean isActive = activeChats.containsKey(target);
        Message req;
        int code = 0;
        if (isReply || isActive) {
            req = new Message("chat_accept", username, target, System.currentTimeMillis(), null);
            out.println(gson.toJson(req));
            pendingChatRequests.remove(target);
            openSessions.add(target);
            try { code = startChatSession(target, sca);
            } finally { openSessions.remove(target); }
        } else {
            req = new Message("chat_request", username, target, System.currentTimeMillis(), null);
            out.println(gson.toJson(req));
            // log("   Chat request sent to " + target + ", waiting for acceptance...\n");
        }
        return code;
    }
    private int startChatSession(String chatUser, Scanner sc) {
        log("\n[+] " + Message.Colors.GREEN + "ENTERING " + chatUser + "'s chat" + Message.Colors.RESET + ", type /q to quit, /close to close entirely.");
        activeChats.put(chatUser, true);
        chatQueues.computeIfAbsent(chatUser, k -> new LinkedBlockingQueue<>());

        int code = 0;
        boolean inChat = true;
        while (inChat) {
            BlockingQueue<String> queue1 = chatQueues.get(chatUser);
            while (!queue1.isEmpty()) {
                log(" > [" + chatUser + "]" + queue1.poll());
            }

            System.out.print(" > "); System.out.flush();
            String line;
            try {
                if (!sc.hasNextLine()) break;
                line = sc.nextLine();
            } catch (NoSuchElementException e) { break; }
            if (line == null) break;
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.equalsIgnoreCase("/q")) {
                log(" - Exiting chat with " + chatUser + " (but still active) \n");
                openSessions.remove(chatUser);
                inChat = false;
            } else if (line.equalsIgnoreCase("/close") || line.equalsIgnoreCase("/exit")) {
                log("[-] Closing chat with " + chatUser + "\n");
                activeChats.remove(chatUser);
                chatQueues.remove(chatUser);
                Message closeMsg = new Message("chat_close", username, chatUser, System.currentTimeMillis(), null);
                out.println(gson.toJson(closeMsg));
                pendingChatRequests.remove(chatUser);
                openSessions.remove(chatUser);
                inChat = false;
                if (line.equalsIgnoreCase("/exit")) code = 1;
            } else {
                if (activeChats.containsKey(chatUser)) {
                    Message chatMsg = new Message("chat", username, chatUser, line, System.currentTimeMillis(), null);
                    chatMsg.toType = "user";
                    out.println(gson.toJson(chatMsg));
                } else {
                    logerr("   No active chat with " + chatUser);
                    inChat = false; } }

            if (!activeChats.containsKey(chatUser)) { inChat = false; }
        }
        return code;
    }

    private void handleListCommand(String target) {
        try {
            Message req = new Message("list", username, null, null, System.currentTimeMillis(), null);
            req.target = target;
            out.println(gson.toJson(req));
            Message reply;
            do { reply = queue.take();
            } while (!"list".equals(reply.type));

            /*String line = in.readLine();
            if (line == null) { logerr("Server closed connection"); return; }
            Message reply = gson.fromJson(line, Message.class);*/

            List<String> items = null;
            if (reply.items != null && !reply.items.isEmpty()) {
                items = reply.items;
            } else if (reply.content != null && !reply.content.isBlank()) { // split by line for groups (each line: group: user1, user2)
                if ("groups".equalsIgnoreCase(target) || "g".equalsIgnoreCase(target)) {
                    items = Arrays.stream(reply.content.split("\n")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                } else { // users
                    items = Arrays.stream(reply.content.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList()); }
            }

            if ("users".equalsIgnoreCase(target) || "u".equalsIgnoreCase(target)) {
                if (items == null || items.isEmpty()) { log("   No active users...\n"); return; }
                List<String> others = items.stream().filter(u -> !u.equals(username)).collect(Collectors.toList());

                if (others.isEmpty()) { log("   No other active users...\n"); return; }
                log("   Other active " + Message.Colors.GREEN +  "users: " + Message.Colors.RESET);
                for (String u : others) {
                    System.out.println("   - " + u); }
                System.out.println();
            } else if ("groups".equalsIgnoreCase(target) || "g".equalsIgnoreCase(target)) {
                if (items == null || items.isEmpty()) { log("   No active " + target + "...\n"); return; }
                log("   Active " + Message.Colors.GREEN + target + ":" + Message.Colors.RESET);
                for (String g : items) {
                    System.out.println("   - " + g);
                }
                System.out.println();
            }
        } catch (Exception e) { logerr("   Error while listing: " + e.getMessage()); }
    }

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9999;
        while (true) {
            try {
                ClientMain client = new ClientMain(host, port);
                client.start();
            } catch (Exception e) { System.err.println("Connection failed: " + e.getMessage()); }
            System.out.println("Reconnecting in 3 seconds...");
            Thread.sleep(3500);
        }
    }
}
