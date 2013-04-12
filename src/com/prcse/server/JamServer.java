package com.prcse.server;


import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.ResourceBundle;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;

import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;
import com.prcse.protocol.Request;


public class JamServer {
	private ServerSocket servSock;
	private DataSource dataSource;
	private ResourceBundle queries;
	private String mysqlUrl = "jdbc:mysql://127.0.0.1:8889/test";
	private String oracleUrl = "jdbc:oracle:thin:@larry.uopnet.plymouth.ac.uk:1521:orcl";
	private final int PORT = 1234;
	private ArrayList handlers;

	public JamServer() {
		handlers = new ArrayList();
		
		// comment out method based on which database source you want to use
		connectMySQL();
//		try {
//			connectOracle();
//		} catch (SQLException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

	private void connectMySQL() {
		MysqlConnectionPoolDataSource pds = new MysqlConnectionPoolDataSource();
		pds.setUrl(mysqlUrl);
		pds.setUser("test");
		pds.setPassword("test");
		queries = ResourceBundle.getBundle("MySQL");
		
		this.dataSource = pds;
	}
	
	private void connectOracle() throws SQLException {
		PoolDataSource pds = PoolDataSourceFactory.getPoolDataSource();
		pds.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
		pds.setURL(oracleUrl);
		pds.setUser("PRCSE");
		pds.setPassword("tookieknows");
		queries = ResourceBundle.getBundle("Oracle");
		
		this.dataSource = pds;
	}

	public void run() {
		System.out.println("Opening port...\n");
		try {
			servSock = new ServerSocket(PORT);
		}
		catch(IOException ioEx) {
			System.out.println("Unable to attach to port");
			System.exit(1);
		}
		do {
			handleClient();
		}
		while (true);
		//TODO check rest of while true loop structure
	}

	private void handleClient() {
		try {
			ConnectionHandler handler = new ConnectionHandler(servSock.accept(), dataSource, queries);
			
			addHandler(handler);
			handler.addObserver(new Observer(){
				public void update(Observable arg0, Object arg1) {
					if(arg1 != null){
						broadcastRequest((Request)arg1);
					}
					else {
						removeHandler((ConnectionHandler)arg0);
					}
				}});
			
			Thread t = new Thread(handler);
			t.start();
			System.out.println("Client connected.");
		}
		catch(EOFException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch(IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public synchronized void addHandler(ConnectionHandler handler){
		handlers.add(handler);
	}
	
	public synchronized void removeHandler(ConnectionHandler handler){
		handlers.remove(handler);
		System.out.println("Client dropped.");
	}
	
	public synchronized void broadcastRequest(Request request){
		for(Iterator i = handlers.iterator(); i.hasNext();){
			ConnectionHandler handler = (ConnectionHandler)i.next();
			handler.addBroadcastRequest(request);
		}
	}
	
	public static void main(String[] args) {
		JamServer server = new JamServer();
		server.run();
	}
}