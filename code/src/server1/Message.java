package server1;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class Message {
    public String type;
    public String from;
    public String to;
    public String toType;
    public String content;
    public Long timestamp;
    public Map<String, String> metadata;
    public String target;
    public List<String> items;
    public String color;

    public Message() {}
    public Message(String type, String from, String to, String content, Long timestamp, Map<String, String> metadata) {
        this.type = type; this.from = from; this.to = to; this.content = content; this.timestamp = timestamp; this.metadata = metadata;
    }
    public Message(String type, String from, String to, Long timestamp, Map<String, String> metadata) {
        this.type = type; this.from = from; this.to = to; this.timestamp = timestamp; this.metadata = metadata;
    }
    public static String fmtHM(long epochMillis) {
        return java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault())
                .toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
    }



    public static class Colors {
        public static final String YELLOW = "\u001B[33m";
        public static final String CYAN   = "\u001B[36m";
        public static final String GREEN  = "\u001B[32m";
        public static final String PURPLE = "\u001B[35m";
        public static final String RED    = "\u001B[31m";
        public static final String BLUE   = "\u001B[34m";
        public static final String RESET  = "\u001B[0m";
    }
    public static String getColor() {
        String[] colors = { "\u001B[33m", "\u001B[36m", "\u001B[32m", "\u001B[35m", "\u001B[31m", "\u001B[34m" };
        int n = server.getNumofClients();
        if (n>colors.length) return colors[new Random().nextInt(colors.length)];
        return colors[server.getNumofClients()];
    }
    public static String fmtUser(Message msg) {
        if (msg.color == null) return msg.from;
        return msg.color + msg.from + Message.Colors.RESET;
    }
    public static String randomColor() {
        String[] colors = { "\u001B[31m", "\u001B[32m", "\u001B[33m", "\u001B[34m", "\u001B[35m", "\u001B[36m" };
        return colors[new Random().nextInt(colors.length)];
    }

}
