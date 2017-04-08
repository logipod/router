package com.servion.watson.router;

/**
 * Exception indicating an empty message from SIP Orchestrator
 * This will be a checked exception since upon seeing this 
 * exception application will perform work-around action.
 * 
 * @author larsen.mallick
 *
 */
public class EmptyBodyException extends Exception {

	/**
	 * Auto-generated. This is JVM dependent.
	 * No impacts fore-seen since this exception is not serialized. 
	 */
	private static final long serialVersionUID = -2095060312129210291L;

	public EmptyBodyException() {
		super();
	}
	
}
