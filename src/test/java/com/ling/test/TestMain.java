package com.ling.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import com.ling.remoteservice.Manager;
import com.ling.remoteservice.msg.MessagerFactory;

public class TestMain {
	
	public static void main(String[] args) throws IOException{
		final TestService service=Manager.getInstance().createInstance(TestService.class);
		
		BufferedReader reader=new BufferedReader(new InputStreamReader(System.in));
		String cmd=reader.readLine();
		final String[] packedcmd=new String[]{cmd};
		new Thread(){
			public void run(){
				while (true){
					System.out.println(service.getContent(packedcmd[0]));
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}.start();
		while (cmd!=null && cmd.length()>0){
			
			System.out.println(service.getContent(cmd));
			
			cmd=reader.readLine();
			packedcmd[0]=cmd;
			service.refresh(cmd);
		}
		MessagerFactory.getInstance().close();
	}
}
