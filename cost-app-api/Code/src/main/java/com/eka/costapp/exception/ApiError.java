package com.eka.costapp.exception;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;

import com.eka.costapp.error.ConnectError;
import com.fasterxml.jackson.annotation.JsonInclude;

public class ApiError {

	private HttpStatus status;
	private String message;
	private List<String> errors;
	@JsonInclude(Include.NON_NULL)
	private List<ConnectError> connectErrors;

	public HttpStatus getStatus() {
		return status;
	}

	public void setStatus(HttpStatus status) {
		this.status = status;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public List<String> getErrors() {
		return errors;
	}

	public void setErrors(List<String> errors) {
		this.errors = errors;
	}
	
	public List<ConnectError> getConnectErrors() {
		return connectErrors;
	}

	public void setNestedErrors(List<ConnectError> connectErrors) {
		this.connectErrors = connectErrors;
	}

	public ApiError(HttpStatus status, String message, List<String> errors) {
		super();
		this.status = status;
		this.message = message;
		this.errors = errors;
	}

	public ApiError(HttpStatus status, String message, String error) {
		super();
		this.status = status;
		this.message = message;
		errors = Arrays.asList(error);
	}
	
	public ApiError(HttpStatus status, String message, List<ConnectError> connectErrors, String localizedMessage) {
		super();
		this.status = status;
		this.message = message;
		this.connectErrors = connectErrors;
	}

}