import java.net.*; 
import java.io.*; 
import java.util.*; 
import java.net.InetAddress; 

/*
 * Project 3
 * Create a new protocol taking less space than TCP
 * @author  Aseem Mehta   (am1435@rit.edu)
 * 
 */


class Rover
{
 public static void main(String args[])
 {
	 	System.out.println("This is rover");
        if (args.length>0) {

                // Get the rover number (first arg)
		int roverNum = Integer.parseInt(args[0]);
		System.out.println("I'm rover " + roverNum);
		String sendToAddress = args[1];
		String action = args[2];
		String fileName = args[3];
		int sourcePort = 63001;
		int destinationPort = 63001;
		int window = 5120;
		int MTU = 1024;

	        try
        	{
		        // Get our sending address (perhaps for filtering later...)
				InetAddress localhost = InetAddress.getLocalHost(); 
				String address = (localhost.getHostAddress()).trim(); 
				System.out.println("My Address is: "+address);
				System.out.println("Send file to "+sendToAddress);
	
//				Starting receiver
				System.out.println("Starting Receiver...");
				Thread client=new Thread(new Receiver(sourcePort,address,roverNum,window,MTU,fileName));
				client.start();
			
//				Starting sender
				System.out.println("Starting Sender...");
				Thread sender=new Thread(new Sender(sourcePort, destinationPort,sendToAddress,roverNum,action,MTU,fileName));
				sender.start();
			
			while(true){Thread.sleep(1000);}
		}
		catch(Exception ex)
		{
	          ex.printStackTrace();
		}
	}
	else
		System.out.println("No input args! Must specify Rover Number!");
 }
}
