package org.red5.signalling;

import org.json.JSONException;
import org.json.JSONObject;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

/**
 * 
 * @author Dmitry Bezheckov
 */

@ServerEndpoint(value = "/ws")
public class SignalConnection {
	
	private static final Logger LOG = Red5LoggerFactory.getLogger(SignalConnection.class, "signalling");
	private static final JSONObject LOGIN_SUCCESS_MESSAGE;
	private static final JSONObject LOGIN_FAIL_MESSAGE;
	private static final JSONObject LEAVE_MESSAGE;
	private static final JSONObject UNRECOGNIZED_COMMAND_MESSAGE;
	
	private static final HashMap<String, SignalConnection> users = new HashMap<String, SignalConnection>();
	
	static
	{
		JSONObject loginTrueMessage = null;
		JSONObject loginFalseMessage = null;
		JSONObject leaveMessage = null;
		JSONObject unrecognizedMessage = null;
		
		try {
			loginTrueMessage = new JSONObject()
					.put("type", "login")
					.put("success", "true");
			loginFalseMessage = new JSONObject()
					.put("type", "login")
					.put("success", "false");
			leaveMessage = new JSONObject().put("type", "leave");
			unrecognizedMessage = new JSONObject()
					.put("type", "error")
					.put("message", "Unrecognized command");
		} catch (JSONException e) {
			LOG.error("json constant haven't constructed " + e.getMessage());
		}
		
		LOGIN_SUCCESS_MESSAGE = loginTrueMessage;
		LOGIN_FAIL_MESSAGE = loginFalseMessage;
		LEAVE_MESSAGE = leaveMessage;
		UNRECOGNIZED_COMMAND_MESSAGE = unrecognizedMessage; 
	}
	
	private String otherName;
	private String name;
	private Session session;
	
    @OnOpen
    public void onOpen(Session session) throws IOException {
    	this.setSession(session);
        LOG.info("User connected");
    }

    @OnMessage
    public void onMessage(String message) throws IOException {
    	LOG.info("Got message: " + message);
    	
    	JSONObject data = null;
    	String type = null;
    	
    	try {
    		data = new JSONObject(message);
    		type = data.getString("type");
		} catch (JSONException e) {
			LOG.error("Error parsing JSON " + e.getMessage());
		}
    	
    	switch (type) {
    	case "login":
    		handleLogin(data);
    		break;
    	case "offer":
    		handleMessageNameWithJSONObj(data, "offer");
    		break;
    	case "answer":
    		handleMessageNameWithJSONObj(data, "answer");
    		break;
    	case "candidate":
    		handleCandidate(data);
    		break;
    	case "leave":
    		handleLeave(data);
    		break;
    	default:
    		getSession().getBasicRemote().sendText(UNRECOGNIZED_COMMAND_MESSAGE.toString());
    	}
    }

    private void handleLeave(JSONObject data) {
    	String targetName = null;
		
		try {
			targetName = data.getString("name");
		} catch (JSONException e) {
			LOG.error("Error parsing JSON " + e.getMessage());
		}
		
		if (targetName != null) {
			SignalConnection targetConnection = users.get(targetName);
			Session targetSession = targetConnection.getSession();
    		if (targetSession != null) {
    			LOG.info("Disconnecting user from: " + targetName);
        		
        		try {
					targetSession.getBasicRemote().sendText(LEAVE_MESSAGE.toString());
				} catch (IOException e) {
					LOG.error(e.getMessage());
				}
        		
        		targetConnection.setOtherName(null);
    		}
    		else {
    			LOG.error("Target session is null " + targetName);
    			LOG.error(users.toString());
    		}
		}
	}

	private void handleCandidate(JSONObject data) {
    	String targetName = null;
		JSONObject candidate = null;
		
		try {
			targetName = data.getString("name");
			candidate = data.getJSONObject("candidate");
		} catch (JSONException e) {
			LOG.error("Error parsing JSON " + e.getMessage());
		}
		
		if (targetName != null && candidate != null) {
			Session targetSession = users.get(targetName).getSession();
    		if (targetSession != null) {
    			LOG.info("Sending candidate to: " + targetName);
        		
        		try {
        			JSONObject sendObj = new JSONObject()
        					.put("type", "candidate")
        					.put("candidate", candidate);
					targetSession.getBasicRemote().sendText(sendObj.toString());
				} catch (IOException | JSONException e) {
					LOG.error(e.getMessage());
				}
    		}
    		else {
    			LOG.error("Target seesion is null " + targetName);
    			LOG.error(users.toString());
    		}
		}
	}

	@OnError
    public void onError(Throwable t) {
    	LOG.error("Error " + t.toString(), t);
    }

    @OnClose
    public void onClose() {
    	if (name != null) {
    		LOG.info("User " + name + " disconnected");
    		users.remove(name);
    		
    		if (getOtherName() != null) {
    			SignalConnection targetConnection = users.get(otherName);
    			Session targetSession = targetConnection.getSession();
        		if (targetSession != null) {
        			LOG.info("Disconnecting user from: " + getOtherName());
            		
            		try {
						targetSession.getBasicRemote().sendText(LEAVE_MESSAGE.toString());
					} catch (IOException e) {
						LOG.error(e.getMessage());
					}
            		
            		targetConnection.setOtherName(null);
        		}
        		else {
        			LOG.error("Target seesion is null " + getOtherName());
        			LOG.error(users.toString());
        		}
    		}
    	}	
    }
    
    private void handleMessageNameWithJSONObj(JSONObject data, String jsonObjName) {
    	String targetName = null;
		JSONObject jsonObject = null;
		
		try {
			targetName = data.getString("name");
			jsonObject = data.getJSONObject(jsonObjName);
		} catch (JSONException e) {
			LOG.error("Error parsing JSON " + e.getMessage());
		}
		
		if (targetName != null && jsonObject != null) {
			Session targetSession = users.get(targetName).getSession();
    		if (targetSession != null) {
    			LOG.info("Sending " + name + " to: " + targetName);
    			setOtherName(targetName);
    			
        		try {
        			JSONObject sendObj = new JSONObject()
        					.put("type", jsonObjName)
        					.put("name", name)
        					.put(jsonObjName, jsonObject);
					targetSession.getBasicRemote().sendText(sendObj.toString());
				} catch (IOException | JSONException e) {
					LOG.error(e.getMessage());
				}
    		}
    		else {
    			LOG.error("Target seesion is null " + targetName);
    			LOG.error(users.toString());
    		}
		}
    }
    
    private void handleLogin(JSONObject data) {
    	String id = null;
		
		try {
			id = data.getString("name");
		} catch (JSONException e) {
			LOG.error("Error parsing JSON " + e.getMessage());
		}
		
		if (id != null) {
			LOG.info("User logged in as " + id);
    		if (users.containsKey(id)) {
    			try {
					getSession().getBasicRemote().sendText(LOGIN_FAIL_MESSAGE.toString());
				} catch (IOException e) {
					LOG.error(e.getMessage());
				}
    		}
    		else {
    			users.put(id, this);
    			name = id;
    			try {
					getSession().getBasicRemote().sendText(LOGIN_SUCCESS_MESSAGE.toString());
				} catch (IOException e) {
					LOG.error(e.getMessage());
				}
    		}
		}
	}

	public Session getSession() {
		return session;
	}

	public void setSession(Session session) {
		this.session = session;
	}

	public String getOtherName() {
		return otherName;
	}

	public void setOtherName(String otherName) {
		this.otherName = otherName;
	}
}
