import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {
  private Socket socket;
  private BufferedReader in;
  private PrintWriter out;
  private String clientId;
  private String roomId;
  private Map<String, ArrayList<String>> roomClientMap;  // 방별 클라이언트 목록 관리
  private Map<String, ArrayList<String>> enteredClientMap;  // 실시간 클라이언트 파악용
  private Map<String, ClientHandler> clientHandlerMap;  // 클라이언트 ID와 ClientHandler 매핑

  // 클라이언트가 있는 방의 리스트
  private ArrayList<String> clientsInRoom;

  public ClientHandler(Socket socket, Map<String, ArrayList<String>> roomClientMap, Map<String, ArrayList<String>> enteredClientMap, Map<String, ClientHandler> clientHandlerMap) {
    this.socket = socket;
    this.roomClientMap = roomClientMap;
    this.enteredClientMap = enteredClientMap;
    this.clientHandlerMap = clientHandlerMap;  // 클라이언트와 핸들러 매핑 저장
    this.clientsInRoom = new ArrayList<>();

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
          String[] parts = message.split("/");
          if (parts.length < 3) continue;

          String senderId = parts[0];  // 클라이언트 ID
          String roomId = parts[1];    // 방 ID
          String roomname = parts[2];  // 방 이름
          String msg = parts[3];       // 메시지

          this.clientId = senderId;

          switch (msg) {
            case "socket_open":
              // 클라이언트가 연결될 때 처리
              handleSocketOpen(senderId);
              break;

            case "made_room":
              // 방이 새로 생성될 때 처리
              handleMadeRoom(senderId, roomId);
              break;

            case "입장" :
              // 방 입장 처리
              handleMadeRoom(senderId, roomId);
              enterClientToRoom(roomId, senderId, roomname);
              break;

            case "퇴장":
              // 방 퇴장 처리
              removeClientFromRoom(roomId, senderId, roomname);
              break;

            case "차단":
              // 방에서 클라이언트 차단 처리
              removeClientToRoom(roomId, senderId);
              enterClientToRoom(roomId, senderId, roomname);
              break;

            case "차단해제":
              // 방에서 차단 해제 처리
              handleMadeRoom(senderId,roomId);
              break;

            default:
              // 메시지 전송 및 읽음 처리
              clientsInRoom = this.enteredClientMap.get(roomId);
              String clientList = String.join(",", clientsInRoom);  // 방에 있는 클라이언트 목록
              enterbroadcastToRoom(roomId, senderId, senderId + ": " + roomId + ": " + roomname + ": " + msg + ": " + clientList);
              break;
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

  private void handleSocketOpen(String senderId) {
    // 클라이언트가 소켓 연결을 열면 ClientHandler 매핑
    clientHandlerMap.put(senderId, this);
    System.out.println("Client " + senderId + " socket opened.");
    System.out.println(clientHandlerMap.size());
  }

  private void handleMadeRoom(String senderId, String roomId) {
    // 새로운 방 생성 시 해당 방에 클라이언트를 추가
    this.addClientToRoom(roomId, senderId);
    System.out.println("Client " + senderId + " created room " + roomId);
  }

  private synchronized void enterClientToRoom(String roomId, String senderId, String roomname) {
    // enteredClientMap에 클라이언트 추가
    this.enteredClientMap.putIfAbsent(roomId, new ArrayList<>());
    if (!this.enteredClientMap.get(roomId).contains(senderId)) {
      this.enteredClientMap.get(roomId).add(senderId);
      System.out.println("Client " + senderId + " added to room " + roomId);
    }

    clientsInRoom = this.enteredClientMap.get(roomId);
    String clientList = String.join(",", clientsInRoom);  // 클라이언트 리스트

    // 입장 메시지 전송
    broadcastToRoom(roomId, senderId, senderId + ": " + roomId + ": " + roomname + ": 입장: " + clientList);
  }

  private synchronized void removeClientFromRoom(String roomId, String senderId, String roomname) {
    if (roomId != null && this.enteredClientMap.containsKey(roomId)) {
      this.enteredClientMap.get(roomId).remove(senderId);
      System.out.println("Client " + senderId + " exited from room " + roomId);

      clientsInRoom = this.enteredClientMap.get(roomId);
      String clientList = String.join(",", clientsInRoom);

      // 퇴장 메시지 전송
      enterbroadcastToRoom(roomId, senderId, senderId + ": " + roomId + ": " + roomname + ": 퇴장: " + clientList);

      if (this.enteredClientMap.get(roomId).isEmpty()) {
        this.enteredClientMap.remove(roomId);
      }
    }
  }

  private synchronized void broadcastToRoom(String roomId, String senderId, String message) {
    // 방에 있는 모든 클라이언트에게 메시지 전송 (senderId 제외)
    if (this.roomClientMap.containsKey(roomId)) {
      ArrayList<String> clientsInRoom = this.roomClientMap.get(roomId);
      for (String clientIdInRoom : clientsInRoom) {
        // senderId를 제외한 다른 클라이언트에게 메시지 전송
        ClientHandler clientHandler = this.clientHandlerMap.get(clientIdInRoom);
        if (clientHandler != null) {
          clientHandler.sendMessage(message);
        }
      }
    }
  }


  private synchronized void enterbroadcastToRoom(String roomId, String senderId, String message) {
    // 읽음 처리와 같은 특정 메시지를 방에 있는 클라이언트에게 전송
    if (this.roomClientMap.containsKey(roomId)) {
      ArrayList<String> clientsInRoom = this.roomClientMap.get(roomId);
      for (String clientIdInRoom : clientsInRoom) {
        // senderId를 제외한 클라이언트들에게 메시지 전송
        if (!clientIdInRoom.equals(senderId)) {
          ClientHandler clientHandler = this.clientHandlerMap.get(clientIdInRoom);
          if (clientHandler != null) {
            clientHandler.sendMessage(message);
          }
        }
      }
    }
  }

  private void addClientToRoom(String roomId, String clientId) {
    synchronized (this.roomClientMap) {
      // roomId에 해당하는 클라이언트 목록이 없으면 새로 추가
      this.roomClientMap.putIfAbsent(roomId, new ArrayList<>());

      // 중복된 클라이언트가 추가되지 않도록 확인 후 추가
      if (!this.roomClientMap.get(roomId).contains(clientId)) {
        this.roomClientMap.get(roomId).add(clientId);
        System.out.println("Client " + clientId + " added to room " + roomId);
      }
    }
  }

  private void removeClientToRoom(String roomId, String clientId) {
    synchronized (this.roomClientMap) {
      // roomId에 해당하는 클라이언트 목록을 가져옴
      List<String> clients = this.roomClientMap.get(roomId);

      if (clients != null) {
        // 클라이언트가 roomId에 존재하면 삭제
        clients.remove(clientId);

        // 클라이언트 목록이 비어 있으면 roomId 자체를 제거
        if (clients.isEmpty()) {
          this.roomClientMap.remove(roomId);
        }

        System.out.println("Client " + clientId + " removed from room " + roomId);
      } else {
        System.out.println("No clients found for room " + roomId);
      }
    }
  }

  private void sendMessage(String message) {
    System.out.println("Sending message to client: " + message);
    this.out.println(message);
    this.out.flush();
  }
}
