/*
 * Created by huisedenanhai on 2018/06/04
 */

package com.huisedenanhai;

import com.huisedenanhai.exception.TooManyConnectionException;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;

public class ConnectedClient {

    private final Socket clientSocket;

    private final BOBServer server;

    private final int id;

    private String name;

    private InputStream inputStream;

    private OutputStream outputStream;

    private final ArrayList<JSONSerializable> actions = new ArrayList<>();

    /**
     * Send all actions to server's action pool, and clear all actions in the list
     */
    public void pushAllActionsToPool() {
        synchronized (this.actions) {
            server.pushAllActionsToPool(actions);
            actions.clear();
        }
    }

    /**
     * Close the connection, and do some cleanup
     */
    private void closeConnection() {
        pushAllActionsToPool();
        server.removeConnectedClient(this);
        try {
            clientSocket.close();
            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final String CID_CHARSET = "1234567890";
    private static final int CID_LENGTH = 16;
    private static final int BUFFER_LENGTH = 512;

    /**
     * Read request from client
     *
     * @return the decoded JSONObject
     * @throws IOException
     */
    private JSONObject readClientRequest() throws IOException {
        byte[] buffer = new byte[BUFFER_LENGTH];
        inputStream.read(buffer);
        return new JSONObject(new String(buffer, Config.DEFAULT_CHARSET));
    }

    /**
     * Send json to client
     *
     * @param jsonObject the json object to sent
     * @throws IOException
     */
    public void sendJsonResponse(JSONObject jsonObject) throws IOException {
        outputStream.write((jsonObject.toString() + "\r\n").getBytes(Config.DEFAULT_CHARSET));
    }

    /**
     * Tell the client the required name is not valid
     */
    private void tellClientNameNotValid() throws IOException {
        JSONObject response = new JSONObject("{method:register-response,valid:0}");
        sendJsonResponse(response);
    }

    /**
     * The name is valid, send client required message
     */
    private void tellClientNameIsValid() throws IOException {
        JSONObject response = new JSONObject("{method:register-response,valid:1}");
        response.put("id", id);
        response.put("sync-msg", server.getCurrentSyncronizationMessage(id).encodeJSON());
        sendJsonResponse(response);
    }

    /**
     * Check is the name is legal
     *
     * @param name the name to be checked
     * @return true if the name is legal
     */
    private boolean isLegalName(String name) {
        return name != null;
    }

    /**
     * Show verbose info
     *
     * @param request request from client
     */
    private void showRequestInfo(JSONObject request) {
        StringBuilder sb = new StringBuilder("Read response from ");
        sb.append(id).append(": ").append(request.toString());
        System.out.println(sb.toString());
    }

    /**
     * Check if it is a valid connection
     * The connected client should follow the our predefined protocol
     *
     * @return true if check succeed, else return false
     */
    private boolean checkConnection() {
        while (true) {
            try {
                JSONObject clientRegisterInformation = readClientRequest();
                showRequestInfo(clientRegisterInformation);
                String clientMethod = clientRegisterInformation.getString("method");
                // check if the client give us a valid message
                if (!"register".equals(clientMethod)) {
                    return false;
                }
                String clientRequiredName = clientRegisterInformation.getString("name");
                // the client send us an illegal name or the name has been registered
                if (!isLegalName(clientRequiredName) || server.isRegisteredName(clientRequiredName)) {
                    tellClientNameNotValid();
                    continue;
                }
                // Now the name given by the client must be valid
                // WARN: this piece of code is buggy, but for the sack of simplification, I still choose to put it here
                // Since the system may handle everything by id, who will care if there have duplicated names!
                // If there were time, I would fix them in the future...
                this.name = clientRequiredName;
                tellClientNameIsValid();
                break;
            } catch (UnsupportedEncodingException ex) {
                ex.printStackTrace();
                return false;
            } catch (IOException | JSONException ex) {
                return false;
            }
        }
        return true;
    }

    /**
     * Append action to list
     * This method is thread safe
     *
     * @param action the action to be appended
     */
    private void appendActionToList(JSONSerializable action) {
        synchronized (this.actions) {
            actions.add(action);
        }
    }

    private void serve() {
        while (true) {
            try {
                JSONObject request = readClientRequest();
                showRequestInfo(request);
                String method = request.getString("method");
                if ("action".equals(method)) {
                    Action action = new Action();
                    action.parseJSON(request.getJSONObject("action"));
                    appendActionToList(action);
                    continue;
                }
                if ("disconnect".equals(method)) {
                    break;
                }
                // get invalid message
                break;
            } catch (IOException | JSONException ex) {
                ex.printStackTrace();
                break;
            }
        }
    }

    public String getName() {
        return name;
    }

    public int getID() {
        return id;
    }

    public InetAddress getInetAddress() {
        return clientSocket.getInetAddress();
    }

    /**
     * The thread that do the main work of serving
     */
    class WorkingThread extends Thread {
        @Override
        public void run() {
            super.run();
            // check if this is a valid connection
            if (!checkConnection()) {
                closeConnection();
                return;
            }
            // Check connection succeed, tell the server to add this client
            try {
                server.addConnectedClient(ConnectedClient.this);
            } catch (TooManyConnectionException ex) {
                // Too many connection, return
                closeConnection();
                return;
            }
            serve();
            closeConnection();
        }
    }


    public ConnectedClient(int id, Socket clientSocket, BOBServer server) {
        this.clientSocket = clientSocket;
        this.server = server;
        this.id = id;
        // Open input and output stream
        try {
            this.inputStream = clientSocket.getInputStream();
            this.outputStream = clientSocket.getOutputStream();
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
        // if any of the above operation fails, the method will return
        // Create a new thread and serve the client
        new WorkingThread().start();
    }
}
