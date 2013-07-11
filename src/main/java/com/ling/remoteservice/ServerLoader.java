package com.ling.remoteservice;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sun.misc.Signal;
import sun.misc.SignalHandler;

public class ServerLoader {
    static Log logger = LogFactory.getLog(ServerLoader.class);

    static boolean running = true;
    static Server serverInstance;
    public static void initServer() {
        
        final Server server=new Server();
        serverInstance=server;
        server.setDaemon(true);
        server.start();
        
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                if (running){
                    running=false;
                    System.out.println("Server shutdown!");
                    logger.error("Server closed.", new Exception("caller stack"));
                    server.close();
                }else{
                    System.out.println("Server is closing...");
                }
            }
        });
 
        SignalHandler handler = new SignalHandler() {
            public void handle(Signal sig) {
                if (running) {
                    running = false;
                    logger.error("Signal " + sig);
                    logger.error("Shutting down server...");
                    logger.error("Server closed.");
                    
                } else {
                    logger.error("Signal " + sig);
                    logger.error("server is closing...");
                }
                server.close();
                System.exit(0);
            }
        };
        Signal.handle(new Signal("INT"), handler);
        Signal.handle(new Signal("TERM"), handler);
        //Signal.handle(new Signal("KILL"), handler);
    }
    public static void close(){
    	if (serverInstance!=null)
    		serverInstance.close();
    }

    public static void main(String args[]) throws Exception {
        initServer();
        new Thread(){
        	public void run(){
        		while (true){
        			System.out.println("Server running...");
        			try {
						sleep(30000);
					} catch (InterruptedException e) {
						break;
					}
        		}
        	}
        }.start();
    }

}
