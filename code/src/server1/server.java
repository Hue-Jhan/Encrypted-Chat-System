package server1;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.Set;
import server1.Message.Colors;

public class server implements Runnable{
    private final int PORT = 9999;
    private static final ConcurrentMap<String, ConnManager> clients = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CopyOnWriteArraySet<String>> groups = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CopyOnWriteArraySet<String>> pendingChats = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CopyOnWriteArraySet<String>> activeChats = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();

    @Override
    public void run() { }
    public server(int port) { run(port); }
    public static void main(String[] args) { new server(9999); }
    public static int getNumofClients() { return clients.size(); }

    public void run(int port) {
        try (ServerSocket server = new ServerSocket(port)) {
            log(" + " + Colors.GREEN + "Server attivo " + Colors.RESET + "su " + port);
            while (true) {
                Socket clientSocket = server.accept();
                ConnManager cm = new ConnManager(clientSocket, clients, groups, pendingChats, activeChats, gson);
                new Thread(cm).start();
            }
        } catch (IOException e) { logr("Errore nel server: " + e.getMessage()); }
    }

    public static void log (String s) { System.out.println(s); }
    public static void logl(String s) { System.out.print(s);   }
    public static void logr(String s) { System.err.println(s); }
    public static void logerr(String s){System.out.println(Message.Colors.RED + s + Message.Colors.RESET);}
}
