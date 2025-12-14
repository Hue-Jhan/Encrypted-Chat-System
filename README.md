# Terminal Chat System
Simple chat system in java using tcp sockets with TLS encryption. Allows the creation of public groupchats and private chat tunnels.

### ⚙ How to run:

- Install JDK 24, intellij, gson-2.3.1.jar, and clone the repo in a new project;

- Compile Server and ClientMain, and run them with  ```java -jar server.jar``` and ```java -jar client.jar```.

If you want to use the TLS encrypted sockets:

- Before compiling uncomment the tls "run" function in the server, the "ClientMain" function in the client and comment the non tls ones;

- On intellij terminal run: ```keytool -genkeypair -alias chatserver -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore server.p12 -validity 3650```;

- Insert ```localhost``` as CN, then insert the file name and the password in the server function ```createSSLServerSocket```;

- Compile and insert the .p12 file in the same directory as the server jar.


# 💻 Code
This app is a simple chat system managed by a central server. The server accepts socket and creates a ConnManager class for each client which will manage the communication between clients.

Once running the app the user must register with a unique username, once the server confirms the new identity a menu showing all the options will popup:

- Listing all users/groups;
- Creating a chat with a user;
- Creating a new public groupchat;
- Joining a public groupchat;
- Quitting;
- Showing the menu again;
- Shortcut example.

How the server validates stuff...

logic examples....

message structure....



