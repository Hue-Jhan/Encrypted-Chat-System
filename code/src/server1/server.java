package server1;

import com.google.gson.Gson;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.util.concurrent.*;
import java.util.Set;
import javax.net.ssl.*;
import server1.Message.Colors;

public class server implements Runnable{
    private final int PORT = 9999;
    private static final ConcurrentMap<String, ConnManager> clients = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CopyOnWriteArraySet<String>> groups = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CopyOnWriteArraySet<String>> pendingChats = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CopyOnWriteArraySet<String>> activeChats = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CopyOnWriteArraySet<String>> activeGroups = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();

    @Override
    public void run() { }
    public server(int port) { run(port); }
    public static void main(String[] args) { new server(9999); }
    public static int getNumofClients() { return clients.size(); }

    // simple sockets
    /*public void run(int port) {
        try (ServerSocket server = new ServerSocket(port)) {
            log(" + " + Colors.GREEN + "Server running " + Colors.RESET + "on port " + port);
            while (true) {
                Socket clientSocket = server.accept();
                ConnManager cm = new ConnManager(clientSocket, clients, groups, pendingChats, activeChats, gson);
                new Thread(cm).start();
            }
        } catch (IOException e) { logr("Errore nel server: " + e.getMessage()); }
    }*/

    // tls sockets
    public void run(int port) {
        try (SSLServerSocket server = createSSLServerSocket(port)) {
            log(" + Server " + Colors.GREEN + "TLS running " + Colors.RESET + "on port " + port);
            while (true) {
                SSLSocket clientSocket = (SSLSocket) server.accept();
                clientSocket.startHandshake();
                ConnManager cm = new ConnManager(clientSocket, clients, groups, pendingChats, activeChats, activeGroups, gson );
                new Thread(cm).start(); }
        } catch (Exception e) { logr("Errore TLS server: " + e.getMessage()); }
    }
    private SSLServerSocket createSSLServerSocket(int port) throws Exception {
        char[] pass = "123456".toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream is = new FileInputStream("server.p12")) {
            ks.load(is, pass);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, pass);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        SSLServerSocketFactory factory = ctx.getServerSocketFactory();
        return (SSLServerSocket) factory.createServerSocket(port);
    }

    public static void log (String s) { System.out.println(s); }
    public static void logl(String s) { System.out.print(s);   }
    public static void logr(String s) { System.err.println(s); }
    public static void logerr(String s){System.out.println(Message.Colors.RED + s + Message.Colors.RESET);}
}
