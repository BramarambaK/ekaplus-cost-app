package com.eka.costapp.model;

import java.util.List;

public class FilterData {

	private List<MongoOperations> filter;

	public FilterData(List<MongoOperations> filter) {
		this.filter = filter;
	}

	public List<MongoOperations> getFilter() {
		return filter;
	}

	public void setFilter(List<MongoOperations> filter) {
		this.filter = filter;
	}

}
