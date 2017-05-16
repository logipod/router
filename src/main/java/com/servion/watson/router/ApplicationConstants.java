package com.servion.watson.router;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
	
	public final static String TRUE = "true";

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
	 * Informs the voice gateway to pause Speech to Text processing. When
	 * applied, the same value is used in all subsequent transactions, unless a
	 * new request arrives that overrides the existing value.
	 */
	public final static String VGW_PAUSE_STT = "vgwPauseSTT";

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
	 * no-input timeout for DTMF States
	 */
	public static final String DTMF_POST_RESPONSE_TIMEOUT = "5000";

	/**
	 * Hang-Up indicator. Sent to VG.
	 */
	public static final String CALL_HANG_UP = "vgwHangUp";

	/**
	 * One time audio to play the caller till input is received
	 */
	public static final String POST_RESPONSE_ONE_TIME_AUDIO = "vgwOneTimeAudioURL";

	/**
	 * A URL to an audio file that is played in a loop as soon as the included
	 * text is played back, such as for playing music on hold (MOH)
	 */
	public static final String POST_RESPONSE_ON_HOLD_AUDIO = "vgwMusicOnHoldURL";

	/**
	 * Audio to be played upon conversation response play-back
	 */
	public static final String POST_RESPONSE_AUDIO_VALUE_CUT = "https://raw.githubusercontent.com/larsen-mallick/conversation_router/master/musicOnHoldSample_cut.wav";

	/**
	 * Audio to be played when SOE performs API hit
	 */
	public static final String POST_RESPONSE_AUDIO_VALUE = "https://raw.githubusercontent.com/WASdev/sample.voice.gateway/master/audio/musicOnHoldSample.wav";

	public static final String CONVERSATION_SERVICE_USER_NAME = "89315619-69b1-4641-a9a3-8cb29350a8cd";

	public static final String CONVERSATION_SERVICE_PASSWORD = "xxxxx";

	public static final String HTTP_SESSION_ID = "sessionID";

	public static final String INPUT_TIMEOUT = "vgwPostResponseTimeout";

	/**
	 * Indicates the DTMF Input collected from VG
	 */
	public static final String DTMF_INPUT = "dtmfInput";

	public static final String WATSON_STT_SMART_FORMATTING = "smartFormatting";

	public static final String WATSON_STT_KEYWORDS = "keywords";

	public static final String STT_CONFIG_SETTINGS = "vgwSTTConfigSettings";

	public static final List<String> DIGITS = Arrays.asList(new String[] { "zero", "one", "two", "three", "four",
			"five", "six", "seven", "eight", "nine", "hash", "pound", "#", "*", "star" });

	public static final Map<String, String> NUMBERS_MAP = new HashMap<String, String>() {

		/**
		 * Random generated serialization ID will be used. This is generated
		 * based on the class name, number of parameters. This could lead to a
		 * problem in case of a different variant of JVM is used for
		 * de-serialization.
		 */
		private static final long serialVersionUID = 2722118959672149259L;

		{
			put("one", "1");
			put("two", "2");
			put("to", "2");
			put("three", "3");
			put("four", "4");
			put("for", "4");
			put("five", "5");
			put("six", "6");
			put("seven", "7");
			put("eight", "8");
			put("nine", "9");
			put("zero", "0");
			put("pound", "#");
			put("hash", "#");
			put("start", "*");

		}
	};

	public static final String CONFIG = "config";

	/**
	 * Maximum allowed length of DTMF Input is determined by the maxLength
	 * property set by conversation in context.
	 */
	public static final String MAX_LENGTH = "maxLength";

	/**
	 * Indicates call is in a state of collecting DTMF Input
	 */
	public static final String STATE_COLLECTING_DTMF = "collectingDTMF";

	/**
	 * Forces a new turn with no input from the Media Relay.
	 */
	public static final String FORCE_NO_INPUT = "vgwForceNoInputTurn";

	/**
	 * This is similar to vgNoInputResponseTimeout. Received from VG when
	 * vgwForceNoInputTurn = Yes
	 */
	public static final String VG_NO_INPUT_TURN = "vgwNoInputTurn";
	
	/**
	 * authenticated indicator in context
	 */
	public static final String CALLER_AUTHENTICATED = "authenticated";

	/**
	 * Indicates if business data is required. Business data is required
	 * when conversation service needs customer specific information
	 */
	public static final String BUSINESS_HIT_REQUESTED = "businessHitRequested";
}
