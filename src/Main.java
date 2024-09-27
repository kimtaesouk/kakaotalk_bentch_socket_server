
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Main {
  private static final int PORT = 8080;
  private static final String ROOM_CLIENT_MAP_FILE = "roomClientMap.ser";
  private static Map<String, ArrayList<String>> roomClientMap = new HashMap<>();
  private static final Map<String, ArrayList<String>> enteredClientMap = new HashMap<>();
  private static final Map<String, ClientHandler> clientHandlerMap = new HashMap<>();

  public static void main(String[] args) {
    try {
      // 서버 시작 시 roomClientMap을 파일에서 복원, 없으면 새로 생성
      roomClientMap = loadRoomClientMap();

      InetAddress ipAddress = InetAddress.getLocalHost();
      System.out.println("Server IP: " + ipAddress.getHostAddress());
      ServerSocket serverSocket = new ServerSocket(PORT);
      System.out.println("Server is running on port " + PORT);

      while (true) {
        Socket clientSocket = serverSocket.accept();
        new Thread(new ClientHandler(clientSocket, roomClientMap, enteredClientMap, clientHandlerMap)).start();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, ArrayList<String>> loadRoomClientMap() {
    File file = new File(ROOM_CLIENT_MAP_FILE);
    if (!file.exists()) {
      System.out.println("roomClientMap file does not exist. Creating a new map.");
      return new HashMap<>();
    }

    try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
      return (Map<String, ArrayList<String>>) in.readObject();
    } catch (IOException | ClassNotFoundException e) {
      System.out.println("Error loading roomClientMap file. Creating a new map.");
      return new HashMap<>();  // 파일이 없거나 오류 발생 시 새로 생성
    }
  }
}
