import java.net.Socket;

public class client0 {
  public static void main(String[] argv) {

    if (argv.length != 2) {
      System.err.println("usage: client0 <hostname> <hostport>");
      System.err.println("client0 should successfully request a single connection");
      System.exit(1);
    }

    try {
      TCPStart.start();

      Socket sock = new Socket(argv[0], Integer.parseInt(argv[1]));

      System.out.println("got socket "+sock);

      Thread.sleep(200);

      sock.close();
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
}
