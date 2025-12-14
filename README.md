# Terminal Chat System
Simple chat systems in java using tcp sockets

### How to run:

- Install JDK 24, intellij, gson-2.3.1.jar, and clone the repo in a new project;

- Compile Server and ClientMain, and run them with  ```java -jar server.jar``` and ```java -jar client.jar```.

If you want to use the TLS encrypted sockets:

1) Before compiling uncomment the tls "run" function in the server, the "ClientMain" function in the client and comment the non tls ones;

2) On intellij terminal run: ```keytool -genkeypair -alias chatserver -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore server.p12 -validity 3650```;

3) Insert ```localhost``` as CN, then insert the file name and the password in the server function ```createSSLServerSocket```;

4) Compile and insert the .p12 file in the same directory as the server jar.



# 💻 Code
a
