import java.net.*;
import java.io.*;
import java.util.Timer;

class StudentSocketImpl extends BaseSocketImpl {

  // SocketImpl data members:
  //   protected InetAddress address;
  //   protected int port;
  //   protected int localport;

  private Demultiplexer D; // Given
  private Timer tcpTimer;  // Given

  private static final int winSize = 5;
  private static final byte[] payload = null; // Un-statify if using payloads
  private State curState;
  private int localAckNum;
  private TCPPacket talkback;
  private int localSeqNumber;
  private int localSourcePort;
  private int localSeqNumberStep;
  private InetAddress localSourcAddr;

  private TCPPacket lastPack;
  private TCPPacket lastAck;


  // In order
  // Also fuck Java's static enum shit
  enum State { 
    CLOSED{@Override public String toString(){return "CLOSED";}}, 
    LISTEN{@Override public String toString(){return "LISTEN";}}, 
    SYN_SENT{@Override public String toString(){return "SYN_SENT";}}, 
    SYN_RCVD{@Override public String toString(){return "SYN_RCVD";}}, 
    ESTABLISHED{@Override public String toString(){return "ESTABLISHED";}}, 
    FIN_WAIT_1{@Override public String toString(){return "FIN_WAIT_1";}}, 
    CLOSE_WAIT{@Override public String toString(){return "CLOSE_WAIT";}}, 
    FIN_WAIT_2{@Override public String toString(){return "FIN_WAIT_2";}}, 
    LAST_ACK{@Override public String toString(){return "LAST_ACK";}}, 
    TIME_WAIT{@Override public String toString(){return "TIME_WAIT";}}, 
    CLOSING{@Override public String toString(){return "CLOSING";}}
  }

  StudentSocketImpl(Demultiplexer D) {  // default constructor
    this.D = D;
    curState = State.CLOSED;
  }

  /**
   * Connects this socket to the specified port number on the specified host.
   *
   * @param      address   the IP address of the remote host.
   * @param      port      the port number.
   * @exception  IOException  if an I/O error occurs when attempting a
   *               connection.
   */
  public synchronized void connect(InetAddress address, int port) throws IOException{
    TCPPacket initSYN;

    localAckNum = 3;
    localSeqNumberStep = 8; // Uniformity
    localport = D.getNextAvailablePort();

    // Make connection, wrap the packet and shoot it out
    D.registerConnection(address, this.localport, port, this);

    wrapAndSend(false, lastPack, this.localport, port, localAckNum, localSeqNumberStep, false, true, false, address);

    // State printout
    curState = stateMovement(State.CLOSED, State.SYN_SENT);
    
    // Step 3 
    while (curState != State.ESTABLISHED){
      try{
       wait();
      } 
      catch(InterruptedException e){
         e.printStackTrace();
      }
    }
  }
  
  /**
   * Called by Demultiplexer when a packet comes in for this connection
   * @param p The packet that arrived
   */
  public synchronized void receivePacket(TCPPacket p){

    //System.out.println("Made it in to receive");

    switch (curState){
      case LISTEN:
         //System.out.println("Made it in to LISTEN");

         if (!p.ackFlag && p.synFlag){

           localSeqNumber = p.seqNum; // Value from a wrapped TCP packet
           localSeqNumberStep = localSeqNumber + 1;
           localSourcAddr = p.sourceAddr;
           localAckNum = p.ackNum;

           wrapAndSend(false, lastPack, localport, p.sourcePort, localAckNum, localSeqNumberStep, true, true, false, localSourcAddr); 

           curState = stateMovement(curState, State.SYN_RCVD);

           // Keeps bugging for this try/catch
           // bleh
           try{
            D.unregisterListeningSocket(localport, this);
            D.registerConnection(localSourcAddr, localport, p.sourcePort, this);
           } 
           catch(IOException e){
            e.printStackTrace();
           }
         }

         break;

      case SYN_SENT:
         //System.out.println("Made it in to SYN_SENT");

         if (p.synFlag && p.ackFlag){

           killTCPTimer();

           localSeqNumber = p.seqNum;
           localSeqNumberStep = localSeqNumber + 1;
           localSourcAddr = p.sourceAddr;
           localSourcePort = p.sourcePort;

           
           wrapAndSend(false, lastPack, localport, localSourcePort, -2, localSeqNumberStep, true, false, false, localSourcAddr);

           localSourcePort = p.sourcePort;

           curState = stateMovement(curState, State.ESTABLISHED);
         }

         break;

      case SYN_RCVD:
         //System.out.println("Made it in to SYN_RCVD");

         if (p.ackFlag){

           killTCPTimer();

           localSourcePort = p.sourcePort;

           curState = stateMovement(curState, State.ESTABLISHED);
         }

         else if (p.synFlag){
          wrapAndSend(false, lastPack, this.localport, this.localSourcePort, localAckNum, localSeqNumberStep, false, false, true, localSourcAddr);
         }

         break;

      case ESTABLISHED:
         //System.out.println("Made it in to ESTABLISHED");

         if (p.finFlag){

           localSeqNumber = p.seqNum;
           localSeqNumberStep = localSeqNumber + 1;
           localSourcAddr = p.sourceAddr;
           localSourcePort = p.sourcePort;

           wrapAndSend(false, lastPack, localport, localSourcePort, -2, localSeqNumberStep, true, false, false, localSourcAddr);

           curState = stateMovement(curState, State.CLOSE_WAIT);
         }

         else if (p.ackFlag && p.synFlag){
          wrapAndSend(false, lastAck, localport, localSourcePort, -2, localSeqNumberStep, true, false, false, localSourcAddr);
         }

         break;

      case FIN_WAIT_1:
         //System.out.println("Made it in to FIN_WAIT_1");

         if (p.ackFlag && p.synFlag){
          wrapAndSend(true, lastAck, 0, 0, 0, 0, false, false, false, localSourcAddr);
         }

         if(p.finFlag){
          localSeqNumber = p.seqNum; 
          localSeqNumberStep = localSeqNumber + 1;
          localSourcAddr = p.sourceAddr;
          localAckNum = p.ackNum;

          wrapAndSend(false, lastPack, localport, localSourcePort, -2, localSeqNumberStep, true, false, false, localSourcAddr);

          curState = stateMovement(curState, State.CLOSING);
         }

         else if (p.ackFlag){
          curState = stateMovement(curState, State.FIN_WAIT_2);
          killTCPTimer();
         }

         break;

      case FIN_WAIT_2:
         //System.out.println("Made it in to FIN_WAIT_2");

         if(p.finFlag){
          localSeqNumber = p.seqNum; 
          localSeqNumberStep = localSeqNumber + 1;
          localSourcAddr = p.sourceAddr;
          localAckNum = p.ackNum;

          wrapAndSend(false, lastPack, localport, localSourcePort, -2, localSeqNumberStep, true, false, false, localSourcAddr);

          curState = stateMovement(curState, State.TIME_WAIT);

          createTimerTask(30 * 1000, null);
         }

         break;

      case LAST_ACK:
         //System.out.println("Made it in to LAST_ACK");

         if (p.finFlag){
          wrapAndSend(true, lastAck, 0, 0, 0, 0, false, false, false, localSourcAddr);
         } 

         if(p.ackFlag){

          killTCPTimer();

          curState = stateMovement(curState, State.TIME_WAIT);
          createTimerTask(30 * 1000, null);
         }

         break;

      case CLOSE_WAIT:
         //System.out.println("Made it in to CLOSE_WAIT");

         if (p.finFlag){
          wrapAndSend(false, lastAck, localport, localSourcePort, -2, localSeqNumberStep, true, false, false, localSourcAddr);
         }

         break;

      case TIME_WAIT:
         //System.out.println("Made it in to TIME_WAIT");

         if (p.finFlag){
          wrapAndSend(false, lastAck, localport, localSourcePort, -2, localSeqNumberStep, true, false, false, localSourcAddr);
         }

         break;

      case CLOSING:
         //System.out.println("Made it in to CLOSING");

         if (p.finFlag){
          wrapAndSend(true, lastAck, 0, 0, 0, 0, false, false, false, localSourcAddr);
         }

         if(p.ackFlag){

          killTCPTimer();

          curState = stateMovement(curState, State.TIME_WAIT);
          createTimerTask(30 * 1000, null);
         }

         break;

      default:
         break;
    }

    this.notifyAll();

  }

  /** 
   * Waits for an incoming connection to arrive to connect this socket to
   * Ultimately this is called by the application calling 
   * ServerSocket.accept(), but this method belongs to the Socket object 
   * that will be returned, not the listening ServerSocket.
   * Note that localport is already set prior to this being called.
   */
  public synchronized void acceptConnection() throws IOException {
    //System.out.println("Made it in to AcceptConnection");

    D.registerListeningSocket(this.localport, this);
    curState = stateMovement(State.CLOSED, State.LISTEN);

    // Step 3
  
    while (curState != State.ESTABLISHED) {
      try {
        wait();
      } 
      catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  
  /**
   * Returns an input stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an 
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     a stream for reading from this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               input stream.
   */
  public InputStream getInputStream() throws IOException {
    // project 4 return appIS;
    return null;
    
  }

  /**
   * Returns an output stream for this socket.  Note that this method cannot
   * create a NEW InputStream, but must return a reference to an 
   * existing InputStream (that you create elsewhere) because it may be
   * called more than once.
   *
   * @return     an output stream for writing to this socket.
   * @exception  IOException  if an I/O error occurs when creating the
   *               output stream.
   */
  public OutputStream getOutputStream() throws IOException {
    // project 4 return appOS;
    return null;
  }


  /**
   * Closes this socket. 
   *
   * @exception  IOException  if an I/O error occurs when closing this socket.
   */
  public synchronized void close() throws IOException {

    wrapAndSend(false, lastPack, this.localport, this.localSourcePort, localAckNum, localSeqNumberStep, false, false, true, localSourcAddr);

    // Test for state response after final packet push
    if(curState == State.CLOSE_WAIT){
      curState = stateMovement(curState, State.LAST_ACK);
    }
    else if(curState == State.ESTABLISHED){
      curState = stateMovement(curState, State.FIN_WAIT_1);
    }

    CloseThread kill = new CloseThread(this);
    kill.run();

    return;
  }

  /** 
   * create TCPTimerTask instance, handling tcpTimer creation
   * @param delay time in milliseconds before call
   * @param ref generic reference to be returned to handleTimer
   */
  private TCPTimerTask createTimerTask(long delay, Object ref){
    if(tcpTimer == null)
      tcpTimer = new Timer(false);
    return new TCPTimerTask(tcpTimer, delay, this, ref);
  }


  /**
   * handle timer expiration (called by TCPTimerTask)
   * @param ref Generic reference that can be used by the timer to return 
   * information.
   */
  public synchronized void handleTimer(Object ref){

    killTCPTimer();

    if(curState == State.TIME_WAIT){

      curState = stateMovement(curState, State.CLOSED);
      this.notifyAll();

      try {
           D.unregisterConnection(localSourcAddr, localport, localSourcePort, this);
      } catch (IOException e) {
          e.printStackTrace();
      }
      
    }

    else{
      wrapAndSend(true, lastPack, 0, 0, 0, 0, false, false, false, localSourcAddr);
    }

  }

  private State stateMovement(State in, State out) {
    System.out.println("!!! " + in + "->" + out);
    return out;
  }

  public State returnState(){
    return curState;
  }

  public State returnClosed(){
    return State.CLOSED;
  }

  private void wrapAndSend(boolean prePack, TCPPacket passed, int sourcePortP, int destPortP, int seqNumP, int ackNumP, boolean first, boolean second, boolean third, InetAddress sendTo){

    TCPPacket push;

    if(prePack){
      push = passed;
    }
    else{
      push = new TCPPacket(sourcePortP, destPortP, seqNumP, ackNumP, first, second, third, winSize, payload);
    }

    TCPWrapper.send(push, sendTo);

    if (!push.ackFlag || push.synFlag){
      lastPack = push;
      createTimerTask(1000, null);
    }
    
    else
      lastAck = push;
  }

  public void killTCPTimer(){
    tcpTimer.cancel();
    tcpTimer = null;
  }

}

class CloseThread implements Runnable {

    private StudentSocketImpl threadToKill;

    public CloseThread(StudentSocketImpl passed){
      this.threadToKill = passed;
    }
    
    public void run(){
      while (threadToKill.returnState() != threadToKill.returnClosed()){
        synchronized(threadToKill){
        try {
          threadToKill.wait();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        }
      }
    }
}


