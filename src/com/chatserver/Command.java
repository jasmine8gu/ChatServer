package com.chatserver;

public class Command {
	//cmd
	public static final int LOGIN = 1;
	public static final int SIGNUP = 2;
	public static final int CONVERSATION = 4;
	public static final int SEARCHCONTACT = 5;
	public static final int NICKNAME = 6;
	public static final int UPDATEPICTURE = 7;
	public static final int UPDATEPROFILE = 8;
	public static final int SYNCPICTURE = 9;
	public static final int SYNCPROFILE = 10;
	public static final int ADDCONTACT = 11;
	public static final int DELETECONTACT = 12;
	public static final int CONTACTSIGNIN = 13;
	public static final int CONTACTSIGNOFF = 14;
	
	public int cmd = 0;
	public int arg1 = 0;
	public int arg2 = 0;
	public byte[] value = null;
	

	public Command() {
		//
	}
	
	public Command(int c, int a1, int a2, byte[] v) {
		cmd = c;
		arg1 = a1;
		arg2 = a2;
		value = v;
	}
	
	public static Command decode(byte[] message) {
		if (message == null || message.length == 0) {
			return null;
		}
				

		byte[] bytesValue = null;
		if (message.length > 16) {
			bytesValue = new byte[message.length - 16];
			System.arraycopy(message, 16, bytesValue, 0, message.length - 16);
		}

    	int cmd = 0;
    	for (int i = 0; i < 4; i++) {
	    	int shift = (4 - i - 1) * 8;
	    	cmd += (message[4 + i] & 0x000000FF) << shift;
    	}

    	int arg1 = 0;
    	for (int i = 0; i < 4; i++) {
	    	int shift = (4 - i - 1) * 8;
	    	arg1 += (message[8 + i] & 0x000000FF) << shift;
    	}

    	int arg2 = 0;
    	for (int i = 0; i < 4; i++) {
	    	int shift = (4 - i - 1) * 8;
	    	arg2 += (message[12 + i] & 0x000000FF) << shift;
    	}

		Command command = new Command(cmd, arg1, arg2, bytesValue);
		return command;
	}

	public byte[] encode() {
		int valueLen = 0;
		if (value != null) {
			valueLen = value.length;
		}
		
		byte[] ret = new byte[16 + valueLen];
		
		int cmdLen = 12 + valueLen;
		ret[0] = (byte)(cmdLen >>> 24);
		ret[1] = (byte)(cmdLen >>> 16);
		ret[2] = (byte)(cmdLen >>> 8);
		ret[3] = (byte)(cmdLen >>> 0);
		
		ret[4] = (byte)(cmd >>> 24);
		ret[5] = (byte)(cmd >>> 16);
		ret[6] = (byte)(cmd >>> 8);
		ret[7] = (byte)(cmd >>> 0);
		
		ret[8] = (byte)(arg1 >>> 24);
		ret[9] = (byte)(arg1 >>> 16);
		ret[10] = (byte)(arg1 >>> 8);
		ret[11] = (byte)(arg1 >>> 0);
		
		ret[12] = (byte)(arg2 >>> 24);
		ret[13] = (byte)(arg2 >>> 16);
		ret[14] = (byte)(arg2 >>> 8);
		ret[15] = (byte)(arg2 >>> 0);

		if (value != null) {
			System.arraycopy(value, 0, ret, 16, valueLen);
		}
		
		return ret;
	}	
}

