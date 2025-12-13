package server1;
import com.google.gson.Gson;
import java.io.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

import static server1.Message.*;
import static server1.server.log;
import static server1.server.logl;
import static server1.server.logr;

public class ConnManager implements Runnable {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private final ConcurrentMap<String, ConnManager> clients;
    private final ConcurrentMap<String, CopyOnWriteArraySet<String>> groups;
    private final Map<String, List<Message>> groupHistory = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CopyOnWriteArraySet<String>> pendingChats;
    private final ConcurrentMap<String, CopyOnWriteArraySet<String>> activeChats;
    private final Gson gson;
    private String nickname = "";
    private final String color = getColor();
    private final Object outLock = new Object();

    public ConnManager(Socket socket, ConcurrentMap<String, ConnManager> clients, ConcurrentMap<String, CopyOnWriteArraySet<String>> groups,ConcurrentMap<String, CopyOnWriteArraySet<String>> pendingChats, ConcurrentMap<String, CopyOnWriteArraySet<String>> activeChats, Gson gson) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        this.clients = clients; this.groups = groups; this.pendingChats = pendingChats; this.activeChats = activeChats; this.gson = gson;
    }
    public ConnManager(Socket socket, ConcurrentMap<String, ConnManager> clients, ConcurrentMap<String, CopyOnWriteArraySet<String>> groups, Gson gson) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        this.clients = clients; this.groups = groups; this.gson = gson; this.pendingChats = null; this.activeChats = null;
    }

    private void send(Message m) {
        m.color = color;
        String json = gson.toJson(m);
        synchronized(outLock) { out.println(json); out.flush(); }
    }
    private void sendl(Message m) {
        String json = gson.toJson(m);
        synchronized(outLock) { out.print(json); out.flush(); }
       /* try {  out.print(gson.toJson(m));
        } catch (Exception e) { server.logr("send error to " + nickname + ": " + e.getMessage()); }*/
    }
    private void sendSimple(String text) { out.println(text); }

    @Override
    public void run() {
        try {
            send(new Message("system", "server", null, "READY", System.currentTimeMillis(), null));
            while (true) {  // registration
                String line = in.readLine();
                if (line == null) { cleanup(); return; } // client closed
                Message first = gson.fromJson(line, Message.class);
                if (!"register".equals(first.type) || first.from == null || first.from.isBlank()) {
                    send(new Message("error", "server", null, "Must register first", System.currentTimeMillis(), null));
                    continue; }

                String candidate = first.from.trim();
                if (candidate.length() > 20 || !candidate.matches("[A-Za-z0-9_\\-]+")) {
                    send(new Message("error", "server", null, "Invalid username", System.currentTimeMillis(), null));
                    continue; }

                nickname = candidate;
                if (clients.putIfAbsent(nickname, this) != null) {
                    send(new Message("error", "server", nickname, "Username taken", System.currentTimeMillis(), null));
                    continue; }

                send(new Message("system", "server", nickname, "Successfully registered!", System.currentTimeMillis(), null));
                log(" + Client " + color + nickname + Colors.RESET + " connected.");
                break;
            }

            String line;
            while ((line = in.readLine()) != null) {
                Message msg;
                try { msg = gson.fromJson(line, Message.class);
                } catch (Exception ex) { send(new Message("error", "server", nickname, "bad json", System.currentTimeMillis(), null)); continue; }
                handle(msg);
            }
        } catch (IOException ioe) { server.logr("IO: " + ioe.getMessage());
        } catch (Exception ex) { server.logr("Unexpected: " + ex.getMessage());
        } finally { cleanup(); }
    }

    private void cleanup() {
        if (!nickname.isBlank()) clients.remove(nickname);
        groups.forEach((g, s) -> s.remove(nickname));
        try { socket.close(); } catch (IOException ignored) {}
        log(" - Client " + color + nickname + Colors.RESET + " disconnected.");
    }
    private void handle(Message msg) {
        if (msg == null || msg.type == null) { log("n"); return; }
        switch (msg.type) {
            case "chat_request" -> handleChatReq(msg);
            case "chat_accept" -> handleChatAccept(msg);
            case "chat_close" -> handleChatClose(msg);
            case "chat" -> handleChat(msg);
            case "create_group" -> handleCreateGroup(msg);
            case "join_group" -> handleJoinGroup(msg);
            case "leave_group" -> handleLeaveGroup(msg);
            case "group_msg" -> handleGroupMsg(msg);
            case "list" -> handleList(msg);
            default -> send(new Message("error", "server", nickname, "unknown type", System.currentTimeMillis(), null));
        }
    }

    private void handleChatReq(Message msg){
        if (msg.to == null) { send(new Message("error","server",nickname,"no recipient",System.currentTimeMillis(),null)); return; }
        pendingChats.computeIfAbsent(msg.to, k -> new CopyOnWriteArraySet<>()).add(nickname);
        ConnManager dest = clients.get(msg.to);
        if (dest != null) {
            send(new Message("chat_notify","server",nickname,"Chat request sent to " + msg.to + ", waiting for acceptance...",System.currentTimeMillis(),null));
            Message notif = new Message("chat_request", nickname, msg.to, "by " + nickname + ", type \"/chat " + nickname + "\" to accept.", System.currentTimeMillis(), null);
            dest.send(notif); }
        else send(new Message("chat_error","server",nickname,"User doesn't exist",System.currentTimeMillis(),null));
}
    private void handleChatAccept(Message msg) {
        String requester = msg.to; // the user who initially requested
        if (requester == null) { send(new Message("error","server",nickname,"no target to accept",System.currentTimeMillis(),null)); return; }
        var requests = pendingChats.getOrDefault(nickname, new CopyOnWriteArraySet<>());

        if (requests.remove(requester)) {
            activeChats.computeIfAbsent(nickname, k -> new CopyOnWriteArraySet<>()).add(requester);
            activeChats.computeIfAbsent(requester, k -> new CopyOnWriteArraySet<>()).add(nickname);
            send(new Message("system_notify","server",nickname,"Chat with " + requester + " started",System.currentTimeMillis(),null));
            Message toRequester = new Message("chat_accept", nickname, requester,nickname + " accepted your request! Start chatting with /chat " + nickname + ".", System.currentTimeMillis(), null);
            ConnManager dest = clients.get(requester);
            if (dest != null) dest.send(toRequester);
        } else send(new Message("error","server",nickname,"No pending request from " + requester,System.currentTimeMillis(),null));
    }
    private void handleChatClose(Message msg) {
        if (msg.to == null) { send(new Message("error","server",nickname,"no recipient",System.currentTimeMillis(),null)); return; }
        var aSet = activeChats.getOrDefault(nickname, new CopyOnWriteArraySet<>());
        aSet.remove(msg.to);
        var bSet = activeChats.getOrDefault(msg.to, new CopyOnWriteArraySet<>());
        bSet.remove(nickname);

        ConnManager dest = clients.get(msg.to);
        if (dest != null) {
            dest.send(new Message("chat_close", nickname, msg.to, "closed chat", System.currentTimeMillis(), null));
        }
        send(new Message("system_notify","server",nickname,"chat closed with " + msg.to,System.currentTimeMillis(),null));
    }
    private void handleChat(Message msg) {
        if ("user".equalsIgnoreCase(msg.toType)) {
            var active = activeChats.getOrDefault(nickname, new CopyOnWriteArraySet<>());
            if (active.contains(msg.to)) {
                ConnManager dest = clients.get(msg.to);
                // msg.from = color + nickname + Colors.RESET;
                if (dest != null) dest.send(msg);
            } else send(new Message("error","server",nickname,"No active chat with " + msg.to,System.currentTimeMillis(),null));
        }
    }

    private void handleList(Message msg) {
        if ("users".equalsIgnoreCase(msg.target)) {
            List<String> users = new ArrayList<>(clients.keySet());
            Message reply = new Message("list","server",nickname,null,System.currentTimeMillis(),null);
            reply.items = users;
            send(reply);
        } else if ("groups".equalsIgnoreCase(msg.target)) { // tried 2 ways of sending stuff
            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, CopyOnWriteArraySet<String>> e : groups.entrySet()) {
                String g = e.getKey();
                String members = String.join(", ", e.getValue());
                lines.add(g + ": " + members); }
            send(new Message("list", "server", nickname, String.join("\n", lines), System.currentTimeMillis(), null));
        } else send(new Message("error","server",nickname,"unknown list target",System.currentTimeMillis(),null));
    }

    private void handleCreateGroup(Message msg) {
        /*if (msg.to == null) { send(new Message("error","server",nickname,"no group name",System.currentTimeMillis(),null)); return; }
        groups.computeIfAbsent(msg.to, k -> new CopyOnWriteArraySet<>()).add(nickname);
        send(new Message("create_group","server",nickname,"group created/joined: "+msg.to,System.currentTimeMillis(),null)); */
        if (msg.to == null) {
            send(new Message("group_error","server",nickname, "no group name", System.currentTimeMillis(), null)); return; }

        groups.computeIfAbsent(msg.to, k -> new CopyOnWriteArraySet<>()).add(nickname);
        send(new Message("group_joined", nickname, msg.to, msg.to+" group created and joined.", System.currentTimeMillis(), null));
    }
    private void handleJoinGroup(Message msg) {
        if (msg.to == null) { send(new Message("group_error","server",nickname,"no group name",System.currentTimeMillis(),null)); return; }
        var set = groups.get(msg.to);
        if (set == null) { send(new Message("group_error","server",nickname,msg.to+" group does not exist.",System.currentTimeMillis(),null)); return; }
        set.add(nickname);
        send(new Message("group_joined", nickname, msg.to, "joined group"+msg.to, System.currentTimeMillis(), null));
        for (String member : set) {
            if (member.equals(nickname)) continue;
            ConnManager dest = clients.get(member);
            if (dest != null) { dest.send(new Message("group_notify", nickname, msg.to, msg.from + " joined " + msg.to, System.currentTimeMillis(), null)); }
        }
        List<Message> history = groupHistory.getOrDefault(msg.to, Collections.emptyList());
        ConnManager dest = clients.get(nickname);
        if (dest != null) {
            for (Message m : history) dest.send(m);
        }
    }
    private void handleLeaveGroup(Message msg) {
        if (msg.to == null) { send(new Message("group_error","server",nickname,"no group name",System.currentTimeMillis(),null)); return; }
        var set = groups.get(msg.to);
        if (set == null || !set.remove(nickname)) { send(new Message("group_error","server",nickname, "not in group " + msg.to, System.currentTimeMillis(), null)); return; }
        if (set.isEmpty()) { groups.remove(msg.to); }
        send(new Message("group_left", nickname, msg.to, "left group " + msg.to, System.currentTimeMillis(), null));
        for (String member : set) {
            if (member.equals(nickname)) continue;
            ConnManager dest = clients.get(member);
            if (dest != null) { dest.send(new Message("group_notify", nickname, msg.to, " left " + msg.to, System.currentTimeMillis(), null)); }
        }
    }
    private void handleGroupMsg(Message msg) {
        if (msg.to == null) {
            send(new Message("group_error","server",nickname, "no group name", System.currentTimeMillis(), null));return; }
        Set<String> members = groups.get(msg.to);
        if (members == null || !members.contains(nickname)) {
            send(new Message("group_error","server",nickname, "not a member of group " + msg.to, System.currentTimeMillis(), null)); return; }

        groupHistory.computeIfAbsent(msg.to, k -> new ArrayList<>()).add(msg);
        for (String member : members) {
            if (member.equals(nickname)) continue;
            ConnManager dest = clients.get(member);
            if (dest != null) {dest.send(msg); }
        }
    }
}
