import java.net.*;
import java.io.*;

public class server3 {
  public static void main(String[] argv){
    
    if(argv.length!= 1){
      System.err.println("usage: server3 <hostport>");
      System.exit(1);
    }

    try{
      TCPStart.start();
      
      ServerSocket sock = new ServerSocket(Integer.parseInt(argv[0]));
      Socket connSock = sock.accept();

      System.out.println("got socket "+connSock);

      BufferedReader in = new BufferedReader
	(new InputStreamReader(connSock.getInputStream()));
      PrintWriter out = new PrintWriter(connSock.getOutputStream(), true);

      String request = in.readLine();
      System.out.println("request: "+request);

      out.println("how are you?");
      System.out.println("sent how are you?");

      String reply = in.readLine();
      System.out.println("answer: "+reply);

      out.println("great.  good bye!");
      System.out.println("sent goodbye");

      for(int i=0;i<250;i++){
	int num = Integer.parseInt(in.readLine().trim());
	if(num!= i)
	  System.err.println("error: "+i+" != "+num);
      }

      for(int i=0;i<250;i++)
	out.println(i);

      connSock.close();
      sock.close();

    }
    catch(Exception e){
      System.err.println("Caught exception "+e);
    }
  }
}
