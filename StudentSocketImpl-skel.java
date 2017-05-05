/* TCP State machine
 * by Don Andes and Quint Guvernator
 *
 * As per our original hopes, we inititally attempted to integrate and write
 * this project in Scala. While Scala does have the ability to integrate into 
 * existing Java code, it proved beyond the scope of our ability for this 
 * project. We have instead opted to fall back on the original specifications
 * and deliver our final product in Java. 
 *
 * We will try again with project three. 
 */

import java.net.*;
import java.io.*;
import java.util.Timer;

class StudentSocketImpl extends BaseSocketImpl {

  // SocketImpl data members:
  //   protected InetAddress address;
  //   protected int port;
  //   protected int localport;

  private Demultiplexer D;
  private Timer tcpTimer;

  private int winSize = 10; // Doesn't matter
  private static final byte[] noPayload = null; // Un-statify if using payloads

  private PipedOutputStream appOS;
  private PipedInputStream appIS;
  private PipedInputStream pipeAppToSocket;
  private PipedOutputStream pipeSocketToApp;
  private SocketReader reader;
  private SocketWriter writer;
  private boolean terminating = false;
  private boolean pushed = false;

  private InfiniteBuffer sendBuffer;
  private InfiniteBuffer recvBuffer;

  private int sendBufLeft;
  private int sendBufSize; 

  private int recvBufLeft;
  private int recvBufSize; 

  private int recvWindow;
  private int unAckPackTrack; 


  static final int data_bytes_per_packet = 1000;

  private TCPPacket last_packet_sent = null;


  private State curState; // Internal state of the program
  private int localAckNum; // Local copy of ackNum
  private TCPPacket talkback; // Response packet
  private int localSeqNumber;  // Local copy of SeqNum
  private int localSourcePort; // Local copy of SourcePort
  private int localSeqNumberStep; // SeqNum +1, makes calls look prettier
  private TCPPacket prevBufPack1; // Packet tracking 
  private TCPPacket prevBufPack2; // Packet tracking
  private TCPPacket prevBufPack3; // Packet tracking
  private InetAddress localSourcAddr; // Our interface

  private int counter = 1; // Fix for a re-transmission

  // Ridiculous Enumeration of States for switch statement while preserving printability (at least it looks pretty)
  enum State { 
    CLOSED      {@Override public String toString(){return "CLOSED";}}, 
    LISTEN      {@Override public String toString(){return "LISTEN";}}, 
    CLOSING     {@Override public String toString(){return "CLOSING";}},
    SYN_SENT    {@Override public String toString(){return "SYN_SENT";}}, 
    SYN_RCVD    {@Override public String toString(){return "SYN_RCVD";}}, 
    LAST_ACK    {@Override public String toString(){return "LAST_ACK";}}, 
    TIME_WAIT   {@Override public String toString(){return "TIME_WAIT";}},
    CLOSE_WAIT  {@Override public String toString(){return "CLOSE_WAIT";}},  
    FIN_WAIT_1  {@Override public String toString(){return "FIN_WAIT_1";}}, 
    FIN_WAIT_2  {@Override public String toString(){return "FIN_WAIT_2";}}, 
    ESTABLISHED {@Override public String toString(){return "ESTABLISHED";}}
  }

  StudentSocketImpl(Demultiplexer D) {  // default constructor
    this.D = D;

    try {
      pipeAppToSocket = new PipedInputStream();
      pipeSocketToApp = new PipedOutputStream();
      
      appIS = new PipedInputStream(pipeSocketToApp);
      appOS = new PipedOutputStream(pipeAppToSocket);
    }
    catch(IOException e){
      System.err.println("unable to create piped sockets");
      System.exit(1);
    }


    initBuffers();

    reader = new SocketReader(pipeAppToSocket, this);
    reader.start();

    writer = new SocketWriter(pipeSocketToApp, this);
    writer.start();
  }

  /**
   * initialize buffers and set up sequence numbers
   */
  private void initBuffers(){
    // Buffers are made and trackers are set to keep endpoints on the current buffer size 
    //  amount of space that is left to write to.
    sendBuffer = new InfiniteBuffer();
    sendBufSize = sendBuffer.getBufferSize();
    sendBufLeft = sendBufSize;

    recvBuffer = new InfiniteBuffer();
    recvBufSize = recvBuffer.getBufferSize();
    recvBufLeft = recvBufSize;
  }

  /**
   * Basically a nice little wrapper function that protects the inherently unsafe *infinite* circular buffer. 
   * Wraps all attempts at appending with a safety fallout if the buffer tries to circle around and overwrite. 
   */
  private synchronized void attemptAppend(boolean sendBuf, byte[] buffer, int length){

    if(sendBuf){
      //System.out.println("In attemptAppend send");
      if ((sendBufLeft - sendBufSize) < 0){
        System.out.println("Buffer circled around, panic and throw stuff.");
        return;
      }

      sendBufLeft -= length;
      sendBuffer.append(buffer, 0, length);
      //System.out.println(sendBufLeft);
      return;
    }
    else{
      //System.out.println("In attemptAppend recv");
      if ((recvBufLeft - recvBufSize) < 0){
        System.out.println("Buffer circled around, panic and throw stuff.");
        return;
      }

      recvBufLeft -= length;
      recvBuffer.append(buffer, 0, length);
      //System.out.println(recvBufLeft);
      return;
    }
  }

  /**
 * Basically a nice little wrapper function that protects the inherently unsafe *infinite* circular buffer. 
 * Wraps all attempts at reading with a safety fallout if the buffer tries to read garbage data. 
 */
  private synchronized byte[] attemptRead(boolean sendBuf, byte[] buffer, int length){
    
    
    if(sendBuf){
      //System.out.println("In attemptRead send");
      if ((length == 0) || ((sendBufLeft + length) > sendBufSize)) { // Control for bogus length of read 0
        System.out.println("Reading too far or given length of zero. I can't believe you've done this.");
        return(null);
      }

      sendBufLeft += length; 
      sendBuffer.copyOut(buffer, sendBuffer.getBase(), length);
      sendBuffer.advance(length);
      return(buffer);
    }
    else{
      //System.out.println("In attemptRead recv");
      if ((length == 0) || ((recvBufLeft + length) > recvBufSize)) { // Control for bogus length of read 0
        System.out.println("Reading too far or given length of zero. I can't believe you've done this.");
        return(null);
      }

      recvBufLeft += length; 
      recvBuffer.copyOut(buffer, recvBuffer.getBase(), length);
      recvBuffer.advance(length);
      return(buffer);
    }

  }

  synchronized void sendData() {
    // TODO: Figure out how to set up a tracking space
    //System.out.println("In sendData");

    int sentSpace = -1;
    if (recvWindow > 0){ sentSpace = 0;}

    while(unAckPackTrack <= 7 && ((sendBufSize - sendBufLeft) > 0) && sentSpace < recvWindow){
      int packSize = 1000;

      //System.out.println(packSize);
      //System.out.println(sendBufSize - sendBufLeft);
      //System.out.println(recvWindow - sentSpace);

      if (packSize > (sendBufSize - sendBufLeft)){ packSize = (sendBufSize - sendBufLeft);}
      //if (packSize > (recvWindow - sentSpace)){ packSize = (recvWindow - sentSpace);}

      
      //System.out.println(packSize);

      byte[] passer = new byte[packSize];
      byte[] payload = attemptRead(true, passer, packSize);
    
      // Throws gotten string at screen after decoding
      String puller = new String(payload);
      //System.out.println("This is the string being inserted into the packet");
      //System.out.println(puller);

      wrapAndSend(false, prevBufPack1, localport, localSourcePort, localSeqNumber, localAckNum, false, false, false, localSourcAddr, noPayload);
      //TCPPacket payloadPacket = new TCPPacket(localport, port, seqNum, ackNum, false, false, false, recvBufLeft, payload);

      sentSpace += packSize;
      seqNum += packSize;
      unAckPackTrack ++;
      String plaintext = new String(payload);

      /** Please god work */
      //sendPacket(payloadPacket, false);
      //System.out.println("Attempted a packet send with data");

    }
    notifyAll();

  }
  /**
   * Called by the application-layer code to copy data out of the 
   * recvBuffer into the application's space.
   * Must block until data is available, or until terminating is true
   * @param buffer array of bytes to return to application
   * @param length desired maximum number of bytes to copy
   * @return number of bytes copied (by definition > 0)
   */
  synchronized int getData(byte[] buffer, int length){
    //System.out.println("In getData");
    while ((recvBufSize - recvBufLeft) == 0){
      try {wait();} 
      catch (InterruptedException e){e.printStackTrace();}
    }

    int minReaderVal = recvBufSize - recvBufLeft; 
    if (length < minReaderVal) {
      minReaderVal = length;
    }

    //System.out.println(minReaderVal);

    buffer = attemptRead(false, buffer, minReaderVal);
    
    // Throws gotten string at screen after decoding
    String puller = new String(buffer);
    //System.out.println(puller);

    notifyAll();
    return minReaderVal;
  }

  /**
   * accept data written by application into sendBuffer to send.
   * Must block until ALL data is written.
   * @param buffer array of bytes to copy into app
   * @param length number of bytes to copy 
   */
  synchronized void dataFromApp(byte[] buffer, int length){
    //System.out.println("In dataFromApp");
    while (sendBufLeft == 0){
      //System.out.println("In dataFromApp wait loop");
      try {wait();} 
      catch (InterruptedException e){e.printStackTrace();}
    }
    //System.out.println(sendBufLeft);

    attemptAppend(true, buffer, length);

    //buffer = attemptRead(true, buffer, length);
    
    // Throws gotten string at screen after decoding
    //String puller = new String(buffer);
    //System.out.println(puller);


    //System.out.println(sendBufLeft);

    if (terminating){pushed = true;}

    sendData();
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

    // First packet initialization
    TCPPacket initSYN;

    counter = counter -1; // Weird double send fix

    localAckNum = 3; // 100% arbitrary
    localSeqNumberStep = 6; // Uniformity in arbitrarity 
    localSourcAddr = address;
    localport = D.getNextAvailablePort();

    // Make connection, wrap the packet and shoot it out
    D.registerConnection(address, this.localport, port, this);
    wrapAndSend(false, prevBufPack1, this.localport, port, localAckNum, localSeqNumberStep, false, true, false, localSourcAddr, noPayload);

    // State printout
    curState = stateMovement(State.CLOSED, State.SYN_SENT);
    
    // Step 3 
    while (curState != State.ESTABLISHED){
      try{
       wait();
      } 
      catch(Exception e){
         e.printStackTrace();
      }
    }
  }

  //TODO: sometimes gets stuck sending packets on server side when in LAST_ACK on server and CLOSED on client
  
  /**
   * Called by Demultiplexer when a packet comes in for this connection
   * @param p The packet that arrived
   */
  public synchronized void receivePacket(TCPPacket p){

  /* Nintendo */ switch (curState){ // Sometimes I think I'm funny 
      case LISTEN:

         if (!p.ackFlag && p.synFlag){

           localSeqNumber = p.seqNum; // Value from a wrapped TCP packet
           localSeqNumberStep = localSeqNumber + 1;
           localSourcAddr = p.sourceAddr;
           localAckNum = p.ackNum;

           wrapAndSend(false, prevBufPack1, localport, p.sourcePort, localAckNum, localSeqNumberStep, true, true, false, localSourcAddr, noPayload); 
           curState = stateMovement(curState, State.SYN_RCVD);

           // Compiler required try/catch block
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

         if (p.synFlag && p.ackFlag){

           killTCPTimer();

           localSeqNumber = p.seqNum;
           localSeqNumberStep = localSeqNumber + 1;
           localSourcAddr = p.sourceAddr;
           localSourcePort = p.sourcePort;

           wrapAndSend(false, prevBufPack1, localport, localSourcePort, -2, localSeqNumberStep, true, false, false, localSourcAddr, noPayload);
           localSourcePort = p.sourcePort;
           curState = stateMovement(curState, State.ESTABLISHED);
         }
         break;

      case SYN_RCVD:

         if (p.ackFlag){
           killTCPTimer();
           localSourcePort = p.sourcePort;
           curState = stateMovement(curState, State.ESTABLISHED);
         }

         else if (p.synFlag){
          wrapAndSend(true, prevBufPack1, 0, 0, 0, 0, false, false, false, localSourcAddr, noPayload);
         }
         break;

      case ESTABLISHED:

         if (p.finFlag){

           localSeqNumber = p.seqNum;
           localSeqNumberStep = localSeqNumber + 1;
           localSourcAddr = p.sourceAddr;
           localSourcePort = p.sourcePort;

           wrapAndSend(false, prevBufPack1, localport, localSourcePort, -2, localSeqNumberStep, true, false, false, localSourcAddr, noPayload);
           curState = stateMovement(curState, State.CLOSE_WAIT);
         }

         else if (p.ackFlag && p.synFlag){
          wrapAndSend(false, prevBufPack2, localport, localSourcePort, -2, localSeqNumberStep, true, false, false, localSourcAddr, noPayload);
         }
         break;

      case FIN_WAIT_1:

         if (p.ackFlag){
          if(p.synFlag){
            wrapAndSend(true, prevBufPack2, 0, 0, 0, 0, false, false, false, localSourcAddr, noPayload);
          }
          else{
            curState = stateMovement(curState, State.FIN_WAIT_2);
            killTCPTimer();
          }
         }

         if(p.finFlag){
          localSeqNumber = p.seqNum; 
          localSeqNumberStep = localSeqNumber + 1;
          localSourcAddr = p.sourceAddr;
          localAckNum = p.ackNum;
          localSourcePort = p.sourcePort;

          wrapAndSend(false, prevBufPack1, localport, localSourcePort, -2, localSeqNumberStep, true, false, false, localSourcAddr, noPayload);

          curState = stateMovement(curState, State.CLOSING);
         }
         break;

      case FIN_WAIT_2:

         if(p.finFlag){
          localSeqNumberStep = localSeqNumber + 1;
          localAckNum = p.ackNum;
          localSourcePort = p.sourcePort;

          wrapAndSend(false, prevBufPack1, localport, localSourcePort, -2, localSeqNumberStep, true, false, false, localSourcAddr, noPayload);
          curState = stateMovement(curState, State.TIME_WAIT);

          createTimerTask(30 * 1000, null);
         }
         break;

      case LAST_ACK:

         if (p.ackFlag){
          killTCPTimer();
          curState = stateMovement(curState, State.TIME_WAIT);
          createTimerTask(30 * 1000, null);
         }

         if (p.finFlag){
          wrapAndSend(true, prevBufPack2, 0, 0, 0, 0, false, false, false, localSourcAddr, noPayload);
         } 
         break;

      case CLOSE_WAIT:

         if (p.finFlag){
          wrapAndSend(true, prevBufPack2, 0, 0, 0, 0, false, false, false, localSourcAddr, noPayload);         
         }
         break;

      case TIME_WAIT:

          try {
            if (p.finFlag){
              wrapAndSend(true, prevBufPack2, 0, 0, 0, 0, false, false, false, localSourcAddr, noPayload);
            }
          } catch (Exception e) {
              // This is a really bad catch. This should literally never happen
              e.printStackTrace();
            }
         break;

      case CLOSING:

          if(p.ackFlag){
            killTCPTimer();
            curState = stateMovement(curState, State.TIME_WAIT);
            createTimerTask(30 * 1000, null);
          }

          else if (p.finFlag){
            wrapAndSend(true, prevBufPack2, 0, 0, 0, 0, false, false, false, localSourcAddr, noPayload);
          }
         break;

      default:
         break;
    }

    // Call around to all waiting and update 
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
    return appIS;
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
    return appOS;
  }

  /**
   * Closes this socket. 
   *
   * @exception  IOException  if an I/O error occurs when closing this socket.
   */
  public synchronized void close() throws IOException {

    wrapAndSend(false, prevBufPack1, this.localport, this.localSourcePort, localAckNum, localSeqNumberStep, false, false, true, localSourcAddr, noPayload);

    // Test for state response after final packet push
    if(curState == State.CLOSE_WAIT){
      curState = stateMovement(curState, State.LAST_ACK);
    }
    else if(curState == State.ESTABLISHED){
      curState = stateMovement(curState, State.FIN_WAIT_1);
    }

    // As per specifications, this allows a prolonged wait on the thread while still immediately returning (via threading)
    try{
     CloseThread kill = new CloseThread(this);
     kill.run();
    } 
    catch (Exception e){
      e.printStackTrace();
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

    try{
      killTCPTimer();
    } 
    catch (Exception e){
      System.out.println("State is " + curState);
    }

    if(curState == State.TIME_WAIT){
      try {
        curState = stateMovement(curState, State.CLOSED);      
      } 
      catch (Exception e) {
        notifyAll();
      }

      // Force a wakeup just in case we encounter any errors that killed the last one
      notifyAll(); 

      // Compiler required try/catch block
      try {
           D.unregisterConnection(localSourcAddr, localport, localSourcePort, this);
      } 
      catch (Exception e) {
        e.printStackTrace();
      }
    }

    else{
      wrapAndSend(true, prevBufPack1, 0, 0, 0, 0, false, false, false, localSourcAddr, noPayload);
    }
  }

  // Visualization at terminal of state transitions
  public State stateMovement(State in, State out) {
    System.out.println("!!! " + in + "->" + out);
    return out;
  }

  // Get and workaround for getting close State
  public State returnState(boolean currentState){
    if(currentState){
      return curState;
    }
    else{
      return State.CLOSED;
    }
  }

 /**
  * Packing, building, and sending hybrid function
  *  In first case, builds a packed out of the supplied values and sends it to the given address
  *  In second case, sends a pre-saved buffer packet to the specified address. When called in this fashion all other values are blanked to 0 and false respectively
  */
  public void wrapAndSend(boolean prePack, TCPPacket passed, int sourcePortP, int destPortP, int seqNumP, int ackNumP, boolean first, boolean second, boolean third, InetAddress sendTo, byte[] carrier){

    // For some reason after the connection is naturally shut down, it calls another instance of wrapAndSend
    // This is a "temporary" fix (read: not temporary at all)
    if(curState == State.CLOSED && counter > 0){
      notifyAll();
      return;
    }

    // Looks like little fishies ^_^
    if(prePack){
      System.out.println("<>< RE-SENDING DROPPED PACKET <><");
    }

    counter = counter + 1; // Un-breaks a false wrapAndSend call after natural termination
    TCPPacket push;

    if(prePack){
      push = passed;
    }
    else{
      push = new TCPPacket(sourcePortP, destPortP, seqNumP, ackNumP, first, second, third, recvBufLeft, carrier);
    }

    TCPWrapper.send(push, sendTo);

    if (!push.ackFlag || push.synFlag){
      prevBufPack1 = push;
      createTimerTask(1000, null);
    }
    
    else{
      prevBufPack2 = push;
    }
  }

  // Because these two lines were getting repetitive
  public void killTCPTimer(){
    tcpTimer.cancel();
    tcpTimer = null;
  }

  // That's all folks!
}

/**
 * Extension of a threading run class
 *  allows the calling thread the immediately return to its parent function
 *  while performing a wait() call untilt the thread closes itself
 */
class CloseThread implements Runnable{

  public StudentSocketImpl threadToKill;
  public CloseThread(StudentSocketImpl passed) throws InterruptedException{
    this.threadToKill = passed;
  }
  
  // Keep calling home and checking status against CLOSED and wait() until done 
  public void run(){ 
    while (threadToKill.returnState(true) != threadToKill.returnState(false)){
      try {
        threadToKill.wait();
      } 
      // General catch all
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}


