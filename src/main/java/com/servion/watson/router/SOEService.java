package com.servion.watson.router.service;

import static com.servion.watson.router.ApplicationConstants.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import com.ibm.watson.developer_cloud.conversation.v1.ConversationService;
import com.ibm.watson.developer_cloud.conversation.v1.model.Entity;
import com.ibm.watson.developer_cloud.conversation.v1.model.Intent;
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageRequest;
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageResponse;
import com.ibm.watson.developer_cloud.http.ServiceCall;
import com.ibm.watson.developer_cloud.util.GsonSingleton;
import com.servion.watson.router.ApplicationUtils;
import com.servion.watson.router.exceptions.BusinessAppCommunicationException;
import com.servion.watson.router.exceptions.EmptyBodyException;

/**
 * @author larsen.mallick
 *
 *         Disclaimer: This is created as part of a hobby project, so kindly do
 *         not expect proper package structure. :) So here it goes -
 * 
 *         Provides a platform for customizing input and output between SIP
 *         Orchestrator and Conversation service. Complies Proxy pattern.
 * 
 */
@Path("soe")
public class SOEService {

	private final static Logger logger = Logger.getLogger(SOEService.class);

	/*
	 * sessionID is used to maintain session on the business application side.
	 */
	private String sessionID = null;

	/**
	 * This method is required to do the following - 1. Get request from SIP
	 * ORchestrator 2. Parse the request body and take out input text and
	 * context 3. Re-construct MessageRequest with the input and context
	 * information from step-2 4. Invoke conversation service and get back
	 * MesssageResponse 5. From the response, get the context param -
	 * actionRequested to know if conversation requires some info from business
	 * application 6. Based on the actionRequested, hit appropriate URL to get
	 * business data 7. Construct MessageRequest object and pass on the business
	 * data to conversation service 8. To maintain session with the business
	 * application, sessionID will be included in context param
	 *
	 * @param request
	 * @param workspaceid
	 *            - conversation workspace ID. This is generated by IBM
	 *            Conversation Studio.
	 * @param version
	 *            - not used as of now
	 * @param httpResponse
	 * @return
	 */
	@Path("/v1/workspaces/{workspaceid}/message")
	@Consumes({ MediaType.APPLICATION_JSON })
	@POST
	public String postRequest(@Context Request request, @PathParam("workspaceid") String workspaceid,
			@QueryParam("version") String version, @Context Response httpResponse) {

		/*
		 * Represents the input given by the caller. This input comes from STT
		 * -> Media Relay -> SIP Orchestrator -> Conversation Client API
		 */
		String input = null;

		/*
		 * Context object is passed back & forth between conversation client and
		 * service to maintain session
		 */
		Map<String, Object> context = null;

		/*
		 * New request message constructed from the input and context received
		 * from SIP Orchestrator. This represents a proxy to the actual request
		 * generated by SIP Orchestrator
		 */
		MessageRequest proxyRequestMsg = null;

		/*
		 * Response message returned from Conversation Service. This will be
		 * parsed to understand the action requested by the caller.
		 */
		MessageResponse conversationResponseMsg = null;

		/**
		 * Create an instance of conversation service
		 */
		ConversationService service = new ConversationService(ConversationService.VERSION_DATE_2016_07_11,
				CONVERSATION_SERVICE_USER_NAME, CONVERSATION_SERVICE_PASSWORD);

		/*
		 * Represents a synchronous call to conversation service.
		 */
		ServiceCall<MessageResponse> serviceCall = null;

		/*
		 * Indicates the API call to be executed in the business application
		 */
		String actionRequested = null;

		/*
		 * After business application hit, intent will be manipulated by the
		 * router service and sent to conversation service
		 */
		String intent = null;

		/*
		 * This indicates the status of the HTTP URL operation in order to get
		 * business data. Possible values are success or failure.
		 */
		String dataFetchResult = null;

		try {

			/*
			 * 1. Get body of the request 2. Convert request to Json 3. Get
			 * input provided by the caller and the context object
			 */
			Buffer postBody = request.getPostBody(0);

			/*
			 * Null message looks to be an error with SIP Orchestrator. This is
			 * not a retrieval option. This is only for monitoring. Since post
			 * body is empty, context will not be available to continue with the
			 * conversation.
			 */
			if (postBody.toStringContent() == null || postBody.toStringContent().isEmpty()) {
				throw new EmptyBodyException();
			}

			JsonObject requestBodyJson = GsonSingleton.getGson().fromJson(postBody.toStringContent(), JsonElement.class)
					.getAsJsonObject();
			input = requestBodyJson.getAsJsonObject(FIELD_INPUT).get(FIELD_TEXT).getAsString();
			if (logger.isDebugEnabled())
				logger.debug("Input received from VG => " + input);

			if (requestBodyJson.getAsJsonObject(FIELD_CONTEXT) != null) {
				String contextAsString = requestBodyJson.getAsJsonObject(FIELD_CONTEXT).toString();
				context = GsonSingleton.getGson().fromJson(contextAsString, new TypeToken<HashMap<String, Object>>() {
				}.getType());
				if (logger.isDebugEnabled())
					logger.debug("Context received from VG => " + context);
			}

			/*
			 * 
			 * (vgwAllowDTMF) is set true by conversation service only where
			 * application needs DTMF input.
			 * 
			 * ---------------------------------------------------- i. Check the
			 * expected input-type (DTMF or Speech) a. Checks if that’s a DTMF
			 * Input (Single Digit Input) i. Construct mock response for Single
			 * Digit DTMF Input ii. Concatenate the dtmf input and store
			 * oncontext b. If the input is ‘#’ i. Create a proxy request and
			 * hit conversation service c. If the transcription from STT service
			 * is not a single digit, return an error message requesting DTMF
			 * Input only If the expected input is not DTMF and SOE receives
			 * speech inputs i. Create a proxy request and hit conversation
			 * service ----------------------------------------------------
			 * 
			 */
			if (ApplicationUtils.verifyProperty(context, VGW_ALLOW_DTMF, YES)) {

				input = (input != null) ? input.trim() : "";

				if (NUMBERS_MAP.containsKey(input))
					input = NUMBERS_MAP.get(input);

				if (ApplicationUtils.isDigit(input)) {

					if (ApplicationUtils.isTermChar(input) || isReachedMaxLength(context, input)) {
						input = finalizeDTMFInput(context);
					} else {
						conversationResponseMsg = mockResponseForDTMF(input, context);
					}

				} else {

					if (ApplicationUtils.isTimeoutDuringDTMF(input)) {
						if (((String) context.get(DTMF_INPUT)).length() > 0) {
							input = finalizeDTMFInput(context);
						} else {
							conversationResponseMsg = expectingDTMFInput(context,
									"Please key-in the requested information."
											+ " And after entering the desired input, enter hash key or pound sign.");
						}
					} else {
						conversationResponseMsg = expectingDTMFInput(context,
								"Sorry! I did not understand that." + " You can give a touch-tone input."
										+ " If you are done," + " you can enter hash key or pound sign.");
					}
				}

			}

			while (conversationResponseMsg == null) {

				if (input != null)
					proxyRequestMsg = new MessageRequest.Builder().inputText(input).context(context).build();
				else
					proxyRequestMsg = new MessageRequest.Builder().intent(new Intent(intent, 1.0)).context(context)
							.build();
				if (logger.isDebugEnabled())
					logger.debug("ConversationService::Proxy_Request " + proxyRequestMsg);

				serviceCall = service.message(workspaceid, proxyRequestMsg);

				conversationResponseMsg = serviceCall.execute();
				if (logger.isDebugEnabled())
					logger.debug("ConversationService::Conversation_Response " + conversationResponseMsg);

				context = conversationResponseMsg.getContext();
				if (logger.isDebugEnabled())
					logger.debug("ConversationService::Conversation_Context " + context);

				/*
				 * businessHitRequested will be included included in context by
				 * conversation If true, business application hit will be
				 * performed After business application hit, this parameter will
				 * need to reset in context
				 */
				if (ApplicationUtils.verifyProperty(context, BUSINESS_HIT_REQUESTED, true)) {

					/*
					 * Look for session id in the context object. Session ID
					 * will be used to stick session with the conversation
					 * service session ID will not be available if no business
					 * application hit is performed till the point in the
					 * current conversation
					 */
					sessionID = (context.containsKey(HTTP_SESSION_ID)) ? (String) context.get(HTTP_SESSION_ID) : null;
					if (logger.isDebugEnabled())
						logger.debug("ConversationService::Conversation_sessionID " + sessionID);

					/*
					 * Conversation service will define the action to be
					 * performed based on the caller intent Get the action
					 * requested from context object After performing the
					 * action, action requested will be either set to an empty
					 * string
					 */
					actionRequested = (context.containsKey("actionRequested")) ? (String) context.get("actionRequested")
							: null;
					if (logger.isDebugEnabled())
						logger.debug("ConversationService::Conversation_actionRequested " + actionRequested);

					/*
					 * Business application URL will be invoked and requried
					 * data will be updated in context while the method returns
					 * either success of failure This method will throw
					 * IllegalStateException if the requried request parameter
					 * is not passed by conversation service.
					 */
					if (logger.isDebugEnabled())
						logger.debug("Calling getBusinessData.");
					if (actionRequested.equals("transferToAgent"))
						dataFetchResult = "success";
					else
						dataFetchResult = getBusinessData(actionRequested, context);

					/*
					 * After business application hit, this parameter will need
					 * to reset in context
					 */
					if (logger.isDebugEnabled())
						logger.debug("Update Context::actionRequested removed");
					context.remove("actionRequested");

					/*
					 * After business application hit, session ID will be
					 * maintained in conversation context to manage cookies
					 */
					if (logger.isDebugEnabled())
						logger.debug("Update Context::sessionID = " + sessionID);
					context.put(HTTP_SESSION_ID, sessionID);

					/*
					 * After business application hit, this parameter will need
					 * to reset in context
					 */
					if (logger.isDebugEnabled())
						logger.debug("Update Context::informationRequested removed");
					context.remove("informationRequested");

					/*
					 * After business application hit, this parameter will need
					 * to reset in context
					 */
					if (logger.isDebugEnabled())
						logger.debug("Update Context::businessHitRequested removed");
					context.remove(BUSINESS_HIT_REQUESTED);

					/*
					 * Intent will be updated and sent to conversation service
					 * to drive the dialog. Possible values are:
					 * identify_success/identify_failure
					 * getProfile_success/getProfile_failure
					 * acctStatus_success/acctStatus_failure This will be
					 * computed as a concatenation of
					 * actionRequested+Success/Failure.
					 */
					intent = actionRequested + "_" + dataFetchResult;
					if (logger.isDebugEnabled())
						logger.debug("Update intent => " + intent);

					/*
					 * change the input to null, since router will have to reach
					 * conversation while returning from business application.
					 * Input is only a transcription result from STT.
					 */
					input = null;
					if (logger.isDebugEnabled())
						logger.debug("Update input => " + input);

					/*
					 * changing conversation response to null since router will
					 * have to reach conversation again to get the action of
					 * business application failure or success.
					 */
					conversationResponseMsg = null;
					if (logger.isDebugEnabled())
						logger.debug("Update conversationResponseMsg => " + conversationResponseMsg);

				} else {

					if (ApplicationUtils.verifyProperty(context, "vgwTransfer", YES)) {
						context.put("vgwTransferTarget", "sip:8002@ped.servion.com");

						if (logger.isDebugEnabled())
							logger.debug("ConversationService::Conversation_vgwTransferTarget "
									+ context.get("vgwTransferTarget"));
					}

				}

			}

		} catch (Exception exception) {

			if (exception instanceof BusinessAppCommunicationException) {

				/*
				 * Indicates communication with business layer failed. Please
				 * debug getBusinessData method & controllerCall method.
				 */
				logger.error("Communication with business layer failed!", exception);

			} else if (exception instanceof EmptyBodyException) {

				/*
				 * This exception is thrown when post body is empty.
				 */
				logger.error("Request from Voice Gateway is empty!", exception);

			} else {
				/*
				 * This is a generic catcher.
				 */
				logger.error("Looks like something went wrong!", exception);

				/*
				 * Upon getting any exception, play generic error message and
				 * listen for caller input.
				 */
			}

			updateTextForFailureResponse(conversationResponseMsg);

		} finally {

			addCommonVGParams(conversationResponseMsg);

			if (logger.isInfoEnabled()) {
				/*
				 * SEND_SIP_CALL_ID_TO_CONVERSATION in docker-compose has to be
				 * true. the SIP call ID is passed to the Conversation in this
				 * state variable: vgwSIPCallID MDC update will be used as key
				 * for ELK Monitoring.
				 */
				if (proxyRequestMsg != null && proxyRequestMsg.context() != null
						&& proxyRequestMsg.context().containsKey("vgwSIPCallID")) {
					MDC.put("vgwSIPCallID", proxyRequestMsg.context().get("vgwSIPCallID"));
					logger.info("Input: " + proxyRequestMsg.toString());
				}
				logger.info("Output: " + conversationResponseMsg.toString());
				if (MDC.get("vgwSIPCallID") != null)
					MDC.remove("vgwSIPCallID");
			}
		}

		return conversationResponseMsg.toString();

	}

	/**
	 * This method will format the URL with the request parameters and invoke
	 * the controller. The returned data will be parsed and the context will be
	 * updated with the required details that will be used by conversation
	 * service.
	 *
	 * @param actionRequested
	 * @param context
	 */
	private String getBusinessData(String actionRequested, Map<String, Object> context)
			throws BusinessAppCommunicationException {

		/*
		 * This list will hold the request parameters to be included in the
		 * URLs. The list will be converted to var-args and passed to
		 * String.format to build full URL
		 */
		List<String> requestParameters = new ArrayList<String>();

		/*
		 * Holds the formated URL with the required request parameters
		 */
		String controllerURL = "";

		/*
		 * Conversation service will collect inputs (identifierNumber,
		 * secretNumber & accountNumber) and pass along the context. logout
		 * doesn't require any request parameter and hence no specific handling
		 * to add request parameters.
		 */

		if (context.get("requestParam") != null) {
			Object requestParam = context.get("requestParam");
			if (requestParam != null && requestParam instanceof Double)
				requestParam = Double.toString((Double) requestParam);
			requestParameters.add((String) requestParam);
			context.remove("requestParam");
		}

		/*
		 * This will be used to pull specific information within an API call -
		 * for example: getting pendingDebitsAmount from MLI-4501
		 */
		if (context.get("informationRequested") != null)
			requestParameters.add((String) context.get("informationRequested"));
		else
			requestParameters.add("");

		try {
			controllerURL = String.format(urlsMap.get(actionRequested),
					requestParameters.toArray(new String[requestParameters.size()]));
		} catch (Exception exception) {

			/*
			 * This could happen when URL formating fails. Please note: Illegal
			 * Argument Exception was replaced for customization.
			 *
			 */
			logger.error("URL formating failed: " + urlsMap.get(actionRequested) + " <-> request parameters: "
					+ requestParameters, exception);
			return "failure";
		}

		return (controllerCall(controllerURL, context) ? "success" : "failure");
	}

	/**
	 *
	 * This method will invoke the controller and get back the business data.
	 * Business data will be returned as a JSON String and includes a
	 * success/failure identifier. success/failure identifier will be based on
	 * the MLI response action code
	 *
	 * Note: Basic implementation copied from Apache Http Client Sample and
	 * modified as required to retain session and cookies.
	 *
	 * @param controllerURL
	 * @param context
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	private boolean controllerCall(String controllerURL, Map<String, Object> context)
			throws BusinessAppCommunicationException {

		/*
		 * Updated based on the result from URL call. Set to false in case of
		 * exceptions where in further processing could not be proceeded.
		 */
		boolean responseSuccessful = false;

		HttpClient client = HttpClientBuilder.create().build();

		HttpGet request = new HttpGet(controllerURL);

		/*
		 * JSESSIONID is included manually in cookie to stick session. This is
		 * automatically done by browsers but from a stand-alone java
		 * application this has to be done manually.
		 */
		request.addHeader("Cookie", sessionID);

		/*
		 * Execute HTTP Client Request. In case of any exceptions, catch the
		 * exception and return false to indicate failure. For a successful
		 * response, get the result and convert to JSON Object. Access the JSON
		 * object and update the required parameters in Context, that will be
		 * used by conversation service.
		 */
		HttpResponse response = null;
		String responseString = null;
		try {
			response = client.execute(request);
			/*
			 * Gets the self-contained entity from the http response Convert
			 * HTTP entity to String using Static helpers from
			 * org.apache.http.util.EntityUtils
			 */
			HttpEntity entity = response.getEntity();
			try {
				responseString = EntityUtils.toString(entity, "UTF-8");
				if (responseString == null || responseString.isEmpty()) {
					throw new BusinessAppCommunicationException();
				}
				sessionID = (sessionID == null) ? response.getHeaders("Set-Cookie")[0].getValue().split(";")[0]
						: sessionID;
			} catch (ParseException | IOException e) {
				throw e;
			}
		} catch (Exception e) {
			responseSuccessful = false;
			throw new BusinessAppCommunicationException();
		}

		/*
		 * 1. Convert response string to json object 2. Get status from response
		 * json object
		 */
		org.json.JSONObject jsonResponse = new org.json.JSONObject(responseString);
		String status = jsonResponse.getString("status");
		responseSuccessful = (status != null & status.equals("success")) ? true : false;

		/*
		 * 1. Get the output from controller as a json object 2. Get the entry
		 * set from the output json object 3. Copy <K,V> pairs from output json
		 * to context map
		 *
		 * Due to serialization impedance, org.json library will be used. (Check
		 * application controllers)
		 */
		if (jsonResponse.has("outputs")) {
			org.json.JSONObject apiResult = jsonResponse.getJSONObject("outputs");
			Iterator<?> keys = apiResult.keys();
			while (keys.hasNext()) {
				String key = (String) keys.next();
				context.put(key, apiResult.getString(key));
			}
		}
		return responseSuccessful;
	}

	/**
	 * Returns an empty response. Dummy Intents and Entities included to
	 * overcome VG error - java.lang.NullPointerException at
	 * com.ibm.ws.cgw.api.impl.CGWSessionImpl.putIntents(CGWSessionImpl.java:265)
	 * 
	 * @return
	 */
	private MessageResponse createEmptyResponse() {

		if (logger.isDebugEnabled())
			logger.debug("Entering createEmptyResponse()");

		MessageResponse response = new MessageResponse();

		List<Intent> intents = new ArrayList<>();
		response.setIntents(intents);
		List<Entity> entities = new ArrayList<>();
		response.setEntities(entities);

		Map<String, Object> output = new HashMap<>();
		response.setOutput(output);

		if (logger.isDebugEnabled())
			logger.debug("Exiting createEmptyResponse() response - " + response);

		return response;
	}

	/**
	 *
	 * This method is used to tweak the JSON object by modifying or adding new
	 * K,V to output JSON in the Response Object
	 *
	 * @param response
	 * @param property
	 * @param values
	 * @return
	 */
	private void updateTextInResponse(MessageResponse response, String property, String[] values) {
		JsonArray array = new JsonArray();
		for (String value : values) {
			array.add(new JsonPrimitive(value));
		}
		response.getOutput().put(property, array);
		return;
	}

	/**
	 *
	 * This method is called to manually update the text to be played to the
	 * caller. This method, for now, is only used when something goes wrong and
	 * the caller will be played a fialure notification.
	 *
	 * @param response
	 * @return
	 */
	private void updateTextForFailureResponse(MessageResponse response) {

		if (response == null)
			response = createEmptyResponse();

		updateTextInResponse(response, "text",
				new String[] { "Sorry! The requested operation failed. " + "Please call us again in sometime" });

		if (response.getContext() != null)
			response.getContext().put(CALL_HANG_UP, YES);

		return;
	}

	/**
	 * Returns a mock response when a speech input is received while expecting a
	 * Touch-tone input.
	 * 
	 * @param request
	 * @return
	 */
	private MessageResponse expectingDTMFInput(Map<String, Object> context, String text) {
		if (logger.isDebugEnabled())
			logger.debug("Entering expectingDTMFInput() context - " + context);
		MessageResponse response = createEmptyResponse();
		response.setContext(context);
		updateTextInResponse(response, "text", new String[] { text });
		if (logger.isDebugEnabled())
			logger.debug("Returning expectingDTMFInput() response - " + response);
		return response;
	}

	/**
	 * This method is responsible to return a mock response to VG while
	 * collecting DTMF input. If a DTMF input is received by the router, the
	 * router starts concatenating the DTMF input on context and returns back to
	 * VG with the current context.
	 * 
	 * and
	 * 
	 * @param request
	 * @return
	 */
	private MessageResponse mockResponseForDTMF(String text, Map<String, Object> context) {
		if (logger.isDebugEnabled())
			logger.debug("Entering mockResponseForDTMF() text - " + text);
		if (logger.isDebugEnabled())
			logger.debug("Entering mockResponseForDTMF() context - " + context);
		String dtmfInput = "";
		if (context.get(DTMF_INPUT) != null) {
			dtmfInput = (String) context.get(DTMF_INPUT);
			if (logger.isDebugEnabled())
				logger.debug("Inside mockResponseForDTMF() dtmfInput from context - " + dtmfInput);
		}

		dtmfInput = dtmfInput.concat(text);
		if (logger.isDebugEnabled())
			logger.debug("Inside mockResponseForDTMF() updated dtmfInput - " + dtmfInput);

		context.put(DTMF_INPUT, dtmfInput);
		if (logger.isDebugEnabled())
			logger.debug("Inside mockResponseForDTMF() updated context - " + context);

		if (context.containsKey("vgwTextAlternatives"))
			context.remove("vgwTextAlternatives");

		if (!context.containsKey(STATE_COLLECTING_DTMF) || (context.containsKey(STATE_COLLECTING_DTMF)
				&& NO.equalsIgnoreCase((String) context.get(STATE_COLLECTING_DTMF))))
			context.put(STATE_COLLECTING_DTMF, YES);

		MessageResponse response = createEmptyResponse();
		response.setContext(context);

		if (logger.isDebugEnabled())
			logger.debug("Returning mockResponseForDTMF() response - " + response);
		return response;
	}

	/**
	 * The below listed will be treated as common VG Param and will be sent back
	 * to VG on every response from Router. The primary reason, these values are
	 * reset/removed from context every time a response is sent back to VG from
	 * Conversation. So instead of setting these in each of the nodes, we will
	 * set commonly in Router.
	 *
	 *
	 * • vgwTransferFailedMessage • vgwConversationFailedMessage •
	 * vgwMusicOnHoldURL
	 *
	 * Whereas , the following values need to set on the initial node in the
	 * conversation since these are not rest unless specifically removed by
	 * Conversation/Router.
	 *
	 * • vgwAllowBargeIn • vgwAllowDTMF • vgwOneTimeAudioURL (only if required)
	 *
	 * This method also sets -
	 *
	 * - vgwSTTConfigSettings: API parameters for the Speech to Text service -
	 * vgwTTSConfigSettings: API parameters for the Text to Speech service
	 *
	 * This is very important, please read developer comments before modifying
	 * this method.
	 *
	 * Changing the configuration for the Speech to Text service causes the
	 * connection from the voice gateway to the Speech to Text service to
	 * disconnect and reconnect, which might cause the voice gateway to miss
	 * part of an utterance. Typically, the connection is reestablished while
	 * audio is streamed to the caller from the Conversation response, which
	 * avoids missing any part of an utterance unless the caller barges in
	 * quickly.
	 *
	 * @param response
	 */
	private void addCommonVGParams(MessageResponse response) {

		if (logger.isDebugEnabled())
			logger.debug("Entering addCommonVGParams() response - " + response);

		if (!ApplicationUtils.verifyProperty(response.getContext(), CALL_HANG_UP, YES)) {

			response.getContext().put(ALLOW_BARGE_IN, YES);

			/*
			 * Default value for Allow DTMF is agreed to be NO But in case
			 * conversation sends YES (Ex: PIN Collection), this is a special
			 * indication and has to be passed back to VG.
			 */
			if (ApplicationUtils.verifyProperty(response.getContext(), VGW_ALLOW_DTMF, YES)) {
				response.getContext().put(VGW_PAUSE_STT, YES);
			} else {
				response.getContext().put(VGW_ALLOW_DTMF, NO);
				response.getContext().put(VGW_PAUSE_STT, NO);
			}

			response.getContext().put(POST_RESPONSE_TIMEOUT, STANDARD_POST_RESPONSE_TIMEOUT);

			/*
			 * one time audio : A URL to an audio file that is played a single
			 * time as soon as the included text is played back, such as for
			 * one-time utterances. This could be used to override
			 * vgwMusicOnHoldURL from conversation
			 */
			response.getContext().put(POST_RESPONSE_ONE_TIME_AUDIO, POST_RESPONSE_AUDIO_VALUE_CUT);

			if (ApplicationUtils.verifyProperty(response.getContext(), STATE_COLLECTING_DTMF, YES)) {
				response.getContext().put(POST_RESPONSE_TIMEOUT, DTMF_POST_RESPONSE_TIMEOUT);
				response.getContext().remove(POST_RESPONSE_ONE_TIME_AUDIO);
			}

		}

	}

	/**
	 * A list of keywords to spot in the audio. Each keyword string can include
	 * one or more tokens. Keywords are spotted only in the final hypothesis,
	 * not in interim results. Omit the parameter or specify an empty array if
	 * you do not need to spot keywords.
	 * 
	 * Adds list of keywords to Conversation response. Called when vgwAllowDTMF
	 * = true.
	 * 
	 * @param response
	 */
	@SuppressWarnings("unused")
	private void addKeywordsForSTT(MessageResponse response) {

		/*
		 * Set custom STT Parameters as required. These will override the
		 * parameters set by conversation service.
		 */
		JsonObject vgwSTTConfigSettings = new JsonObject();
		JsonObject vgwSTTConfigParams = new JsonObject();
		/* Add more STT Settings here - Start */

		JsonArray keywords = new JsonArray();
		for (String digit : DIGITS) {
			keywords.add(digit);
		}
		vgwSTTConfigParams.add(WATSON_STT_KEYWORDS, keywords);

		/* Add more STT Settings here - End */
		vgwSTTConfigSettings.add(CONFIG, vgwSTTConfigParams);

		response.getContext().put(STT_CONFIG_SETTINGS, vgwSTTConfigSettings);

	}

	/**
	 * 1. Get the input received. 2. Take out DTMF input from context 3.
	 * Concatenate the input received with the dtmf input taken from context 4.
	 * check the length of concatenated input 5. if length = max_length, return
	 * true
	 * 
	 * Note: Max-Length will be set by conversation for each DTMF enabled node.
	 * 
	 * @param text
	 * @return
	 */
	private boolean isReachedMaxLength(Map<String, Object> context, String text) {
		if (logger.isDebugEnabled())
			logger.debug("Entering isReachedMaxLength() text - " + text);
		boolean isMaxLengthReached = false;
		String maxLengthAllowed = context.containsKey(MAX_LENGTH) ? (String) context.get(MAX_LENGTH) : null;
		if (logger.isDebugEnabled())
			logger.debug("Max Length From Context - " + maxLengthAllowed);
		if (maxLengthAllowed != null) {
			String dtmfInput = (String) context.get(DTMF_INPUT);
			logger.debug("isReachedMaxLength() :: Assigning input from dtmfInput in context " + dtmfInput);
			dtmfInput = dtmfInput.concat(text);
			if (dtmfInput.length() == Integer.valueOf(maxLengthAllowed)) {
				isMaxLengthReached = true;
				context.put(DTMF_INPUT, dtmfInput);
				logger.debug("isReachedMaxLength() :: Reached max length. Updating value in context." + dtmfInput);

			}
		}
		if (logger.isDebugEnabled())
			logger.debug("Exiting isReachedMaxLength() isMaxLengthReached - " + isMaxLengthReached);
		return isMaxLengthReached;
	}

	/**
	 * This will remove the dtmfInput from context.
	 * 
	 * @param context
	 * @return
	 */
	private String finalizeDTMFInput(Map<String, Object> context) {

		if (logger.isDebugEnabled())
			logger.debug("Entering finalizeDTMFInput()");

		String dtmfInput = (String) context.get(DTMF_INPUT);
		logger.debug("Assigning input from dtmfInput in context " + dtmfInput);

		/*
		 * Indicates termination of DTMF input. Remove/Reset vgwAllowDTMF &
		 * dtmfInput from context.
		 */
		context.put(VGW_ALLOW_DTMF, NO);
		context.put(DTMF_INPUT, null);
		context.put(STATE_COLLECTING_DTMF, NO);

		if (logger.isDebugEnabled())
			logger.debug("Exiting finalizeDTMFInput() :: dtmfInput - " + dtmfInput);
		return dtmfInput;

	}

}
