package com.chatserver;

import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ChatServer {
    Connection dbConnection = null;

    private InetAddress serverAddress;
    private int port;
    private Selector selector;

    class SCObject {
    	User user;
    	List<ByteBuffer> sndData;
    	ByteCache rcvData;
    	
    	SCObject() {
    		sndData = new ArrayList<ByteBuffer>();
    		rcvData = new ByteCache();
    	}
    }
    
    private Map<SocketChannel, SCObject> scObjectMap;
    public ConcurrentHashMap<Integer, User> onlineUser = new ConcurrentHashMap<Integer, User>();
    
    private void doLogin(SelectionKey key, Command command) {
    	log("doLogin");
        SocketChannel sc = (SocketChannel)key.channel();
		SCObject scObject = scObjectMap.get(sc);

		Statement stmt = null;
		ResultSet rs = null;
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
		
		try {
        	dbConnection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/sparkii?user=root&password=jasmine8gu");
			stmt = dbConnection.createStatement();
			
			String value = new String(command.value);
			int sep = value.indexOf(' ');
			String account = value.substring(0, sep);
			String password = value.substring(sep + 1);
	
	    	int sid = -1;
	    	
			String query = "select * from user where account='" + account + "' and password='" + password + "';";
			log(query);
			rs = stmt.executeQuery(query);
	        if (rs.next()) {
	        	sid = rs.getInt("id");
	
	        	scObject.user = new User(sid);
	        	scObject.user.setSocketChannel(sc);
	        	scObject.user.isConnected = true;
				onlineUser.put(sid, scObject.user);
				
				scObject.user.account = rs.getString("account");
				scObject.user.nickName = rs.getString("nickname");
				scObject.user.email = rs.getString("email");
				scObject.user.gender = rs.getInt("gender");
	
				command.arg1 = sid;
				command.value = gson.toJson(scObject.user).getBytes();
	        }
	        else {
				command.arg1 = -1;
				command.value = null;				
	        }
			
	        List<ByteBuffer> sndData = scObject.sndData;
	        sndData.add(ByteBuffer.wrap(command.encode()));
	        key.interestOps(SelectionKey.OP_WRITE);

	        if (sid == -1) {
	        	return;
	        }
	        
    		File file = new File(sid + ".jpg");
    		if (file.exists()) {
    			log(sid + ".jpg transfering");
	    	    byte[] imgData = new byte[(int) file.length()];
	    	    log("file length: " + file.length());
	    	    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
	    	    bis.read(imgData, 0, imgData.length);
	    	    log("imgData length: " + imgData.length);
	    	    
	    	    Command imgCommand = new Command(Command.SYNCPICTURE, 0, 0, null);
	    	    imgCommand.arg1 = sid;
	    	    imgCommand.value = imgData;
	    	    
	    	    log("imgCommand");
		        sndData.add(ByteBuffer.wrap(imgCommand.encode()));
		        key.interestOps(SelectionKey.OP_WRITE);
		        
		        bis.close();
    		}
	        
	        //send contact list
			List<User> contactList = new ArrayList<User>();

			String query1 = "select user.* from user right join contact on user.id=contact.cid" + 
							" where contact.id=" + sid;
			log(query1);
			rs = stmt.executeQuery(query1);
			
	        while (rs.next()) {
	        	int id = rs.getInt("id");
    			User user = new User(id);
    			user.account = rs.getString("account");
    			user.nickName = rs.getString("nickname");
    			user.email = rs.getString("email");
    			user.gender = rs.getInt("gender");
				if (onlineUser.get(id) != null) {
					user.isConnected = true;
				}
				else {
					user.isConnected = false;
				}
	        	log(id + "in contactlist isconnected:" + user.isConnected);
	        	contactList.add(user);
				scObject.user.contactList.add(new Integer(id));
	        }
	        
	        if (contactList.size() > 0) {
	        	String json = gson.toJson(contactList);
				log("json:" + json);

	    	    Command command1 = new Command(Command.SYNCPROFILE, 0, 0, null);
		    	command1.value = json.getBytes();
		        sndData.add(ByteBuffer.wrap(command1.encode()));
		        key.interestOps(SelectionKey.OP_WRITE);
	        }
	        
	    	Iterator<User> iterator = contactList.iterator();
	    	while (iterator.hasNext()) {
	    		User u = iterator.next();
	    		int id = u.id;
		    		
	    		File cfile = new File(id + ".jpg");
	    		if (cfile.exists()) {
	    			log(id + ".jpg transfering");
		    	    byte[] imgData = new byte[(int) cfile.length()];
		    	    log("file length: " + cfile.length());
		    	    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(cfile));
		    	    bis.read(imgData, 0, imgData.length);
		    	    log("imgData length: " + imgData.length);
		    	    
		    	    Command imgCommand = new Command(Command.SYNCPICTURE, 0, 0, null);
		    	    imgCommand.arg1 = id;
		    	    imgCommand.value = imgData;
		    	    
		    	    log("imgCommand");
			        sndData.add(ByteBuffer.wrap(imgCommand.encode()));
			        
			        bis.close();
	    		}
		        
		        log("OP_WRITE");
		        key.interestOps(SelectionKey.OP_WRITE);
	        }
	        
			String query4 = "select id from contact where cid=" + command.arg1;
			log(query4);
			rs = stmt.executeQuery(query4);
	        while (rs.next()) {
				int id = rs.getInt("id");
				User contactTo = onlineUser.get(id);
				if (contactTo != null) {
					SocketChannel scTo = contactTo.getSocketChannel();
					SelectionKey keyTo = scTo.keyFor(selector);
					
					Command command1 = new Command(Command.CONTACTSIGNIN, sid, 0, null);
			        List<ByteBuffer> sndData1 = scObjectMap.get(scTo).sndData;
			        sndData1.add(ByteBuffer.wrap(command1.encode()));
			        keyTo.interestOps(SelectionKey.OP_WRITE);
				}
	        }
	        dbConnection.close();
		}
		catch (Exception e) {
			e.printStackTrace();
			log(e.getMessage());
		}
		finally {
		    if (rs != null) {
		        try {
		            rs.close();
		        } catch (SQLException sqlEx) { }

		        rs = null;
		    }

		    if (stmt != null) {
		        try {
		            stmt.close();
		        } catch (SQLException sqlEx) { }

		        stmt = null;
		    }
		}        
    }
 
    private void doSignup(SelectionKey key, Command command) {
    	log("doSignup");
    	
        SocketChannel sc = (SocketChannel) key.channel();
		SCObject scObject = scObjectMap.get(sc);

		Statement stmt = null;
		ResultSet rs = null;
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
		
		try {
        	dbConnection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/sparkii?user=root&password=jasmine8gu");
			stmt = dbConnection.createStatement();

			String value = new String(command.value);
			int sep = value.indexOf(' ');
			String account = value.substring(0, sep);
			String password = value.substring(sep + 1);

			command.arg1 = -1;
			command.value = null;
			
			String query = "select * from user where account='" + account + "' and password='" + password + "';";
			log(query);
			rs = stmt.executeQuery(query);
	        if (rs.next()) {
				command.value = new String("This email address already registered!").getBytes();
	        }
	        else {
				DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				Date date = new Date();
				query = "insert into user(account, password, registerdatetime) values('" + account + 
						"', '" + password + "', '" + dateFormat.format(date) + "');";
				log(query);
				stmt.executeUpdate(query);
				
				rs = stmt.executeQuery("select last_insert_id() as last_id from user");
				if (rs.next()) {
		        	int id = rs.getInt("last_id");
		        	log("last insert id:" + id);
		        	
		        	scObject.user = new User(id);
		        	scObject.user.setSocketChannel(sc);
		        	scObject.user.isConnected = true;
					onlineUser.put(id, scObject.user);
					
					scObject.user.account = account;

	    			command.arg1 = id;
			    	command.value = gson.toJson(scObject.user).getBytes();
				}
	        }				
			
	        List<ByteBuffer> sndData = scObject.sndData;	
	        sndData.add(ByteBuffer.wrap(command.encode()));
	        key.interestOps(SelectionKey.OP_WRITE);
	        
	        dbConnection.close();
		}
		catch (Exception e) {
			e.printStackTrace();
			log(e.getMessage());
		}
		finally {
		    if (rs != null) {
		        try {
		            rs.close();
		        } catch (SQLException sqlEx) { }

		        rs = null;
		    }

		    if (stmt != null) {
		        try {
		            stmt.close();
		        } catch (SQLException sqlEx) { }

		        stmt = null;
		    }
		}        
    }

    private void doContactSignoff(SelectionKey key, Command command) {
    	log("doContactSignoff");
    	
		Statement stmt = null;
		ResultSet rs = null;
		
		try {
        	dbConnection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/sparkii?user=root&password=jasmine8gu");
			stmt = dbConnection.createStatement();

			log("CONTACTSIGNOFF" + command.arg1);
			String query = "select id from contact where cid=" + command.arg1;
			log(query);
			rs = stmt.executeQuery(query);
	        while (rs.next()) {
				int id = rs.getInt("id");
				User contactTo = onlineUser.get(id);
				if (contactTo != null) {
					SocketChannel scTo = contactTo.getSocketChannel();
					SelectionKey keyTo = scTo.keyFor(selector);
					
			        List<ByteBuffer> sndData = scObjectMap.get(scTo).sndData;
			        sndData.add(ByteBuffer.wrap(command.encode()));
			        keyTo.interestOps(SelectionKey.OP_WRITE);
				}
	        }
	        dbConnection.close();
		}
		catch (Exception e) {
			e.printStackTrace();
			log(e.getMessage());
		}
		finally {
		    if (rs != null) {
		        try {
		            rs.close();
		        } catch (SQLException sqlEx) { }

		        rs = null;
		    }

		    if (stmt != null) {
		        try {
		            stmt.close();
		        } catch (SQLException sqlEx) { }

		        stmt = null;
		    }
		}        
    }

    private void doAddContact(SelectionKey key, Command command) {
    	log("doAddContact");
    	
        SocketChannel sc = (SocketChannel)key.channel();
		SCObject scObject = scObjectMap.get(sc);

		Statement stmt = null;
		ResultSet rs = null;
		
		try {
        	dbConnection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/sparkii?user=root&password=jasmine8gu");
			stmt = dbConnection.createStatement();

			String query = "insert into contact(id, cid) values(" + command.arg1 + "," + command.arg2 + ");";
			log(query);
			stmt.executeUpdate(query);
			
			scObject.user.contactList.add(new Integer(command.arg2));
			dbConnection.close();
		}
		catch (Exception e) {
			e.printStackTrace();
			log(e.getMessage());
		}
		finally {
		    if (rs != null) {
		        try {
		            rs.close();
		        } catch (SQLException sqlEx) { }

		        rs = null;
		    }

		    if (stmt != null) {
		        try {
		            stmt.close();
		        } catch (SQLException sqlEx) { }

		        stmt = null;
		    }
		}        
    }

    private void doDeleteContact(SelectionKey key, Command command) {
    	log("doDeleteContact");
    	
        SocketChannel sc = (SocketChannel)key.channel();
		SCObject scObject = scObjectMap.get(sc);

		Statement stmt = null;
		ResultSet rs = null;
		
		try {
        	dbConnection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/sparkii?user=root&password=jasmine8gu");
			stmt = dbConnection.createStatement();

			String query = "delete from contact where id=" + command.arg1 + " and cid=" + command.arg2 + ";";
			log(query);
			stmt.executeUpdate(query);
			
			scObject.user.contactList.remove(new Integer(command.arg2));
			dbConnection.close();
		}
		catch (Exception e) {
			e.printStackTrace();
			log(e.getMessage());
		}
		finally {
		    if (rs != null) {
		        try {
		            rs.close();
		        } catch (SQLException sqlEx) { }

		        rs = null;
		    }

		    if (stmt != null) {
		        try {
		            stmt.close();
		        } catch (SQLException sqlEx) { }

		        stmt = null;
		    }
		}        
    }

    private void doUpdateProfile(SelectionKey key, Command command) {
    	log("doUpdateProfile");
    	
		Statement stmt = null;
		ResultSet rs = null;
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
		
		try {
        	dbConnection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/sparkii?user=root&password=jasmine8gu");
			stmt = dbConnection.createStatement();

			User user = gson.fromJson(new String(command.value), User.class);
			
			String query = "update user set nickname='" + user.nickName +  
							"', email='" + user.email +  
							"', gender=" + user.gender + 
							" where id=" + user.id + ";";
			log(query);
			stmt.executeUpdate(query);		
			dbConnection.close();
		}
		catch (Exception e) {
			e.printStackTrace();
			log(e.getMessage());
		}
		finally {
		    if (rs != null) {
		        try {
		            rs.close();
		        } catch (SQLException sqlEx) { }

		        rs = null;
		    }

		    if (stmt != null) {
		        try {
		            stmt.close();
		        } catch (SQLException sqlEx) { }

		        stmt = null;
		    }
		}        
    }

    private void doUpdatePicture(SelectionKey key, Command command) {
    	log("doUpdatePicture");
    	
		Statement stmt = null;
		ResultSet rs = null;
		
		try {
        	dbConnection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/sparkii?user=root&password=jasmine8gu");
			stmt = dbConnection.createStatement();

			int id = command.arg1;
			log(new Integer(id).toString());
			
			String fileName = id + ".jpg";
			log(fileName);

			File file = new File(fileName);
			if(file.exists()) {
				file.delete();
				log("delete existed file");
			}
			
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(command.value);
			fos.close();
			
			log("write jpg data into file");
			dbConnection.close();
		}
		catch (Exception e) {
			e.printStackTrace();
			log(e.getMessage());
		}
		finally {
		    if (rs != null) {
		        try {
		            rs.close();
		        } catch (SQLException sqlEx) { }

		        rs = null;
		    }

		    if (stmt != null) {
		        try {
		            stmt.close();
		        } catch (SQLException sqlEx) { }

		        stmt = null;
		    }
		}        
    }

    private void doConversation(SelectionKey key, Command command) {
    	log("doConversation");
    	
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
		
		try {
			int id = command.arg2;
			User contactTo = onlineUser.get(id);
			if (contactTo == null) {
				return;
			}

			SocketChannel scTo = contactTo.getSocketChannel();
			SelectionKey keyTo = scTo.keyFor(selector);
			
	        List<ByteBuffer> sndData = scObjectMap.get(scTo).sndData;
	        sndData.add(ByteBuffer.wrap(command.encode()));
	        keyTo.interestOps(SelectionKey.OP_WRITE);
	        
			if (!contactTo.contactList.contains(new Integer(command.arg1))) {
				List<User> contactList = new ArrayList<User>();
				User u = onlineUser.get(command.arg1);
				if (u == null) {
					//Exception
				}
				contactList.add(u);
	        
	        	String json = gson.toJson(contactList);
				log("json:" + json);

	    	    Command command1 = new Command(Command.SYNCPROFILE, 0, 0, null);
		    	command1.value = json.getBytes();
		        sndData.add(ByteBuffer.wrap(command1.encode()));

	    		int cid = u.id;
	    		log("id in contactList: " + cid);
	    		
	    		File file = new File(cid + ".jpg");
	    		if (file.exists()) {
	    			log(cid + ".jpg transfering");
		    	    byte[] imgData = new byte[(int) file.length()];
		    	    log("file length: " + file.length());
		    	    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
		    	    bis.read(imgData, 0, imgData.length);
		    	    log("imgData length: " + imgData.length);
		    	    
		    	    Command imgCommand = new Command(Command.SYNCPICTURE, 0, 0, null);
		    	    imgCommand.arg1 = cid;
		    	    imgCommand.value = imgData;
		    	    
		    	    log("imgCommand");
			        sndData.add(ByteBuffer.wrap(imgCommand.encode()));
			        
			        bis.close();
	    		}
		        
		        log("OP_WRITE");
		        key.interestOps(SelectionKey.OP_WRITE);
		        
		        contactTo.contactList.add(command.arg1);
        	}
		}
		catch (Exception e) {
			e.printStackTrace();
			log(e.getMessage());
		}
    }

    private void doSearchContact(SelectionKey key, Command command) {
    	log("doSearchContact");
    	
        SocketChannel sc = (SocketChannel) key.channel();
		SCObject scObject = scObjectMap.get(sc);

		Statement stmt = null;
		ResultSet rs = null;
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().serializeNulls().create();
		
		try {
        	dbConnection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/sparkii?user=root&password=jasmine8gu");
			stmt = dbConnection.createStatement();
			
			String keyword = new String(command.value);
			log("keyword:" + keyword);
			int id = command.arg1;
			
	    	String json = "";
			List<User> userList = new ArrayList<User>();

			//TODO
			//random range for every search
			String query = "select * from user where (account like '%" + keyword + "%' or nickname like '%" + keyword + "%') and id!=" + id + ";";
			log(query);
			rs = stmt.executeQuery(query);
			
	        while (rs.next()) {
	        	int cid = rs.getInt("id");
    			User user = new User(cid);
    			user.account = rs.getString("account");
    			user.nickName = rs.getString("nickname");
    			user.email = rs.getString("email");
    			user.gender = rs.getInt("gender");
				if (onlineUser.get(cid) != null) {
					user.isConnected = true;
				}
				else {
					user.isConnected = false;
				}
    			userList.add(user);
	        }				
			json = gson.toJson(userList);

			log("json:" + json);
	    	command.value = json.getBytes();
	    	
	        List<ByteBuffer> sndData = scObject.sndData;
	        sndData.add(ByteBuffer.wrap(command.encode()));
	        
	    	Iterator<User> iterator = userList.iterator();
	    	while (iterator.hasNext()) {
	    		User u = iterator.next();
	    		int cid = u.id;
	    		log("id in userlist: " + cid);
	    		
	    		File file = new File(cid + ".jpg");
	    		if (file.exists()) {
	    			log(cid + ".jpg transfering");
		    	    byte[] imgData = new byte[(int) file.length()];
		    	    log("file length: " + file.length());
		    	    BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
		    	    bis.read(imgData, 0, imgData.length);
		    	    log("imgData length: " + imgData.length);
		    	    
		    	    Command imgCommand = new Command(Command.SYNCPICTURE, 0, 0, null);
		    	    imgCommand.arg1 = cid;
		    	    imgCommand.value = imgData;
		    	    
		    	    log("imgCommand");
			        sndData.add(ByteBuffer.wrap(imgCommand.encode()));
			        log("OP_WRITE");
			        
			        bis.close();
	    		}
	    	}	
	        
	        key.interestOps(SelectionKey.OP_WRITE);
	        dbConnection.close();
		}
		catch (Exception e) {
			e.printStackTrace();
			log(e.getMessage());
		}
		finally {
		    if (rs != null) {
		        try {
		            rs.close();
		        } catch (SQLException sqlEx) { }

		        rs = null;
		    }

		    if (stmt != null) {
		        try {
		            stmt.close();
		        } catch (SQLException sqlEx) { }

		        stmt = null;
		    }
		}        
    }


	private void handleCommand(SelectionKey key, byte[] data) {
        Command command = Command.decode((byte[])data);
		switch (command.cmd) {
			case Command.LOGIN: {
				doLogin(key, command);
		        break;
			}
			
			case Command.SIGNUP: {
				doSignup(key, command);
		        break;
			}
			
			case Command.CONTACTSIGNOFF: {
				doContactSignoff(key, command);
				break;
			}
			case Command.ADDCONTACT: {
				doAddContact(key, command);
				break;
			}
			
			case Command.DELETECONTACT: {
				doDeleteContact(key, command);
				break;
			}
			
			case Command.UPDATEPROFILE: {
				doUpdateProfile(key, command);
				break;
			}
			
			case Command.UPDATEPICTURE: {
				doUpdatePicture(key, command);
				break;
			}
			
			case Command.CONVERSATION: {
				doConversation(key, command);
				break;
			}
			
			case Command.SEARCHCONTACT: {
				doSearchContact(key, command);
				break;
			}
		}
    }
    
    public ChatServer(InetAddress a, int p) throws IOException {
        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
        }
        catch (Exception e) {
            log("Class not found exception!");
        }
/*        
        try {
        	dbConnection = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/sparkii?user=root&password=jasmine8gu");
        	log("MySql connected!");
            dbConnection.close();
        } 
        catch (Exception e) {
        	log("MySql connect error" + e.getMessage());
        }
*/    	
    	serverAddress = a;
        port = p;
        scObjectMap = new HashMap<SocketChannel, SCObject>();
        startServer();
    }

    private void startServer() throws IOException {
        selector = Selector.open();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);

        InetSocketAddress listenAddr = new InetSocketAddress(serverAddress, port);
        serverChannel.socket().bind(listenAddr);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        log("Server ready");
        
        while (true) {
        	log("selector select...");
            int cnt = selector.select();
            if (cnt < 1) {
            	continue;
            }
            
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = (SelectionKey) keys.next();

                keys.remove();

                if (! key.isValid()) {
                    continue;
                }

                if (key.isAcceptable()) {
                    accept(key);
                }
                else if (key.isReadable()) {
                    read(key);
                }
                else if (key.isWritable()) {
                    write(key);
                }
            }
        }
    }

    private void accept(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel)key.channel();
        SocketChannel sc = serverChannel.accept();
        sc.configureBlocking(false);

        Socket socket = sc.socket();
        SocketAddress remoteAddr = socket.getRemoteSocketAddress();
        log("accept: " + remoteAddr);

        scObjectMap.put(sc, new SCObject());
        sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    private void read(SelectionKey key) throws IOException {
    	log("read");
    	
        try {
	        SocketChannel sc = (SocketChannel)key.channel();
	        ByteBuffer buffer = ByteBuffer.allocate(1024);
        
        	int len = sc.read(buffer);

	        if (len > -1) {
		        SCObject scObject = scObjectMap.get(sc);
		        ByteCache rcvData = scObject.rcvData;
		        
		        rcvData.append(buffer.array(), len);
		    	
		        log("read len: " + len);
		        //log("read: " + new String(buffer.array()));
		        
		    	while (rcvData.length() >= 4) {
		        	int commandLen = ByteCache.bytesToInt(rcvData.getBytes(0, 4));
		        	
		        	if (commandLen > 0 && rcvData.length() >= commandLen + 4) {
		        		handleCommand(key, rcvData.getBytes(0, 4 + commandLen));
		        		rcvData.truncHead(commandLen + 4);
		        	}
		        	else {
		        		break;
		        	}
		    	}
	        }
	    	else {
	    		SCObject scObject = scObjectMap.get(sc);
	    		if (scObject.user != null) {	    			
		    		Command command = new Command(Command.CONTACTSIGNOFF, scObject.user.id, 0, null);
		    		handleCommand(key, command.encode());
		    		onlineUser.remove(scObject.user);
	    		}
	    		
	        	scObjectMap.remove(sc);
	            Socket socket = sc.socket();
	            SocketAddress remoteAddr = socket.getRemoteSocketAddress();
	            log("Connection closed by client: " + remoteAddr);
	            sc.close();
	            key.cancel();
	            return;
	    	}
        }
        catch (Exception e) {
            e.printStackTrace();
			log(e.getMessage());
        }
    }

    private void write(SelectionKey key) throws IOException {
    	log("write");
    	try {
	        SocketChannel sc = (SocketChannel) key.channel();
	        SCObject scObject = scObjectMap.get(sc);
	        List<ByteBuffer> sndData = scObject.sndData;
	        
	        Iterator<ByteBuffer> it = sndData.iterator();
	        while (it.hasNext()) {
	        	ByteBuffer sendBuffer = it.next();
	            int ret = sc.write(sendBuffer);
	            log("Write buffer: " + ret);
	            
	            if (ret > 0) {
	            	if (0 == sendBuffer.remaining()) {
	            		it.remove();
	            	}
	            }
	            else {
	            	break;
	            }
	        }
	        
	        if (sndData.isEmpty()) {
	        	key.interestOps(SelectionKey.OP_READ);
	        }
    	}
    	catch (Exception e) {
    		e.printStackTrace();
			log(e.getMessage());
    	}
    }

    private static void log(String s) {
    	if (s!= null && s.length() < 256) {
    		System.out.println(s);
    	}
    }

    public static void main(String[] args) throws Exception {
        new ChatServer(null, 8989);
    }
}