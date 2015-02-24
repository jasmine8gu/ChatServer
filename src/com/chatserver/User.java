package com.chatserver;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;

import com.google.gson.annotations.Expose;

public class User {
	
	@Expose
	public int id = -1;
	@Expose
	public String account = null;
	public String password = null;
	@Expose
	public String nickName = null;
	@Expose
	public String email = null;
	@Expose
	public int gender = -1;
	@Expose
	boolean isConnected = false;
	
	ArrayList<Integer> contactList = null;
	
	public SocketChannel schannel = null;
	
	public User(int userId) {
		id = userId;
    	contactList = new ArrayList<Integer>();
	}
	
	public void onDestroy() {
		contactList.clear();
	}
	
	@Override
	public boolean equals(Object o) {
	    if (o instanceof User) {
	    	User c = (User) o;
	   		if (this.account.equals(c.account)) {
    			return true;
    		}
	    }
	    return false;
	}
	
	public static boolean containsAccount(ArrayList<User> contactList, String account) {
    	Iterator<User> it = contactList.iterator();
    	while(it.hasNext()) {
    		User ct = it.next();
    		if (ct.account.equals(account)) {
    			return true;
    		}
    	}
    	return false;
	}
/*	
	public int getId() {
		return id;
	}

	public void setId(int i) {
		id = i;
	}

	public String getAccount() {
		return account;
	}
	
	public void setAccount(String a) {
		account = a;
	}
	
	public String getNickName() {
		return nickName;
	}
	
	public void setNickName(String n) {
		nickName = n;
	}
*/	
	public SocketChannel getSocketChannel() {
		return schannel;
	}
	
	public void setSocketChannel(SocketChannel s) {
		schannel = s;
	}
/*	
    @Override
    public String toString() {
        return "User [id=" + id + 
        		", account=" + account + 
        		", nickName=" + nickName + 
        		"]";
    }	
*/    
}
