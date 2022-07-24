package com.eka.costapp.commons;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.eka.costapp.model.ContextInfo;

/**
 * The Class ContextProvider.
 * 
 * @author Ranjan.Jha
 */
@Component
public class ContextProvider {

	/** The logger. */
	private static Logger logger = LoggerFactory.getLogger(ContextProvider.class.getName());

	/** The current ContextInfo. */
	private ThreadLocal<ContextInfo> currentContext = new ThreadLocal<>();

	/**
	 * Sets the current tenant.
	 *
	 * @param context the new current ContextInfo
	 */
	public void setCurrentContext(ContextInfo context) {
		logger.debug("Setting currentContext to " + currentContext);
		currentContext.set(context);
	}

	/**
	 * Gets the current ContextInfo.
	 *
	 * @return the current ContextInfo
	 */
	public ContextInfo getCurrentContext() {
		return currentContext.get();
	}

	/**
	 * Clear.
	 */
	public void clear() {
		currentContext.set(null);
	}

}