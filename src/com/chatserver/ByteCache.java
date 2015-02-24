package com.chatserver;

public class ByteCache {

	private byte[] cache = null;
	
    public static int bytesToInt(byte[] b) {
    	int value = 0;

    	for (int i = 0; i < 4; i++) {
	    	int shift = (4 - i - 1) * 8;
	    	value += (b[i] & 0x000000FF) << shift;
    	}

    	return value;
	}

    public static byte[] intToBytes(int a) {
    	byte[] ret = new byte[4];

    	ret[3] = (byte) (a & 0xFF);
    	ret[2] = (byte) ((a >> 8) & 0xFF);
    	ret[1] = (byte) ((a >> 16) & 0xFF);
    	ret[0] = (byte) ((a >> 24) & 0xFF);

    	return ret;
    }
    	
	public byte[] append(byte[] buffer, int len) {
    	byte[] newCache = null;
    	
    	if (cache == null) {
        	newCache = new byte[len];
        	System.arraycopy(buffer, 0, newCache, 0, len);
    	}
    	else {
    		newCache = new byte[cache.length + len];
    		System.arraycopy(cache, 0, newCache, 0, cache.length);
    		System.arraycopy(buffer, 0, newCache, cache.length, len);
    	}
    	
    	cache = newCache;
    	newCache = null;
    	
    	return cache;
	}
	
	public int length() {
		int ret = 0;
		if (cache != null) {
			ret = cache.length;
		}
		return ret;
	}
	
	public byte[] getBytes(int start, int len) {
		byte[] newBytes = new byte[len];
    	System.arraycopy(cache, start, newBytes, 0, len);
    	return newBytes;
	}
	
	public void truncHead(int len) {
		byte[] newBytes = new byte[cache.length - len];
		System.arraycopy(cache, len, newBytes, 0, cache.length - len);
		cache = newBytes;
		newBytes = null;
	}
}
