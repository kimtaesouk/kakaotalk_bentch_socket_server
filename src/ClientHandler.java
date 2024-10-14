import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {
  private Socket socket;  // 클라이언트와 서버 간의 소켓 연결
  private BufferedReader in;  // 클라이언트로부터 데이터를 읽어들이는 스트림
  private PrintWriter out;  // 서버에서 클라이언트로 데이터를 보내는 스트림
  private String clientId;  // 클라이언트의 고유 ID
  private String roomId;  // 클라이언트가 입장한 방의 ID
  private Map<String, ArrayList<String>> roomClientMap;  // 방별 클라이언트 목록을 관리하는 맵
  private Map<String, ArrayList<String>> enteredClientMap;  // 방에 실시간으로 입장한 클라이언트들을 관리하는 맵
  private Map<String, ClientHandler> clientHandlerMap;  // 클라이언트 ID와 해당 ClientHandler를 매핑하는 맵

  // 클라이언트가 속한 방의 클라이언트 목록을 저장
  private ArrayList<String> clientsInRoom;

  // 생성자: 소켓과 방 목록, 클라이언트 목록을 받아 초기화
  public ClientHandler(Socket socket, Map<String, ArrayList<String>> roomClientMap, Map<String, ArrayList<String>> enteredClientMap, Map<String, ClientHandler> clientHandlerMap) {
    this.socket = socket;
    this.roomClientMap = roomClientMap;  // 방별 클라이언트 목록을 관리하는 맵
    this.enteredClientMap = enteredClientMap;  // 방에 입장한 클라이언트 목록을 관리하는 맵
    this.clientHandlerMap = clientHandlerMap;  // 클라이언트 ID와 핸들러를 매핑하는 맵
    this.clientsInRoom = new ArrayList<>();  // 방에 있는 클라이언트 목록 초기화

    try {
      // 클라이언트로부터 입력을 받을 수 있는 스트림 생성
      this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      // 클라이언트에게 메시지를 보낼 수 있는 출력 스트림 생성
      this.out = new PrintWriter(socket.getOutputStream(), true);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void run() {
    try {
      // 소켓이 닫히지 않고 연결된 상태에서 계속 반복
      while (!socket.isClosed() && socket.isConnected()) {
        String message;
        // 클라이언트로부터 메시지를 읽어옴
        if ((message = this.in.readLine()) != null) {
          // 메시지를 '|'로 구분하여 각 정보를 추출
          String[] parts = message.split("\\|");
          // 메시지 형식이 잘못되었으면 무시
          if (parts.length < 3) continue;

          // 메시지에서 각 요소 추출
          String senderId = parts[0];  // 클라이언트 ID (발신자)
          String roomId = parts[1];    // 방 ID
          String roomname = parts[2];  // 방 이름
          String msg = parts[3];       // 클라이언트가 보낸 메시지 내용

          this.clientId = senderId;  // 현재 클라이언트 ID 저장

          // 클라이언트가 보낸 메시지 내용에 따라 다른 작업을 수행
          switch (msg) {
            case "socket_open":
              // 클라이언트가 처음 소켓 연결을 열었을 때 처리
              handleSocketOpen(senderId);
              break;

            case "made_room":
              // 클라이언트가 방을 새로 생성했을 때 처리
              handleMadeRoom(senderId, roomId);
              break;

            case "입장":
              // 클라이언트가 방에 입장했을 때 처리
              handleMadeRoom(senderId, roomId);  // 클라이언트를 방에 추가
              enterClientToRoom(roomId, senderId, roomname);  // 방 입장 처리
              break;

            case "퇴장":
              // 클라이언트가 방에서 퇴장했을 때 처리
              removeClientFromRoom(roomId, senderId, roomname);  // 방 퇴장 처리
              break;

            case "차단":
              // 클라이언트가 다른 클라이언트를 차단했을 때 처리
              removeClientToRoom(roomId, senderId);  // 차단 대상 클라이언트 방에서 제거
              enterClientToRoom(roomId, senderId, roomname);  // 차단 해제 후 다시 입장 처리
              break;

            case "차단해제":
              // 클라이언트가 차단을 해제했을 때 처리
              handleMadeRoom(senderId, roomId);  // 차단 해제 후 방에 재입장 처리
              break;

            default:
              // 그 외 메시지 (대화 내용 등) 처리
              System.out.println(msg);  // 서버 로그에 메시지 출력
              clientsInRoom = this.enteredClientMap.get(roomId);  // 방에 있는 클라이언트 목록 가져옴
              String clientList = String.join(",", clientsInRoom);  // 클라이언트 목록을 쉼표로 구분한 문자열로 변환
              enterbroadcastToRoom(roomId, senderId, senderId + "| " + roomId + "| " + roomname + "| " + msg + "| " + clientList);  // 다른 클라이언트들에게 메시지 전송
              break;
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        // 소켓이 닫히지 않았으면 닫음
        if (!socket.isClosed()) {
          socket.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  // 클라이언트가 소켓 연결을 열었을 때 처리
  private void handleSocketOpen(String senderId) {
    // 클라이언트 ID와 해당 핸들러를 매핑하여 저장
    clientHandlerMap.put(senderId, this);
    System.out.println("Client " + senderId + " socket opened.");  // 로그 출력
    System.out.println(clientHandlerMap.size());  // 현재 연결된 클라이언트 수 출력
  }

  // 클라이언트가 방을 새로 생성했을 때 처리
  private void handleMadeRoom(String senderId, String roomId) {
    // 클라이언트를 방에 추가
    this.addClientToRoom(roomId, senderId);
    System.out.println("Client " + senderId + " created room " + roomId);  // 로그 출력
  }

  // 클라이언트가 방에 입장했을 때 처리
  private synchronized void enterClientToRoom(String roomId, String senderId, String roomname) {
    // 방에 클라이언트 목록이 없으면 생성
    this.enteredClientMap.putIfAbsent(roomId, new ArrayList<>());
    // 클라이언트가 해당 방에 아직 입장하지 않았으면 목록에 추가
    if (!this.enteredClientMap.get(roomId).contains(senderId)) {
      this.enteredClientMap.get(roomId).add(senderId);
      System.out.println("Client " + senderId + " added to room " + roomId);  // 로그 출력
    }

    // 방에 있는 클라이언트 목록을 가져옴
    clientsInRoom = this.enteredClientMap.get(roomId);
    // 클라이언트 목록을 쉼표로 구분한 문자열로 변환
    String clientList = String.join(",", clientsInRoom);

    // 방에 있는 모든 클라이언트들에게 입장 메시지 전송
    enterAllbroadcastToRoom(roomId, senderId, senderId + "| " + roomId + "| " + roomname + "| " + "입장" + "| " + clientList);
  }

  // 클라이언트가 방에서 퇴장했을 때 처리
  private synchronized void removeClientFromRoom(String roomId, String senderId, String roomname) {
    // 방 ID가 유효하고 방에 클라이언트가 있는 경우에 처리
    if (roomId != null && this.enteredClientMap.containsKey(roomId)) {
      // 클라이언트가 방에서 퇴장
      this.enteredClientMap.get(roomId).remove(senderId);
      System.out.println("Client " + senderId + " exited from room " + roomId);  // 로그 출력

      // 방에 남아있는 클라이언트 목록 가져옴
      clientsInRoom = this.enteredClientMap.get(roomId);

      // 클라이언트 목록을 쉼표로 구분한 문자열로 변환
      String clientList = String.join(",", clientsInRoom);
      // 방에 있는 모든 클라이언트들에게 퇴장 메시지 전송
      enterAllbroadcastToRoom(roomId, senderId, senderId + "| " + roomId + "| " + roomname + "| " + "퇴장" + "| " + clientList);

      // 방에 클라이언트가 더 이상 없으면 방 제거
      if (this.enteredClientMap.get(roomId).isEmpty()) {
        this.enteredClientMap.remove(roomId);
      }
    }
  }

  // 방에 있는 다른 클라이언트들에게 메시지를 브로드캐스트 (senderId 제외)
  private synchronized void enterbroadcastToRoom(String roomId, String senderId, String message) {
    if (this.roomClientMap.containsKey(roomId)) {
      // 방에 있는 클라이언트 목록 가져옴
      ArrayList<String> clientsInRoom = this.roomClientMap.get(roomId);
      // 각 클라이언트에게 메시지를 전송
      for (String clientIdInRoom : clientsInRoom) {
        // senderId가 아닌 클라이언트에게만 메시지 전송
        ClientHandler clientHandler = this.clientHandlerMap.get(clientIdInRoom);
        if (clientHandler != null) {
          clientHandler.sendMessage(message);
        }
      }
    }
  }

  // 방에 있는 모든 클라이언트에게 특정 메시지 브로드캐스트 (senderId 포함)
  private synchronized void enterAllbroadcastToRoom(String roomId, String senderId, String message) {
    if (this.roomClientMap.containsKey(roomId)) {
      // 방에 있는 클라이언트 목록 가져옴
      ArrayList<String> clientsInRoom = this.roomClientMap.get(roomId);
      // 모든 클라이언트에게 메시지 전송
      for (String clientIdInRoom : clientsInRoom) {
        ClientHandler clientHandler = this.clientHandlerMap.get(clientIdInRoom);
        if (clientHandler != null) {
          clientHandler.sendMessage(message);
        }
      }
    }
  }

  // 클라이언트를 방에 추가
  private void addClientToRoom(String roomId, String clientId) {
    synchronized (this.roomClientMap) {
      // 방에 클라이언트 목록이 없으면 생성
      this.roomClientMap.putIfAbsent(roomId, new ArrayList<>());

      // 클라이언트가 이미 방에 존재하지 않는 경우에만 추가
      if (!this.roomClientMap.get(roomId).contains(clientId)) {
        this.roomClientMap.get(roomId).add(clientId);  // 클라이언트 추가
        System.out.println("Client " + clientId + " added to room " + roomId);  // 로그 출력
      }
    }
  }

  // 클라이언트를 방에서 제거
  private void removeClientToRoom(String roomId, String clientId) {
    synchronized (this.roomClientMap) {
      // 방에 있는 클라이언트 목록을 가져옴
      List<String> clients = this.roomClientMap.get(roomId);

      // 클라이언트가 방에 있으면 제거
      if (clients != null) {
        clients.remove(clientId);

        // 클라이언트 목록이 비어 있으면 방 자체를 제거
        if (clients.isEmpty()) {
          this.roomClientMap.remove(roomId);
        }

        System.out.println("Client " + clientId + " removed from room " + roomId);  // 로그 출력
      } else {
        System.out.println("No clients found for room " + roomId);  // 방에 클라이언트가 없을 경우 로그 출력
      }
    }
  }

  // 클라이언트에게 메시지 전송
  private void sendMessage(String message) {
    System.out.println("Sending message to client: " + message);  // 서버 로그에 메시지 출력
    this.out.println(message);  // 클라이언트로 메시지 전송
    this.out.flush();  // 즉시 전송
  }
}
