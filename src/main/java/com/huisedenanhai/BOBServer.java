/*
 * Created by huisedenanhai on 2018/06/04
 */

package com.huisedenanhai;

import com.huisedenanhai.exception.BadServerSocketException;
import com.huisedenanhai.exception.NotLegalNameException;
import com.huisedenanhai.exception.TooManyConnectionException;
import org.json.JSONObject;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Time;
import java.util.*;

public class BOBServer {

    private ServerSocket serverSocket;

    private int nextId = 0;

    private final ArrayList<ConnectedClient> connectedClients = new ArrayList<>();

    private final ArrayList<JSONSerializable> actionPool = new ArrayList<>();

    private final ServerModel serverModel = new ServerModel(System.currentTimeMillis());

    private final BroadcastTask broadcastTask = new BroadcastTask();

    /**
     * Get current syncronization message
     *
     * @return
     */
    public SyncronizationMessage getCurrentSyncronizationMessage(Integer generateID) {
        synchronized (this.connectedClients) {
            for (ConnectedClient connectedClient: this.connectedClients) {
                connectedClient.pushAllActionsToPool();
            }
        }
        Map<Integer, Map<Long, String>> sampledActions = new HashMap<>();
        synchronized (this.actionPool) {
            for (JSONSerializable obj: this.actionPool) {
                Action action = (Action) obj;
                if (!sampledActions.containsKey(action.getID())) {
                    sampledActions.put(action.getID(), new HashMap<>());
                }
                sampledActions.get(action.getID()).put(action.getTime(), action.getAction());
            }
            this.actionPool.clear();
        }
        synchronized (this.serverModel) {
            serverModel.acceptAcceleration(new ServerToModel(sampledActions, true));
            Status status;
            if (generateID != null) {
                status = this.serverModel.addClient(generateID, System.currentTimeMillis());
            } else {
                status = serverModel.getStatusFromCurrent(System.currentTimeMillis());
            }
            return new SyncronizationMessage(status);
        }
    }

    /**
     * Check if a name has been registered
     *
     * @param name the name to be checked, if the name is null, NotLegalNameException will be thrown
     * @return true if the name has already been registered, else return false
     */
    public boolean isRegisteredName(String name) {
        if (name == null) {
            throw new NotLegalNameException();
        }
        synchronized (this.connectedClients) {
            for (ConnectedClient client : connectedClients) {
                String clientName = client.getName();
                if (name.equals(clientName)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Push all actions into the action pool
     * Action pool stores all the actions that will be send to the model for calculation
     * It should be noticed that this method will do nothing on the model
     *
     * @param actions actions to be added
     */
    public void pushAllActionsToPool(Collection<JSONSerializable> actions) {
        synchronized (this.actionPool) {
            actionPool.addAll(actions);
//            System.out.println("Current action count: " + actionPool.size());
        }
    }

    /**
     * Add a connected client
     *
     * @param client the connected client
     * @throws TooManyConnectionException too many clients what to connect to the server
     */
    public void addConnectedClient(ConnectedClient client) throws TooManyConnectionException {
        synchronized (this.connectedClients) {
            if (connectedClients.size() >= Config.MAX_CONNECTION) {
                throw new TooManyConnectionException("Too many connects", null);
            }
            connectedClients.add(client);
            System.out.println("Get connection from: " + client.getInetAddress());
            System.out.println("current connection count: " + connectedClients.size());
        }
    }

    /**
     * Remove the client from connected clients list
     *
     * @param client the connected client
     */
    public void removeConnectedClient(ConnectedClient client) {
        synchronized (this.serverModel) {
            this.serverModel.removeClient(client.getID());
        }
        synchronized (this.connectedClients) {
            connectedClients.remove(client);
            System.out.println("Remove connection of: " + client.getInetAddress());
            System.out.println("current connection count: " + connectedClients.size());
        }
    }

    public int getConnectionCount() {
        synchronized (this.connectedClients) {
            return connectedClients.size();
        }
    }

    private int generateId() {
        return nextId++;
    }

    class BroadcastTask extends TimerTask {
        @Override
        public void run() {
            JSONObject message = new JSONObject("{method:sync}");
            message.put("sync-msg", getCurrentSyncronizationMessage(null).encodeJSON());
            synchronized (connectedClients) {
                for (ConnectedClient connectedClient: connectedClients) {
                    try {
                        connectedClient.sendJsonResponse(message);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Start the server
     * Waiting for connections from clients
     */
    public void start() {
        Timer timer = new Timer();
        timer.schedule(broadcastTask, 0, 100);
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                if (getConnectionCount() < Config.MAX_CONNECTION) {
                    int generatedID = generateId();
                    new ConnectedClient(generatedID, clientSocket, this);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public BOBServer() {
        try {
            serverSocket = new ServerSocket(Config.SERVER_PORT);
        } catch (IOException ex) {
            throw new BadServerSocketException("Can't open server socket.", ex);
        }
    }

    public static void main(String[] args) {
        BOBServer server = new BOBServer();
        server.start();
    }
}
