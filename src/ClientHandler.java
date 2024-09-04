import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

public class ClientHandler implements Runnable {
  private Socket socket;
  private BufferedReader in;
  private PrintWriter out;
  private String clientId;
  private String roomId;
  private Map<String, ArrayList<ClientHandler>> roomClientMap;
  private Map<String, ArrayList<String>> enterdClientMap;

  public ClientHandler(Socket socket, Map<String, ArrayList<ClientHandler>> roomClientMap, Map<String, ArrayList<String>> enterdClientMap) {
    this.socket = socket;
    this.roomClientMap = roomClientMap;
    this.enterdClientMap = enterdClientMap;

    try {
      this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      this.out = new PrintWriter(socket.getOutputStream(), true);
    } catch (IOException var3) {
      var3.printStackTrace();
    }
  }

  @Override
  public void run() {
    while (true) {
      try {
        if (this.socket.isConnected()) {
            System.out.println("연결됨");
            String message;
            if ((message = this.in.readLine()) != null) {
              System.out.println(message);
              String[] parts = message.split("/");
              if (parts.length < 3) {
                continue;
              }

              String msg = parts[3];
              String senderId = parts[0];
              String roomId = parts[1];
              String roomname = parts[2];

              this.clientId = senderId;

              if (msg.endsWith("socket_open")) {
                this.parseAndAddClientToRooms(message);
                continue;
              }else if (msg.equals("입장")) {
                this.enterClientToRoom(roomId, senderId, roomname);
                continue;
              }else if (msg.equals("퇴장")) {
                this.removeClientFromRoom(roomId, senderId, roomname);
                continue;
              }
              int roompeople = this.enterdClientMap.get(roomId).size();
              this.broadcastToRoom(roomId, senderId, senderId + ": " + roomId + ": " + roomname + ": " + msg + ": " + roompeople);
            continue;
          }
        }
      } catch (IOException var8) {
        var8.printStackTrace();
      }

      return;
    }
  }

  private void enterClientToRoom(String roomId, String senderId, String roomname) {
    this.enterdClientMap.putIfAbsent(roomId, new ArrayList<>());
    System.out.println("Client " + senderId + " added to room " + roomId);
    if (!this.enterdClientMap.get(roomId).contains(senderId)) {
      this.enterdClientMap.get(roomId).add(senderId);
      System.out.println("Client " + senderId + " added to room " + roomId);
    }

    int roompeople = this.enterdClientMap.get(roomId).size();
    this.enterbroadcastToRoom(roomId, senderId, senderId + ": " + roomId + ": " + roomname + ": 입장: " + roompeople);
  }

  private void addClientToRoom(String roomId, ClientHandler clientHandler) {
    this.roomClientMap.putIfAbsent(roomId, new ArrayList<>());
    if (!this.roomClientMap.get(roomId).contains(clientHandler)) {
      this.roomClientMap.get(roomId).add(clientHandler);
      System.out.println("Client " + clientHandler.clientId + " added to room " + roomId);
    }
  }

  private void parseAndAddClientToRooms(String clientMessage) {
    String[] parts = clientMessage.split("/");
    String[] roomIds = parts[1].replaceAll("[\\[\\]]", "").split(", ");

    for (String roomId : roomIds) {
      this.addClientToRoom(roomId, this);
    }
  }

  private void removeClientFromRoom(String roomId, String senderId, String roomname) {
    if (roomId != null && this.enterdClientMap.containsKey(roomId)) {
      this.enterdClientMap.get(roomId).remove(senderId);
      System.out.println("Client " + senderId + " exited to room " + roomId);
      int roompeople = this.enterdClientMap.get(roomId).size();
      this.enterbroadcastToRoom(roomId, senderId, senderId + ": " + roomId + ": " + roomname + ": 퇴장: " + roompeople);
      if (this.enterdClientMap.get(roomId).isEmpty()) {
        this.enterdClientMap.remove(roomId);
      }
    }
  }

  private void broadcastToRoom(String roomId, String senderId, String message) {

    if (this.roomClientMap.containsKey(roomId)) {
      for (ClientHandler client : this.roomClientMap.get(roomId)) {
        if (!client.clientId.equals(senderId)) {
          client.sendMessage(message);
          System.out.println("00 : " + message);
        }
      }
    }
  }

  private void enterbroadcastToRoom(String roomId, String senderId, String message) {
    if (this.roomClientMap.containsKey(roomId)) {
      for (ClientHandler client : this.roomClientMap.get(roomId)) {
        client.sendMessage(message);
      }
    }
  }

  private void sendMessage(String message) {
    this.out.println(message);
  }
}
