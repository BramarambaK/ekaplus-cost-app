package com.eka.costapp.model;

public class MongoOperations {
	private String fieldName;
	private Object value;
	private String operator;

	public MongoOperations(String fieldName, Object value, String operator) {
		this.fieldName = fieldName;
		this.value = value;
		this.operator = operator;
	}

	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}

	public String getOperator() {
		return operator;
	}

	public void setOperator(String operator) {
		this.operator = operator;
	}

}
