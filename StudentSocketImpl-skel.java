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
  private InetAddress localSourcAddr;
  private int localSeqNumber;
  private int localSeqNumberStep;
  private int localSourcePort;
  private TCPPacket talkback;

  // In order
  // Also fuck Java's static enum shit, not proud of this fix
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

    localAckNum = 5;
    localSeqNumberStep = 8; // Uniformity
    localport = D.getNextAvailablePort();

    // Make connection, wrap the packet and shoot it out
    D.registerConnection(address, this.localport, port, this);
    initSYN = new TCPPacket(this.localport, port, localAckNum, localSeqNumberStep, false, true, false, winSize, payload);
    TCPWrapper.send(initSYN, address);

    // State printout
    stateMovement(State.CLOSED, State.SYN_SENT);

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

    System.out.println("Made it in to receive");

    switch (curState){
      case LISTEN:
         System.out.println("Made it in to LISTEN");

         if (!p.ackFlag || p.synFlag){

           localSeqNumber = p.seqNum; // Value from a wrapped TCP packet
           localSeqNumberStep = localSeqNumber + 1;
           localSourcAddr = p.sourceAddr;
           localAckNum = p.ackNum;

           talkback = new TCPPacket(localport, p.sourcePort, localAckNum, localSeqNumberStep, true, true, false, winSize, payload); 
           TCPWrapper.send(talkback, localSourcAddr);

           stateMovement(curState, State.SYN_RCVD);

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
         System.out.println("Made it in to SYN_SENT");

         if (p.synFlag || p.ackFlag){

           localSeqNumber = p.seqNum;
           localSeqNumberStep = localSeqNumber + 1;
           localSourcAddr = p.sourceAddr;
           localSourcePort = p.sourcePort;

           talkback = new TCPPacket(localport, localSourcePort, -2, localSeqNumberStep, true, false, false, winSize, payload);
           TCPWrapper.send(talkback, localSourcAddr);

           stateMovement(curState, State.ESTABLISHED);
         }

         break;

      case SYN_RCVD:
         System.out.println("Made it in to SYN_RCVD");

         if (p.ackFlag){

           localSourcePort = p.sourcePort;

           stateMovement(curState, State.ESTABLISHED);
         }

         else if (p.synFlag){

          TCPWrapper.send(talkback, localSourcAddr);
         }

         break;

      case ESTABLISHED:
         System.out.println("Made it in to ESTABLISHED");

         if (p.finFlag){

           // TODO: write function for template talkbacks
           localSeqNumber = p.seqNum;
           localSeqNumberStep = localSeqNumber + 1;
           localSourcAddr = p.sourceAddr;
           localSourcePort = p.sourcePort;

           talkback = new TCPPacket(localport, localSourcePort, -2, localSeqNumberStep, true, false, false, winSize, payload);
           TCPWrapper.send(talkback, localSourcAddr);

           stateMovement(curState, State.CLOSE_WAIT);
         }

         break;

      case FIN_WAIT_1:
         System.out.println("Made it in to FIN_WAIT_1");

         if(p.finFlag){
          localSeqNumber = p.seqNum; 
          localSeqNumberStep = localSeqNumber + 1;
          localSourcAddr = p.sourceAddr;
          localAckNum = p.ackNum;

          talkback = new TCPPacket(localport, localSourcePort, -2, localSeqNumberStep, true, false, false, winSize, payload);
          TCPWrapper.send(talkback, localSourcAddr);

          stateMovement(curState, State.CLOSING);
         }

         else if (p.ackFlag){
          stateMovement(curState, State.FIN_WAIT_2);
         }

         break;

      case FIN_WAIT_2:
         System.out.println("Made it in to FIN_WAIT_2");

         if(p.finFlag){
          localSeqNumber = p.seqNum; 
          localSeqNumberStep = localSeqNumber + 1;
          localSourcAddr = p.sourceAddr;
          localAckNum = p.ackNum;

          talkback = new TCPPacket(localport, localSourcePort, -2, localSeqNumberStep, true, false, false, winSize, payload);
          TCPWrapper.send(talkback, localSourcAddr);

          stateMovement(curState, State.TIME_WAIT);
         }

         break;

      case LAST_ACK:
         System.out.println("Made it in to LAST_ACK");

         if(p.ackFlag){
          stateMovement(curState, State.TIME_WAIT);
         }

         break;

      case CLOSE_WAIT:
         System.out.println("Made it in to CLOSE_WAIT");

         break;

      case TIME_WAIT:
         System.out.println("Made it in to TIME_WAIT");

         break;

      case CLOSING:
         System.out.println("Made it in to CLOSING");

         if(p.ackFlag){
          stateMovement(curState, State.TIME_WAIT);
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
    System.out.println("Made it in to AcceptConnection");

    D.registerListeningSocket(this.localport, this);
    stateMovement(State.CLOSED, State.LISTEN);

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

    TCPPacket end = new TCPPacket(this.localport, this.localSourcePort, localAckNum, localSeqNumberStep, false, false, true, winSize, payload);
    TCPWrapper.send(end, localSourcAddr);

    // Test for state response after final packet push
    if(curState == State.CLOSE_WAIT){
      stateMovement(curState, State.LAST_ACK);
    }
    else if(curState == State.ESTABLISHED){
      stateMovement(curState, State.FIN_WAIT_1);
    }

    //closePause(this);

    while(curState != State.CLOSED){
      try{
        wait();
       } 
       catch(InterruptedException e){
         e.printStackTrace();
       }
    }

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
    // TODO

    // this must run only once the last timer (30 second timer) has expired
    tcpTimer.cancel();
    tcpTimer = null;
  }

/*
  public synchronized void closePause(StudentSocketImpl puller){
    Thread waitToClose = new Thread(StudentSocketImpl puller, State closed){ 
        public void run(StudentSocketImpl puller, State closed){
          while (puller.returnState() != closed){
              wait();
          }
        }
    };
    waitToClose.start(this, State.CLOSED);

    return;
  }

  */

  private void stateMovement(State in, State out) {
    System.out.println("!!! " + in + "->" + out);
    curState = out;
  }

  public State returnState(){
    return curState;
  }
}
