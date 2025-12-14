# Terminal Chat System
Simple terminal-based chat application in Java. It uses a central server and supports private chats, public group chats, and TLS-encrypted communication over TCP sockets.
The system is designed to work across multiple devices on the same network.

### ⚙ How to run:

- Install JDK 24, intellij, clone the repo in a new project and add gson-2.3.1.jar to dependencies;

- Compile Server and ClientMain, and run them with  ```java -jar server.jar``` and ```java -jar client.jar```.

### 🔒 TLS Encryption
If you want to use the TLS encrypted sockets:

- Before compiling comment out the plain ServerSocket logic in the server and the plain Socket constructor in ClientMain, then uncomment the TLS-based SSLServerSocket logic and the SSLSocket logic;

- On intellij terminal run: ```keytool -genkeypair -alias chatserver -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore server.p12 -validity 3650```;

- Insert ```localhost``` as CN, then insert the file name and the password in the server function ```createSSLServerSocket```;

- Compile and insert the .p12 file in the same directory as the server jar.


# 💻 Code
This app is a simple chat system managed by a central server which uses a central ServerSocket/SSLServerSocket, for each client connection a ConnManager instance is created, which runs in its own thread and maintains shared state using thread-safe structures (Connected clients, Active chats, Group memberships, Pending chat requests). All communication uses JSON messages serialized with Gson.

The server implements a simple validation logic which makes sure that usernames are unique, commands are validated before execution, groups exist when asked to be joined, and chat requests get an explicit acceptance. Also any disconnection triggers cleanup, which closes active chats, updates group membership, and removes clients from server state.

Each client uses a dedicated reader thread for incoming messages and a main thread handles basic user input/commands. Communication is fully asynchronous, and internal queues separate: Chat messages, Group messages and System events.

# 🗨 Chat
Once running the app the user must register with a unique username, once the server confirms the new identity a menu showing all the options will popup:

- Listing all users/groups;
- Creating a chat with a user;
- Creating a new public group chat;
- Joining a public groupchat;
- Quitting a chat, group, or the program entirely;
- Showing the menu again;
- Shortcut example (short aliases are supported, like /l, /g, /c).
