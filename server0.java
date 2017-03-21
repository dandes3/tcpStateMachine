import java.net.Socket;
import java.net.ServerSocket;

public class server0 {
  public static void main(String[] argv) {

    if (argv.length != 1) {
      System.err.println("usage: server0 <hostport>");
      System.err.println("server0 should successfully accept a single connection");
      System.exit(1);
    }

    try {
      TCPStart.start();

      ServerSocket sock = new ServerSocket(Integer.parseInt(argv[0]));
      Socket connSock = sock.accept();

      System.out.println("got socket "+connSock);

      Thread.sleep(400);

      connSock.close();
      sock.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
