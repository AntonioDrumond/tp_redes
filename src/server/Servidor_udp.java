import java.io.*;
import java.net.*;

class Servidor
{
   private static int portaServidor = 9871;

   public static void main(String args[]) throws Exception
   {
      DatagramSocket serverSocket = new DatagramSocket(portaServidor);

      while(true) 
      {
        byte[] receiveData = new byte[1024];
        byte[] sendData = new byte[1024];
         DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

         System.out.println("Aguardando datagrama do cliente....");
         serverSocket.receive(receivePacket);

         System.out.println("RECEIVED: " + new String(receivePacket.getData()));
         InetAddress ipCliente = receivePacket.getAddress();
         int portaCliente = receivePacket.getPort();
         sendData = (new String(receivePacket.getData())).toUpperCase().getBytes();

         DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ipCliente, portaCliente);
         serverSocket.send(sendPacket);
         System.out.println("Enviado...");
      }
   }
}
