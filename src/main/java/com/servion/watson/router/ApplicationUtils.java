package com.servion.watson.router;

import static com.servion.watson.router.ApplicationConstants.INPUT_TIMEOUT;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

/**
 * Collection of static utility methods used through the application.
 * 
 * @author larsen.mallick
 *
 */
public class ApplicationUtils {

	private final static Logger logger = Logger.getLogger(ApplicationUtils.class);
	
	/**
	 * Check if the property holds expected value in context.
	 * 
	 * @param context
	 * @param property
	 * @param expectedValue
	 * @return
	 */
	public static boolean verifyProperty(Map<String, Object> context, String property, Object expectedValue) {
		if (logger.isDebugEnabled())
			logger.debug("Entering verifyProperty() :: property - " + property + " ; expectedValue - " + expectedValue);
		if (logger.isDebugEnabled())
			logger.debug("Entering verifyProperty() :: context - " + context);
		boolean verifyResult = false;
		if (context != null && context.containsKey(property)) {
			if(!(expectedValue.getClass() == context.get(property).getClass())) {
				if (logger.isDebugEnabled())
					logger.debug("Returning verifyProperty() :: false - IncompatibleType");
			} else {
				if (expectedValue.equals(context.get(property))) {
					verifyResult = true;
				}
			}
		} 
		if (logger.isDebugEnabled())
			logger.debug("Returning verifyProperty() :: " + verifyResult);
		return verifyResult;

	}
	
	/**
	 * This method will detect if the input passed from VG is DTMF. 1. Get the
	 * text from the input using inputText method 2. Trim any space in the input
	 * * 3. Check if length is one 4. Verify if the received input is a number
	 * by invoking Integer.parseInt 5. If NumberFormatException is thrown, the
	 * input received is not a digit.
	 * 
	 * @param request
	 * @return isDigit::boolean
	 */
	public static boolean isDigit(String text) {
		if (logger.isDebugEnabled())
			logger.debug("Entering isDigit() text - " + text);
		boolean isDigit = false;
		if (text.length() == 1) {
			try {
				/*
				 * Another option could be check if the received character is in
				 * range 0...9.
				 */
				Integer.parseInt(text);
				isDigit = true;
			} catch (NumberFormatException exception) {
				if (text.equals("#") || text.equals("*"))
					isDigit = true;
				else
					isDigit = false;
			}
		}
		if (logger.isDebugEnabled())
			logger.debug("Exiting isDigit() isDigit - " + isDigit);
		return isDigit;
	}
	
	
	/**
	 * Check if the input is '#' In a typical IVR Applicaiton, '#' is treated a
	 * DTMF Term Char indicating termination of user input. In case #, router
	 * will assume end of input and trigger conversation.
	 * 
	 * @param request
	 * @return
	 */
	public static boolean isTermChar(String text) {
		if (logger.isDebugEnabled())
			logger.debug("Entering isTermChar() text - " + text);
		boolean isTermChar = false;
		if (text.length() == 1) {
			if ("#".equals(text))
				isTermChar = true;
		}
		if (logger.isDebugEnabled())
			logger.debug("Exiting isTermChar() isTermChar - " + isTermChar);
		return isTermChar;
	}
	
	/**
	 * This method identifies if a timeout happened during DTMF input sequence.
	 * 
	 * @param text
	 * @return
	 */
	public static boolean isTimeoutDuringDTMF(String text) {
		if (logger.isDebugEnabled())
			logger.debug("Entering isTimeoutDuringDTMF() text - " + text);
		boolean isTimeOut = false;
		if (text != null && INPUT_TIMEOUT.equalsIgnoreCase(text))
			isTimeOut = true;
		if (logger.isDebugEnabled())
			logger.debug("Exiting  isTimeoutDuringDTMF() isTimeOut - " + isTimeOut);
		return isTimeOut;
	}
	
}
