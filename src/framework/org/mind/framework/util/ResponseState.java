package org.mind.framework.util;

public enum ResponseState {

	SUCCESS(100, "成功"),
	FAILED(99, "失败");
	
	private int state;
	private String msg;
	
	private ResponseState(int state, String msg) {
		this.state = state;
		this.msg = msg;
	}

	public int getState() {
		return state;
	}
	public String getMsg() {
		return msg;
	}

	@Override
	public String toString() {
		return this.getState()+":"+ this.getMsg();
	}
	
	
}
