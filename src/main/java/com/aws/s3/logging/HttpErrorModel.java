package com.aws.s3.logging;

import java.util.Date;

public class HttpErrorModel {
	private Long no;
	private Date time;
	private String requestUrl;
	private String clientIp;
	private String elbCode;
	public Long getNo() {
		return no;
	}
	public void setNo(Long no) {
		this.no = no;
	}
	public Date getTime() {
		return time;
	}
	public void setTime(Date time) {
		this.time = time;
	}
	public String getRequestUrl() {
		return requestUrl;
	}
	public void setRequestUrl(String requestUrl) {
		this.requestUrl = requestUrl;
	}
	public String getClientIp() {
		return clientIp;
	}
	public void setClientIp(String clientIp) {
		this.clientIp = clientIp;
	}
	public String getElbCode() {
		return elbCode;
	}
	public void setElbCode(String elbCode) {
		this.elbCode = elbCode;
	}
	
	public String printError() { return "{" + this.no + ", " + this.time + ", " + this.requestUrl + ", " + this.clientIp + ", "+ this.elbCode +"}"; }

}
