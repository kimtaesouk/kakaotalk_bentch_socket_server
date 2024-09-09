import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
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
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void run() {
    try {
      while (!socket.isClosed() && socket.isConnected()) {
        String message;
        if ((message = this.in.readLine()) != null) {
          System.out.println("Received: " + message);
          String[] parts = message.split("/");
          if (parts.length < 3) {
            continue;  // 메시지 포맷이 올바르지 않으면 무시
          }

          String senderId = parts[0];
          String roomId = parts[1];
          String roomname = parts[2];
          String msg = parts[3];

          this.clientId = senderId;

          if (msg.endsWith("socket_open")) {
            this.parseAndAddClientToRooms(message);
          } else if (msg.equals("입장")) {
            this.enterClientToRoom(roomId, senderId, roomname);
          } else if (msg.equals("퇴장")) {
//            this.removeClientFromRoom(roomId, senderId, roomname);
          } else {
            int roompeople = this.enterdClientMap.get(roomId).size();
            this.broadcastToRoom(roomId, senderId, senderId + ": " + roomId + ": " + roomname + ": " + msg + ": " + roompeople);
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        if (!socket.isClosed()) {
          socket.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private synchronized void enterClientToRoom(String roomId, String senderId, String roomname) {
    this.enterdClientMap.putIfAbsent(roomId, new ArrayList<>());
    if (!this.enterdClientMap.get(roomId).contains(senderId)) {
      this.enterdClientMap.get(roomId).add(senderId);
      System.out.println("Client " + senderId + " added to room " + roomId);
    }

    int roompeople = this.enterdClientMap.get(roomId).size();
    this.enterbroadcastToRoom(roomId, senderId, senderId + ": " + roomId + ": " + roomname + ": 입장: " + roompeople);
  }

  private synchronized void removeClientFromRoom(String roomId, String senderId, String roomname) {
    if (roomId != null && this.enterdClientMap.containsKey(roomId)) {
      this.enterdClientMap.get(roomId).remove(senderId);
      System.out.println("Client " + senderId + " exited from room " + roomId);

      int roompeople = this.enterdClientMap.get(roomId).size();
      this.enterbroadcastToRoom(roomId, senderId, senderId + ": " + roomId + ": " + roomname + ": 퇴장: " + roompeople);

      if (this.enterdClientMap.get(roomId).isEmpty()) {
        this.enterdClientMap.remove(roomId);
      }
    }
  }

  private void addClientToRoom(String roomId, ClientHandler clientHandler) {
    synchronized (this.roomClientMap) {
      this.roomClientMap.putIfAbsent(roomId, new ArrayList<>());
      if (!this.roomClientMap.get(roomId).contains(clientHandler)) {
        this.roomClientMap.get(roomId).add(clientHandler);
        System.out.println("Client " + clientHandler.clientId + " added to room " + roomId);
      }
    }
  }

  private void parseAndAddClientToRooms(String clientMessage) {
    String[] parts = clientMessage.split("/");
    String[] roomIds = parts[1].replaceAll("[\\[\\]]", "").split(", ");
    for (String roomId : roomIds) {
      this.addClientToRoom(roomId, this);
    }
  }

  private synchronized void broadcastToRoom(String roomId, String senderId, String message) {
    System.out.println("Broadcasting message to room: " + roomId + " Message: " + message);  // 로그 추가
    if (this.roomClientMap.containsKey(roomId)) {
      for (ClientHandler client : this.roomClientMap.get(roomId)) {
        if (!client.clientId.equals(senderId)) {
          System.out.println("Sending to client: " + client.clientId);
          client.sendMessage(message);
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
    System.out.println("Sending message to client: " + message);  // 로그 추가
    this.out.println(message);
    this.out.flush();  // 명시적으로 flush 호출
  }

}
