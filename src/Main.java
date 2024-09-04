import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Main {
  private static final int PORT = 4040;
  private static final Map<String, ArrayList<ClientHandler>> roomClientMap = new HashMap<>();
  private static final Map<String, ArrayList<String>> enterdClientMap = new HashMap<>();

  public static void main(String[] args) {
    try {
      InetAddress ipAddress = InetAddress.getLocalHost();
      System.out.println("Server IP: " + ipAddress.getHostAddress());
      ServerSocket serverSocket = new ServerSocket(PORT);
      System.out.println("Server is running on port " + PORT);

      while (true) {
        Socket clientSocket = serverSocket.accept();
        new Thread(new ClientHandler(clientSocket, roomClientMap, enterdClientMap)).start();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
