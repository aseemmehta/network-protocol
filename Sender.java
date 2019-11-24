import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.io.OutputStream; 
import java.io.FileOutputStream; 
import java.lang.Math; 
import java.util.ArrayList;
import java.util.List;

/*
 * Project 3
 * Create a new protocol taking less space than TCP
 * @author  Aseem Mehta   (am1435@rit.edu)
 * 
 */

public class Sender implements Runnable  {

   public static int destinationPort = 63001; // destiantion Port of receiver
   public int sourcePort = 63001; // source Port of sender
   public static String sendToAddress; // IP address to send on
   public int rover = 0; // the arbitrary rover number of this executable
   public String action; // if it is receiver or sender
   public static volatile long TTLStart; // handshake packet time start
   public static volatile long handshakeTTLEnd; // handshake packet time end
//   public static String allPackets = "";
   public int maxBytes = 1020; // Max bytes of data file
   public static byte[] status = new byte[4]; //header values if received packets
   public static volatile boolean packetRecieved = true; // ACK received status
   public static volatile boolean nakReceived = false; // Nak received status
   public static List<Integer> nakPackets = new ArrayList<>(); // list of nak packets
   public String fileName; // name of file

   // standard constructor
   public Sender(int srcPort, int destPort, String sendToIP, int roverNum, String act, int MTU,String fileName)
   {
	  sourcePort = srcPort;
	  destinationPort = destPort;
      sendToAddress = sendToIP;
      rover = roverNum;
      action = act;
      maxBytes = MTU - 4;
      this.fileName = fileName;
   }
  
   // Send the new protocol message
   public static void sendUdpMessage(byte[] msg) throws IOException {
      // Socket setup
      DatagramSocket socket = new DatagramSocket();
      InetAddress sendIPAddress = InetAddress.getByName(sendToAddress);
      
      // Packet setup
      DatagramPacket packet = new DatagramPacket(msg, msg.length, sendIPAddress, destinationPort);
      socket.send(packet);
      socket.close();
   }
   
//   Function creates the header
   public static byte[] createPacketHeader(int seqNumber, int ackNumber, int flag, int windowSize) {
	   byte[] header = new byte[4];
	   header[0] = (byte)(seqNumber); // Sequence Number
	   header[1] = (byte)(ackNumber); // Acknoledgement Number
	   header[2] = (byte)(flag); // FLAGS
	   header[3] = (byte)(windowSize); // window size
	   return header ;
   }
   
//   performs handshake with the receiver
   public void performHandshake(){
	   try {
		   byte[] handshakepacket = new byte[4];
		   byte[] handshakePacket = createPacketHeader(0,0,36,0);
		   
		   TTLStart = System.currentTimeMillis();
		   handshakeTTLEnd = TTLStart;
		   sendUdpMessage(handshakePacket);
		   
		   boolean keepgoing = true;
		   while(keepgoing) {
			   
			   if(handshakeTTLEnd > TTLStart) {
				   keepgoing = false;
			   }
			   else if((System.currentTimeMillis() - TTLStart)>1000) {
				   TTLStart = System.currentTimeMillis();
				   handshakeTTLEnd = TTLStart;
				   sendUdpMessage(handshakePacket);
			   }
		   }
   		}catch(Exception ex){
   			ex.printStackTrace();
   		}
   }
   
//   joins two byte arrays
   public byte[] joinTwoBytes(byte[] value1,byte[] value2) {
	   byte[] fileMessage = new byte[value2.length+4];
	   for (int i = 0; i < fileMessage.length; ++i)
	   {
		   if(i<value1.length) {
			   fileMessage[i] = value1[i];
		   }else {
			   fileMessage[i] = value2[i - value1.length];
		   }
	   }
	   return fileMessage;
   }
   
// Starts sending file to receiver   
   public void sendFile() {
	   String location = "./RoverStorage/Rover"+rover+"/";
	   String fileLocation = location+fileName;
	   int maxPacket = status[3]; // maximum amount of packet could be sent in one go
	   File file = new File(fileLocation);
	   try {
		   	FileInputStream fis = new FileInputStream(file);
			int fileSize = fis.available();
			// finds the maximum number of packets it would take to send the data to receiver
			double maxPacketNumber = Math.ceil((double)fileSize/(double)maxBytes);
			byte[] fileByteArray = new byte[fileSize];
			fis.read(fileByteArray); // Reading file bytes in fileByteArray
			byte[] header = new byte[4];
			int seqNumber = 1;
			header = createPacketHeader(seqNumber,0,0,0); //Sender will use FIN / no flag during transmission

			int pointer = 0; // traverses the number of sets
			while(pointer < maxPacketNumber) {
				packetRecieved = true;
				seqNumber = 1;
				int secondaryPointer = 0; // traverses the packets in a set
				while((pointer+secondaryPointer)<maxPacketNumber && secondaryPointer < maxPacket) {
					if((pointer+secondaryPointer+1)* maxBytes>=fileSize) { // sends the last packet
						header[0] = (byte)(seqNumber); // Sequence Number
						header[2] = (byte)(1); // FIN Flag set
						byte[] tempByteArray = Arrays.copyOfRange(fileByteArray,(pointer+secondaryPointer)*maxBytes,fileSize);
						byte[] sendMessage = joinTwoBytes(header,tempByteArray);
						
						sendUdpMessage(sendMessage);
						TTLStart = System.currentTimeMillis();
						System.out.println("Last Pointer Value " + pointer + " Packet Size" +fileSize);
					}else { // sends all packet other than last
						header[0] = (byte)(seqNumber); // Sequence Number
						byte[] tempByteArray = Arrays.copyOfRange(fileByteArray,(pointer+secondaryPointer)*maxBytes,(pointer+secondaryPointer+1)*maxBytes);
						byte[] sendMessage = joinTwoBytes(header,tempByteArray);
						sendUdpMessage(sendMessage);
					}
					seqNumber++;
					secondaryPointer++;
				}

				// Check For Ack and Nak's
				while(packetRecieved) {
					if(nakReceived) { // if nak received takes appropriate action
						nakReceived = false;
						System.out.println("Nak received : Pointer " + pointer + " packet seqs " + nakPackets );
						for (Integer packetNotReceived : nakPackets) { // sends packets associated with all naks
							if((pointer+packetNotReceived+1)* maxBytes>=fileSize) {
								header[0] = packetNotReceived.byteValue(); // Sequence Number
								header[2] = (byte)(1); // FIN Flag set
								byte[] tempByteArray = Arrays.copyOfRange(fileByteArray,(pointer+packetNotReceived)*maxBytes,fileSize);
								byte[] sendMessage = joinTwoBytes(header,tempByteArray);
								sendUdpMessage(sendMessage);
								System.out.println("Last Pointer Value from NAK " + pointer);
							}else {
								header[0] = packetNotReceived.byteValue(); // Sequence Number
								header[2] = (byte)2;
								byte[] tempByteArray = Arrays.copyOfRange(fileByteArray,(pointer+packetNotReceived)*maxBytes,(pointer+packetNotReceived+1)*maxBytes);
								byte[] sendMessage = joinTwoBytes(header,tempByteArray);
								sendUdpMessage(sendMessage);
							}
						   }
						nakPackets.clear();
					}
					else if((System.currentTimeMillis() - TTLStart)>4000 && packetRecieved) {
//						if no ACK or NAK is received within 4 seconds, sends the last packet is a set
//						or the last packet, upon reception all other NAK's would flow if any
						if((pointer + secondaryPointer) > maxPacketNumber) {// sends last packet
							header[0] = (byte)maxPacketNumber;
							header[2] = (byte)3;
							byte[] tempByteArray = Arrays.copyOfRange(fileByteArray,(pointer+(header[0]&0xff))*maxBytes,fileSize);
							byte[] sendMessage = joinTwoBytes(header,tempByteArray);
							sendUdpMessage(sendMessage);
						}else {// sends all last set packet if ACK is not received in 4 seconds
							header[0] = (byte)maxPacket;
							header[2] = (byte)2;
							byte[] tempByteArray = Arrays.copyOfRange(fileByteArray,(pointer+(header[0]&0xff))*maxBytes,(pointer+(header[0]&0xff)+1)*maxBytes);
							byte[] sendMessage = joinTwoBytes(header,tempByteArray);
							sendUdpMessage(sendMessage);
						}
						TTLStart = System.currentTimeMillis();
					}
				}
				pointer = pointer + secondaryPointer;	
			}

			System.out.println("sending Complete");
	   }catch(IOException e){
			e.printStackTrace();
	   }
   }
   
   public byte[] twoByteArray(int value) {
	   byte[] twoByte = new byte[2];
	   twoByte[0] = (byte)((value >> 8) & 0xFF);
	   twoByte[1] = (byte)((value)& 0xFF);
	   return twoByte;
   }
   
//   For testing
   public static int twoByteNumber(byte left, byte right) {
		int value = (((left<<8)&0xFFFF)|(right)&0xFF);
		return value;
	}
   
   // the thread runnable.  Starts sending packets every 500ms.
   @Override
   public void run(){
      try {
    	  if (action.equals("S")) {
    		  performHandshake();
            sendFile();
    	  }
    	  Thread.sleep(500);
      }catch(Exception ex){
        ex.printStackTrace();
      }
   }
}
