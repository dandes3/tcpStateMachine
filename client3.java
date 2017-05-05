import java.net.*;
import java.io.*;

public class client3 {
  public static void main(String[] argv){
    
    if(argv.length!= 2){
      System.err.println("usage: client3 <hostname> <hostport>");
      System.exit(1);
    }

    try{
      TCPStart.start();
      
      Socket sock = new Socket(argv[0], Integer.parseInt(argv[1]));

      System.out.println("got socket "+sock);

      BufferedReader in = new BufferedReader
	(new InputStreamReader(sock.getInputStream()));
      PrintWriter out = new PrintWriter(sock.getOutputStream(), true);

      out.println("hello");
      System.out.println("sent hello");

      String reply = in.readLine();
      System.out.println("query: "+reply);
      
      out.println("fine, thanks.  and you?");
      System.out.println("Sent fine");

      reply = in.readLine();
      System.out.println("final: "+reply);

      for(int i=0;i<250;i++)
	out.println(i);

      for(int i=0;i<250;i++){
	int num = Integer.parseInt(in.readLine().trim());
	if(num!= i)
	  System.err.println("error: "+i+" != "+num);
      }
      
      
      sock.close();
      
    }
    catch(Exception e){
      System.err.println("Caught exception:");
      e.printStackTrace();
    }
  }
}
