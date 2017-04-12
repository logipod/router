package com.servion.watson.router;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

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
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageRequest;
import com.ibm.watson.developer_cloud.conversation.v1.model.MessageResponse;
import com.ibm.watson.developer_cloud.http.ServiceCall;
import com.ibm.watson.developer_cloud.util.GsonSingleton;

/**
 * @author larsen.mallick
 *
 *         This is a plain proxy to test data forward. Does not invoke any additional client.
 *         Orchestrator and Covnersation service. Complies Proxy pattern.
 */
@Path("plain")
public class PlainProxy {

	private final static Logger logger = Logger.getLogger(PlainProxy.class);

	private ConversationService service = new ConversationService(ConversationService.VERSION_DATE_2016_07_11,
			"89315619-69b1-4641-a9a3-8cb29350a8cd", "usktlkSLDxUT");

	@Path("/v1/workspaces/{workspaceid}/message")
	@Consumes({ MediaType.APPLICATION_JSON })
	@POST
	public String postRequest(@Context Request request, @PathParam("workspaceid") String workspaceid,
			@QueryParam("version") String version, @Context Response httpResponse) {

		Map<String, Object> context = null;
		MessageRequest proxyRequestMsg = null;
		MessageResponse conversationResponseMsg = null;
		ServiceCall<MessageResponse> serviceCall = null;

		try {

			Buffer postBody = request.getPostBody(0);
			JsonObject requestBodyJson = GsonSingleton.getGson().fromJson(postBody.toStringContent(), JsonElement.class)
					.getAsJsonObject();
			String input = requestBodyJson.getAsJsonObject("input").get("text").getAsString();
			if (requestBodyJson.getAsJsonObject("context") != null) {
				String contextAsString = requestBodyJson.getAsJsonObject("context").toString();
				context = GsonSingleton.getGson().fromJson(contextAsString, new TypeToken<HashMap<String, Object>>(){}.getType());
			}
			proxyRequestMsg = new MessageRequest.Builder().inputText(input).context(context).build();
			serviceCall = service.message(workspaceid, proxyRequestMsg);
			conversationResponseMsg = serviceCall.execute();
			context = conversationResponseMsg.getContext();

		} catch (Exception exception) {

			logger.error("Looks like something went wrong!", exception);
			updateTextForFailureResponse(conversationResponseMsg);

		} finally {


			if (logger.isInfoEnabled()) {
				if (proxyRequestMsg.context() != null && proxyRequestMsg.context().containsKey("vgwSIPCallID")) {
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
	 *
	 * This method is called to manually update the text to be played to the
	 * caller. This method, for now, is only used when something goes wrong and
	 * the caller will be played a fialure notification.
	 *
	 * @param response
	 * @return
	 */
	private void updateTextForFailureResponse(MessageResponse response) {
		updateTextInResponse(response, "text",
				new String[] { "Sorry! The requested operation failed. " + "Please call us again in sometime" });

		if (response.getContext() != null)
			response.getContext().put("vgwHangUp", "Yes");

		return;
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

}
