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
  private BufferedReader in;  // 클라이언트로부터 들어오는 데이터를 읽기 위한 BufferedReader
  private PrintWriter out;  // 클라이언트로 데이터를 전송하기 위한 PrintWriter
  private String clientId;  // 클라이언트 ID
  private String roomId;  // 클라이언트가 있는 방 ID
  private Map<String, ArrayList<String>> roomClientMap;  // 방별 클라이언트 목록 관리
  private Map<String, ArrayList<String>> enteredClientMap;  // 실시간 클라이언트 파악용
  private Map<String, ClientHandler> clientHandlerMap;  // 클라이언트 ID와 ClientHandler 매핑

  // 클라이언트가 있는 방의 리스트
  private ArrayList<String> clientsInRoom;

  // 생성자: 소켓, 방 목록, 실시간 클라이언트 목록, 클라이언트 핸들러 맵을 인자로 받아 초기화
  public ClientHandler(Socket socket, Map<String, ArrayList<String>> roomClientMap, Map<String, ArrayList<String>> enteredClientMap, Map<String, ClientHandler> clientHandlerMap) {
    this.socket = socket;
    this.roomClientMap = roomClientMap;
    this.enteredClientMap = enteredClientMap;
    this.clientHandlerMap = clientHandlerMap;  // 클라이언트와 핸들러 매핑 저장
    this.clientsInRoom = new ArrayList<>();  // 초기화된 방 내 클라이언트 목록

    try {
      // 클라이언트로부터 입력을 받을 스트림 생성
      this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      // 클라이언트에게 메시지를 보내기 위한 출력 스트림 생성
      this.out = new PrintWriter(socket.getOutputStream(), true);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void run() {
    try {
      // 소켓이 닫히지 않고 연결이 유지되는 동안 반복
      while (!socket.isClosed() && socket.isConnected()) {
        String message;
        // 클라이언트로부터 메시지를 읽음
        if ((message = this.in.readLine()) != null) {
          // 메시지를 '|' 구분자로 분리
          String[] parts = message.split("\\|" );
          if (parts.length < 3) continue;  // 메시지 형식이 잘못되면 무시

          // 각 부분을 변수로 할당
          String senderId = parts[0];  // 클라이언트 ID
          String roomId = parts[1];    // 방 ID
          String roomname = parts[2];  // 방 이름
          String msg = parts[3];       // 메시지

          this.clientId = senderId;  // 클라이언트 ID 저장

          // 클라이언트 메시지 내용에 따른 동작 분기
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
              enterClientToRoom(roomId, senderId, roomname, "입장");
              break;
            case "JOIN_ROOM" :
              // 방 참여 처리
              handleJoinRoom(senderId, roomId, roomname);
              break;

            case "Exit_Room":
              // 방 퇴장 처리
              removeClientFromRoom(roomId, senderId, roomname, "Exit_Room");
              break;
            case "퇴장":
              // 방 퇴장 처리
              removeClientFromRoom(roomId, senderId, roomname, "퇴장");
              break;

            case "차단":
              // 방에서 클라이언트 차단 처리
              removeClientToRoom(roomId, senderId);  // 차단 대상 클라이언트를 방에서 제거
              enterClientToRoom(roomId, senderId, roomname, "차단");  // 클라이언트를 다시 방에 입장시킴 (차단 해제 상황 가정)
              break;

            case "차단해제":
              // 방에서 차단 해제 처리
              handleMadeRoom(senderId, roomId);  // 차단 해제 시, 해당 클라이언트를 방에 다시 추가
              break;

            default:
              // 메시지 전송 및 읽음 처리 (기본 동작)
              System.out.println(msg);  // 서버 콘솔에 메시지 출력
              clientsInRoom = this.enteredClientMap.get(roomId);  // 현재 방에 있는 클라이언트 목록 가져오기
              String clientList = String.join(",", clientsInRoom);  // 클라이언트 목록을 쉼표로 연결된 문자열로 변환
              // 방 내 모든 클라이언트에게 메시지 브로드캐스트
              enterbroadcastToRoom(roomId, senderId, senderId + "| " + roomId + "| " + roomname + "| " + msg + "| " + clientList);
              break;
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        // 소켓이 닫혀 있지 않다면 소켓을 닫음
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
    // 클라이언트 ID와 핸들러 매핑 저장
    clientHandlerMap.put(senderId, this);
    System.out.println("Client " + senderId + " socket opened.");  // 클라이언트 연결 로그 출력
    System.out.println(clientHandlerMap.size());  // 현재 연결된 클라이언트 수 출력
  }

  // 새로운 방 생성 시 해당 방에 클라이언트를 추가
  private void handleMadeRoom(String senderId, String roomId) {
    this.addClientToRoom(roomId, senderId);  // 방에 클라이언트를 추가
    System.out.println("Client " + senderId + " created room " + roomId);  // 방 생성 로그 출력
  }

  private void handleJoinRoom(String senderId, String roomId, String roomname) {
    // 클라이언트를 roomClientMap에 추가
    this.addClientToRoom(roomId, senderId);
    System.out.println("Client " + senderId + " joined room " + roomId);

    enterAllbroadcastToRoom(roomId, senderId, senderId + "| " + roomId + "| " + roomname + "| "  + "JoIn_Room"  );
  }

  // 클라이언트를 방에 추가하고 입장 메시지 전송
  private synchronized void enterClientToRoom(String roomId, String senderId, String roomname,  String action) {
    this.enteredClientMap.putIfAbsent(roomId, new ArrayList<>());  // 방이 없으면 새로운 방을 생성
    if (!this.enteredClientMap.get(roomId).contains(senderId)) {
      this.enteredClientMap.get(roomId).add(senderId);  // 방에 클라이언트 추가
      System.out.println("Client " + senderId + " added to room " + roomId);  // 클라이언트 입장 로그 출력
    }

    clientsInRoom = this.enteredClientMap.get(roomId);  // 방에 있는 클라이언트 목록을 업데이트
    String clientList = String.join(",", clientsInRoom);  // 클라이언트 목록을 쉼표로 구분한 문자열로 변환

    // 입장 메시지를 모든 클라이언트에게 브로드캐스트

    if (action.equals("입장")){
      enterAllbroadcastToRoom(roomId, senderId, senderId + "| " + roomId + "| " + roomname + "| "  + "입장" + "| "  + clientList);
    }else if(action.equals("차단")){
      enterAllbroadcastToRoom(roomId, senderId, senderId + "| " + roomId + "| " + roomname + "| "  + "입장" + "| "  + clientList);
    }
  }

  // 클라이언트를 방에서 퇴장시키고 퇴장 메시지를 전송
  private synchronized void removeClientFromRoom(String roomId, String senderId, String roomname, String action) {
    if (roomId != null && this.enteredClientMap.containsKey(roomId)) {

      this.enteredClientMap.get(roomId).remove(senderId);  // 방에서 클라이언트 제거
      System.out.println("Client " + senderId + " exited from room " + roomId);  // 클라이언트 퇴장 로그 출력

      clientsInRoom = this.enteredClientMap.get(roomId);  // 방에 남아 있는 클라이언트 목록 업데이트

      String clientList = String.join(",", clientsInRoom);  // 클라이언트 목록을 문자열로 변환
      // 퇴장 메시지를 모든 클라이언트에게 브로드캐스트
      if (action.equals("Exit_Room")){
        enterAllbroadcastToRoom(roomId, senderId, senderId + "| " + roomId + "| " + roomname + "| "  + "Exit_Room" + "| "  + clientList);
      }else{
        enterAllbroadcastToRoom(roomId, senderId, senderId + "| " + roomId + "| " + roomname + "| "  + "퇴장" + "| "  + clientList);
      }

      if (this.enteredClientMap.get(roomId).isEmpty()) {
        this.enteredClientMap.remove(roomId);  // 방에 클라이언트가 없으면 방을 제거
      }
    }
  }

  // 방에 있는 모든 클라이언트에게 메시지 전송 (senderId 제외)
  private synchronized void broadcastToRoom(String roomId, String senderId, String message) {
    if (this.roomClientMap.containsKey(roomId)) {
      ArrayList<String> clientsInRoom = this.roomClientMap.get(roomId);  // 방에 있는 클라이언트 목록 가져오기
      for (String clientIdInRoom : clientsInRoom) {
        // senderId를 제외한 다른 클라이언트에게 메시지 전송
        ClientHandler clientHandler = this.clientHandlerMap.get(clientIdInRoom);
        if (clientHandler != null) {
          clientHandler.sendMessage(message);  // 클라이언트에게 메시지 전송
        }
      }
    }
  }

  // 읽음 처리와 같은 특정 메시지를 방에 있는 클라이언트에게 전송 (senderId 제외)
  private synchronized void enterbroadcastToRoom(String roomId, String senderId, String message) {
    if (this.roomClientMap.containsKey(roomId)) {
      ArrayList<String> clientsInRoom = this.roomClientMap.get(roomId);  // 방에 있는 클라이언트 목록 가져오기
      for (String clientIdInRoom : clientsInRoom) {
        // senderId를 제외한 클라이언트들에게 메시지 전송
        if (!clientIdInRoom.equals(senderId)) {
          ClientHandler clientHandler = this.clientHandlerMap.get(clientIdInRoom);
          if (clientHandler != null) {
            clientHandler.sendMessage(message);  // 메시지 전송
          }
        }
      }
    }
  }

  // 방에 있는 모든 클라이언트에게 메시지 전송 (senderId 포함)
  private synchronized void enterAllbroadcastToRoom(String roomId, String senderId, String message) {
    if (this.roomClientMap.containsKey(roomId)) {
      ArrayList<String> clientsInRoom = this.roomClientMap.get(roomId);  // 방에 있는 클라이언트 목록 가져오기
      for (String clientIdInRoom : clientsInRoom) {
        // 모든 클라이언트에게 메시지 전송
        System.out.println(clientIdInRoom);
        ClientHandler clientHandler = this.clientHandlerMap.get(clientIdInRoom);
        if (clientHandler != null) {
          clientHandler.sendMessage(message);  // 메시지 전송
        }
      }
    }
  }

  // 방에 클라이언트 추가
  private void addClientToRoom(String roomId, String clientId) {
    synchronized (this.roomClientMap) {
      // roomId에 해당하는 클라이언트 목록이 없으면 새로 추가
      this.roomClientMap.putIfAbsent(roomId, new ArrayList<>());

      // 중복된 클라이언트가 추가되지 않도록 확인 후 추가
      if (!this.roomClientMap.get(roomId).contains(clientId)) {
        this.roomClientMap.get(roomId).add(clientId);  // 클라이언트 추가
        System.out.println("Client " + clientId + " added to room " + roomId);  // 클라이언트 추가 로그 출력
      }
    }
  }

  // 방에서 클라이언트 제거
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

        System.out.println("Client " + clientId + " removed from room " + roomId);  // 클라이언트 제거 로그 출력
      } else {
        System.out.println("No clients found for room " + roomId);  // 해당 방에 클라이언트가 없을 경우 로그 출력
      }
    }
  }

  // 클라이언트에게 메시지 전송
  private void sendMessage(String message) {
    System.out.println("Sending message to client: " + message);  // 서버에서 클라이언트로 전송할 메시지 로그 출력
    this.out.println(message);  // 클라이언트로 메시지 전송
    this.out.flush();  // 메시지를 즉시 전송
  }
}
