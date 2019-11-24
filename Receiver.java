import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.io.OutputStream; 
import java.io.FileOutputStream; 
import java.util.Arrays;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/*
 * Project 3
 * Create a new protocol taking less space than TCP
 * @author  Aseem Mehta   (am1435@rit.edu)
 * 
 */


public class Receiver implements Runnable {

   public int port = 63001; // port to listen on
   public String receiveAddress; // IP address of receiver
   public int rover = 0; // the arbitrary rover number of this executable
   public static int window = 10240; // Max size of window
   public int MTU = 1024; // Max size of one packet
   public static int maxPacket = 0; // Max number of packets sent on line
   public static int currentPacket = 0; // Current packet number between 0 and MaxPacket
   public static List<Integer> packets = new ArrayList<>(); //contains list of dropped packets
   public static byte[] bigBuffer = new byte[window]; // Temporary storage till MaxPackets are received
   public String fileName; // name of file 
   
   // standard constructor
   public Receiver(int thePort, String receiveIp, int roverNum, int window, int MTU,String fileName)
   {
      port = thePort; 
      receiveAddress = receiveIp;
      rover = roverNum;
      this.window = window;
      this.MTU = MTU;
      this.fileName = fileName;
   }

//   Brain of the receiver, identifies the action to be taken when a packet is received
   public void deduce(byte[] msgBytes) {
	   byte[] replyBytes = new byte[4];
	   if(msgBytes[2]==36) { // Handshaking happens here if Falgs REP and SYN are set
		   try {
			   maxPacket = window/MTU;
			   // creates a reply message
			   replyBytes = Sender.createPacketHeader(0,msgBytes[0]+1,20,maxPacket);
			   Sender.sendUdpMessage(replyBytes);
		   }catch(Exception ex){
			   ex.printStackTrace();
		   }
	   }else if(msgBytes[2]==20) { // receives the handshake reply with ACK and SYN flag set
		   Sender.status = Arrays.copyOfRange(msgBytes,0,4);
		   // ends the handshake in Sender loop
		   Sender.handshakeTTLEnd = System.currentTimeMillis();
	   }else if(msgBytes[2] < 4) { // enters if no flag or FIN is set
		   if (currentPacket+1 != (msgBytes[0]&0xFF) && (currentPacket+1!=(maxPacket+1))) {
			   // if packet doesn't match the expected packet, it is stored in packets list
			   packets.add(currentPacket+1);
		   }
		   if(packets.contains(msgBytes[0]&0xFF)) {
			   // removes packets from nak list(packets), if packet is received
			   packets.remove(packets.indexOf(msgBytes[0]&0xFF));
		   }
		   currentPacket = msgBytes[0]&0xFF;		   
		   String location = "./RoverStorage/Rover"+rover+"/";
		   String fileLocation = location+fileName;
		   File fileOutput = new File(fileLocation);
		   try {
			   // temp storage to store one packet
			   byte[] tempByte = Arrays.copyOfRange(msgBytes,4,msgBytes.length);
			   // stores temp storage in bigBuffer
			   addInBuffer(tempByte,(currentPacket-1)*(MTU-4),(currentPacket-1)*(MTU-4)+msgBytes.length-4);			   
			   if(currentPacket == maxPacket || msgBytes[2] == 2) {
				   // enters when all expected packets arrive 
				   
				   if(packets.size()==0) {// sends ACK if all packets in a set are received
					   // stores bytes in system storage when complete set is received
					   byte[] tempWrite = Arrays.copyOfRange(bigBuffer,0,(maxPacket)*(MTU-4));
					   OutputStream os = new FileOutputStream(fileOutput,true);
					   os.write(tempWrite);
					   os.close();
					   Arrays.fill(bigBuffer, (byte)0);
					   
					   replyBytes = Sender.createPacketHeader(0,maxPacket+1,16,maxPacket);
					   currentPacket = 0; // resets the counter of current packet
					   packets.removeAll(packets); // resets Nak list
					   Sender.sendUdpMessage(replyBytes);
					   
				   }else {
					   System.out.println("Sending NAK's " + packets);
					   for (Integer packetNotReceived : packets) {
						   replyBytes = Sender.createPacketHeader(0,packetNotReceived,8,maxPacket);						   
						   Sender.sendUdpMessage(replyBytes);
					   }
				   }
				   
			   }else if (msgBytes[2] == 1||msgBytes[2] == 3){ // used when finish flag is received
				   byte[] tempWrite = Arrays.copyOfRange(bigBuffer,0,(currentPacket-1)*(MTU-4)+msgBytes.length-4);
				   OutputStream os = new FileOutputStream(fileOutput,true);
				   os.write(tempWrite);
				   os.close();
				   replyBytes = Sender.createPacketHeader(0,maxPacket+1,17,maxPacket);
				   Sender.sendUdpMessage(replyBytes);
			   }
			   
			   
		   }catch(IOException e){
				e.printStackTrace();
		   }
	   }else if(msgBytes[2]==16  || msgBytes[2]==17){// breaks loop in sender when ACK/FIN is received
		   Sender.packetRecieved = false;
	   }else if(msgBytes[2]==8){ // informs sender when NAK is received
		   Sender.nakPackets.add(msgBytes[1]&0xFF);
		   Sender.nakReceived = true;
	   }
   }
   
   public void addInBuffer(byte[] tempByte, int start, int end) {
	   // stores temp arrays in big buffer
	   System.arraycopy(tempByte, 0, bigBuffer, start, end-start);
   }
   
   // listens to the ipaddress and reports when a message arrived
   public void receiveUDPMessage() throws
         IOException {
      byte[] buffer=new byte[1024];

      // create and initialize the socket
      DatagramSocket socket=new DatagramSocket(port);
      InetAddress group=InetAddress.getByName(receiveAddress);
      
      while(true){
         DatagramPacket packet=new DatagramPacket(buffer,buffer.length);

         socket.receive(packet);
         String msg=new String(packet.getData(),packet.getOffset(),packet.getLength());

         deduce(Arrays.copyOfRange(packet.getData(),0,packet.getLength()));
         
         // give us a way out if needed
         if("EXIT".equals(msg)) {
            System.out.println("No more messages. Exiting : "+msg);
            break;
         }
      }

      //close up ship
      socket.close();
   }

   // the thread runnable.  just starts listening.
   @Override
   public void run(){
     try {
       receiveUDPMessage();
     }catch(IOException ex){
       ex.printStackTrace();
     }
   }
}
