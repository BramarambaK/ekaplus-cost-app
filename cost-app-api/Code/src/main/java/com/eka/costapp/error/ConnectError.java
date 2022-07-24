package com.eka.costapp.error;

import java.util.List;

import com.eka.costapp.exception.ConnectException;

public class ConnectError {

	private String errorCode;
	private String errorMessage;
	private String errorLocalizedMessage;
	private String errorContext;
	private List<ConnectError> errors;

	public ConnectError() {
	}

	public ConnectError(String errorCode, String errorMessage, String errorLocalizedMessage, String errorContext) {
		super();
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
		this.errorLocalizedMessage = errorLocalizedMessage;
		this.errorContext = errorContext;
	}

	public ConnectError(String errorCode, String errorMessage, String errorLocalizedMessage, List<ConnectError> errors, Throwable ex) {
		this();
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
		this.errorLocalizedMessage = ex.getLocalizedMessage();
		this.setErrors(errors);
	}
	public ConnectError(ConnectException ce) {
		this();
		this.errorMessage = ce.getMessage();
		this.setErrors(ce.getErrors());
	}
	public String getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getErrorLocalizedMessage() {
		return errorLocalizedMessage;
	}

	public void setErrorLocalizedMessage(String errorLocalizedMessage) {
		this.errorLocalizedMessage = errorLocalizedMessage;
	}

	public String getErrorContext() {
		return errorContext;
	}

	public void setErrorContext(String errorContext) {
		this.errorContext = errorContext;
	}

	public List<ConnectError> getErrors() {
		return errors;
	}

	public void setErrors(List<ConnectError> errors) {
		this.errors = errors;
	}
}