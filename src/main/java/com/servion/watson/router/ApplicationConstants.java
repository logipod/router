package com.servion.watson.router;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ApplicationConstants {

	// Base URI the Grizzly HTTP server will listen on
    public static final String BASE_URI = "http://0.0.0.0:8080/conversationproxy/";

    /**
	 * Business application hostname & port
	 */
	private static final String BUSINESS_APP_ROOT = "http://172.16.14.23:8090/ivr.web/rest/";

	public static final Map<String, String> urlsMap = new HashMap<String, String>() {

		/**
		 * Random generated serialization ID will be used. This is generated
		 * based on the class name, number of parameters. This could lead to a
		 * problem in case of a different variant of JVM is used for
		 * de-serialization.
		 */
		public static final long serialVersionUID = -6304439467917487175L;

		{
			put("identify", BUSINESS_APP_ROOT + "identify?identifierNumber=%s&informationRequested=%s");
			put("getProfile", BUSINESS_APP_ROOT + "getProfile?secretNumber=%s&informationRequested=%s");
			put("acctStatus", BUSINESS_APP_ROOT + "acctStatus?accountNumber=%s&informationRequested=%s");
			put("logout", BUSINESS_APP_ROOT + "logout");
		}
	};

	public final static String NO = "No";

	public final static String YES = "Yes";

	/**
	 * Constant representing input field in request object
	 *
	 */
	public final static String FIELD_INPUT = "input";

	/**
	 * Constant representing text field in input
	 */
	public final static String FIELD_TEXT = "text";

	/**
	 * Constant representing context field in request/response
	 */
	public final static String FIELD_CONTEXT = "context";

	/**
	 * Allow DTMF - Input for VG
	 */
	public final static String VGW_ALLOW_DTMF = "vgwAllowDTMF";

	/**
	 * Barge-In
	 */
	public final static String ALLOW_BARGE_IN = "vgwAllowBargeIn";

	/**
	 * No Input Timeout
	 */
	public final static String POST_RESPONSE_TIMEOUT = "vgwPostResponseTimeoutCount";

	/**
	 * Standard no-input timeout
	 */
	public static final String STANDARD_POST_RESPONSE_TIMEOUT = "15000";

	/**
	 * Hang-Up indicator. Sent to VG.
	 */
	public static final String CALL_HANG_UP = "vgwHangUp";

	/**
	 * One time audio to play the caller till input is received
	 */
	public static final String POST_RESPONSE_AUDIO = "vgwOneTimeAudioURL";

	/**
	 * Audio to be played upon conversation response play-back
	 */
	public static final String POST_RESPONSE_AUDIO_VALUE = "https://raw.githubusercontent.com/larsen-mallick/conversation_router/master/musicOnHoldSample_cut.wav";

	public static final String CONVERSATION_SERVICE_USER_NAME = "89315619-69b1-4641-a9a3-8cb29350a8cd";

	public static final String CONVERSATION_SERVICE_PASSWORD = "XXXXXXXXXXXXXXx";

	public static final String HTTP_SESSION_ID = "sessionID";

}
