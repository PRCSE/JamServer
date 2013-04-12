package com.prcse.server;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Observable;
import java.util.ResourceBundle;

import javax.sql.DataSource;

import com.prcse.protocol.CustomerInfo;
import com.prcse.protocol.Request;


public class ConnectionHandler extends Observable implements Runnable {
	
	private Socket socket;
	private DataSource dataSource;
	private ResourceBundle queries;
	private ArrayList broadcastQueue;
	private int clientId;
	private static int clientIdSeed = 0;
	private String clientEmail;
	//TODO set after login
	
	public ConnectionHandler(Socket socket, DataSource dataSource, ResourceBundle queries){
		this.socket = socket;
		this.dataSource = dataSource;
		this.broadcastQueue = new ArrayList();
		this.clientId = nextClientId();
		this.queries = queries;
	}
	
	public void run(){
		System.out.println("Client running...");
		try {
			ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
			BufferedInputStream buff = new BufferedInputStream(socket.getInputStream());
			ObjectInputStream input = new ObjectInputStream(buff);
			
			System.out.println("sending ID...");
			output.writeObject(new Integer(clientId));
			output.flush();
			System.out.println("ID sent.");
			
			Request request = null;
			
			while (true) {
				if(request != null){
					System.out.println("Message received.");
					
					PrcseDataSource objectSource = new PrcseDataSource(this.dataSource.getConnection(), this.queries);
					
					request.handleRequest(objectSource);
					objectSource.disconnect();
					
					if(request instanceof CustomerInfo) {
						if(((CustomerInfo) request).getCustomer() != null){
							this.clientEmail = ((CustomerInfo) request).getEmail();
						}
					}
					
					if(request.shouldBroadcast() && request.getError() == null){
						this.notifyRequest(request); // don't like method name
					}
					else {
						output.writeObject(request);
						output.flush();
					}
					
					request = null;
				}
				
				if(buff.available() > 0){
					System.out.println("Message available.");
					request = (Request)input.readObject();
					System.out.println("Message read.");
				}
				else{
					request = nextBroadcastRequest();
					
					if(request != null){
						output.writeObject(request);
						output.flush();
					}
					
					request = null;
				}
				
				Thread.sleep(10);
			}
		}
		catch(EOFException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch(IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally {
			try {
				System.out.println( "\n* Closing connection... *");
				socket.close();
				this.socket = null;
				changed();
			}
			catch(IOException ioEx) {
				System.out.println("Unable to disconnect");
			}
		}
	}
	
	private Request nextBroadcastRequest() {
		Request result = null;
		synchronized(this.broadcastQueue) {
			if(broadcastQueue.isEmpty() == false) {
				result = (Request)broadcastQueue.get(0);
				broadcastQueue.remove(0);
			}
		}
		return result;
	}

	private void notifyRequest(Request request) {
		setChanged();
		notifyObservers(request);
		clearChanged();
	}

	// Update observers
	protected void changed() {
		setChanged();
		notifyObservers();
		clearChanged();
	}

	public void addBroadcastRequest(Request request) {
		synchronized(this.broadcastQueue) {
			this.broadcastQueue.add(request);
		}
	}
	
	public static int nextClientId() {
		clientIdSeed ++;
		return clientIdSeed;
	}
}
