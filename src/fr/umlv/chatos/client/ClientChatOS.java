package fr.umlv.chatos.client;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.stream.Collectors;

import fr.umlv.chatos.context.Context;
import fr.umlv.chatos.context.Context.ContextAbstract;
import fr.umlv.chatos.utils.ClientReader;
import fr.umlv.chatos.utils.ReaderProcessor;
import fr.umlv.chatos.utils.Sender;
import fr.umlv.chatos.utils.data.ShortString;
import fr.umlv.chatos.utils.reader.http.HTTPReader;

/**
 * Represents the client side working along the COSP protocol.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class ClientChatOS {

	/**
	 * 
	 * Represents the HTTP context for private TCP connections.
	 *
	 */
	static private class ContextHTTP extends ContextAbstract {
		private final HTTPReader reader;
		private final short clientID;
		private final ClientChatOS clientChatOS;

		/**
		 * Creates a new ContextHTTP.
		 * 
		 * @param key          SelectionKey link to the context.
		 * @param clientID     Client id of the other client.
		 * @param clientChatOS The client.
		 */
		private ContextHTTP(SelectionKey key, short clientID, ClientChatOS clientChatOS) {
			super(key);
			this.clientID = clientID;
			this.clientChatOS = clientChatOS;
			this.reader = new HTTPReader(clientChatOS.folder, this);
		}

		@Override
		protected void processIn() {
			processInReader(reader);
		}

		@Override
		public void silentlyClose() {
			clientChatOS.pendingConnections.remove(clientID);
			super.silentlyClose();
		}

		@Override
		public void DoClose() {
			silentlyClose();
		}

	}

	/**
	 * 
	 * Represents the context for message and TCP setups communication.
	 *
	 */
	static private class ContextClient extends ContextAbstract {
		static private final Charset cs = StandardCharsets.UTF_8;
		private final ReaderProcessor readerProcessor;
		private final String login;
		private final ClientChatOS clientChatOS;

		/**
		 * Creates a new ContextClient.
		 * 
		 * @param key          SelectionKey link to the context.
		 * @param login        Client login.
		 * @param clientChatOS The client.
		 */
		private ContextClient(SelectionKey key, String login, ClientChatOS clientChatOS) {
			super(key);
			this.login = login;
			this.clientChatOS = clientChatOS;
			var clientReader = new ClientReader(clientChatOS);
			this.readerProcessor = new ReaderProcessor(() -> close());
			readerProcessor.put(-2, () -> clientReader.receiveTCPRefusal());
			readerProcessor.put(-1, () -> clientReader.receiveError(clientChatOS.setup));
			readerProcessor.put(0, () -> clientReader.receiveClientsListUpdate(clientChatOS.setup));
			readerProcessor.put(1, () -> clientReader.receiveClientDisconnection());
			readerProcessor.put(2, () -> clientReader.receiveBroadcastedMessage());
			readerProcessor.put(3, () -> clientReader.receiveMessageFrom());
			readerProcessor.put(4, () -> clientReader.receiveTCPDemand());
			readerProcessor.put(5, () -> clientReader.receiveTCPAcceptance());
			readerProcessor.put(6, () -> clientReader.receiveTCPValidation());
		}

		@Override
		protected void processIn() {
			processInProcessor(readerProcessor);
		}

		@Override
		public void doConnect() throws IOException {
			super.doConnect();
			Sender.sendString(this, ContextClient.cs.encode(login));
		}

		@Override
		public void silentlyClose() {
			System.out.println("Connexion avec le serveur perdue faites entrée pour terminer le client");
			clientChatOS.console.interrupt();
			super.silentlyClose();
			throw new UncheckedIOException(new IOException("Connexion closed"));
		}

		@Override
		public void DoClose() {
			silentlyClose();
		}

	}

	private final SocketChannel sc;
	private final Selector selector;
	private final InetSocketAddress serverAddress;
	private final String login;
	private final String folder;
	private final Thread console;
	private final ArrayBlockingQueue<String> commandQueue = new ArrayBlockingQueue<>(10);
	private ContextClient uniqueContext;
	private boolean setup = false;
	private final Map<Short, String> connectedUsers = new HashMap<>();
	private final Map<String, Short> connectedUsersLogin = new HashMap<>();
	private final Map<Short, SocketChannel> pendingConnections = new HashMap<>();
	private final Map<Short, String> pendingRequests = new HashMap<>();
	private Set<Short> pendingDemands = new HashSet<>();
	private Map<Short, Context> privateConnections = new HashMap<>();

	/**
	 * Creates a new ClientChatOs.
	 * 
	 * @param folder        Folder location for HTTP resources.
	 * @param login         Client login for server communication.
	 * @param serverAddress Server address.
	 * @throws IOException If an I/O error occurs when opening channel and selector.
	 */
	public ClientChatOS(String folder, String login, InetSocketAddress serverAddress) throws IOException {
		Objects.requireNonNull(folder, login);
		Objects.requireNonNull(serverAddress);
		this.serverAddress = serverAddress;
		this.login = login;
		this.folder = folder;
		this.sc = SocketChannel.open();
		this.selector = Selector.open();
		this.console = new Thread(this::consoleRun);
	}

	/**
	 * Connects pending socket to the server and send GET request along the private
	 * connection and prints message.
	 * 
	 * @param clientB Client which accepted the connection.
	 */
	public void connectionTCPAccepted(short clientB) {

		Sender.sendHTTPGET(connectContextHTTP(clientB), pendingRequests.get(clientB));
		System.out.println("Nouvelle demande de connexion TCP acceptée par " + connectedUsers.get(clientB));
	}

	/**
	 * Connects pending socket to the server.
	 * 
	 * @param clientA Client which has private connection with client.
	 */
	public void connectionTCPValidated(short clientA) {
		connectContextHTTP(clientA);
	}

	/**
	 * Removes the pending connection and prints message.
	 * 
	 * @param clientB Client which refused the private connection.
	 */
	public void connectionTCPRefused(short clientB) {
		pendingConnections.remove(clientB);
		System.out.println("Nouvelle demande de connexion TCP refusée par " + connectedUsers.get(clientB));
	}

	/**
	 * Closes context on existing login and prints error message.
	 */
	public void loginError() {
		System.out.println("ERREUR : Pseudonyme déjà existant veuillez vous reconnecter avec un autre pseudonyme");
		uniqueContext.close();
	}

	/**
	 * Prints error message on missing client connection.
	 * 
	 * @param clientB Client who has disconnected during packet transmission.
	 */
	public void disconnectedError(short clientB) {
		System.out.println("Message non reçu, " + connectedUsers.get(clientB) + " s'est deconnecté");
	}

	/**
	 * Adds new pending TCP demand for private connection.
	 * 
	 * @param clientA Client who asked for a private connection.
	 */
	public void TCPDemand(short clientA) {
		connectedUsers.computeIfPresent(clientA, (IDclientA, login) -> {
			pendingDemands.add(IDclientA);
			printDemandsList();
			return login;
		});
	}

	/**
	 * Registers all clients in list.
	 * 
	 * @param clientList Client list.
	 */
	public void updateClientList(List<ShortString> clientList) {
		for (var client : clientList) {
			if (!client.getString().equals(login)) {
				connectedUsersLogin.put(client.getString(), client.getShort());
				connectedUsers.put(client.getShort(), client.getString());
			}
		}
		printClientList();
		setup = true;
	}

	/**
	 * Registers new client.
	 * 
	 * @param clientData New client.
	 */
	public void registerNewClient(ShortString clientData) {
		if (!clientData.getString().equals(login)) {
			connectedUsersLogin.put(clientData.getString(), clientData.getShort());
			connectedUsers.put(clientData.getShort(), clientData.getString());
			printClientList();
		}
	}

	/**
	 * Removes disconnected client.
	 * 
	 * @param client Disconnected client.
	 */
	public void removeDisconnectedClient(short client) {
		connectedUsersLogin.remove(connectedUsers.remove(client));
		privateConnections.remove(client);
		pendingConnections.remove(client);
		pendingRequests.remove(client);
		pendingDemands.remove(client);
		printClientList();
		printDemandsList();
	}

	/**
	 * Prints the broadcasted message received.
	 * 
	 * @param msgData Message received.
	 */
	public void broadcastedMessage(ShortString msgData) {
		System.out.println("Reçu par tout le monde de la part de " + connectedUsers.get(msgData.getShort()) + " : "
				+ msgData.getString());
	}

	/**
	 * Prints the specific message received
	 * 
	 * @param msgData Message received.
	 */
	public void specificMessage(ShortString msgData) {
		System.out
				.println("Reçu de la part de " + connectedUsers.get(msgData.getShort()) + " : " + msgData.getString());
	}

	/**
	 * Launches client and connect it to server.
	 * 
	 * @throws IOException If an I/O error occurs
	 */
	public void launch() throws IOException {
		sc.configureBlocking(false);
		var key = sc.register(selector, SelectionKey.OP_CONNECT);
		uniqueContext = new ContextClient(key, login, this);
		key.attach(uniqueContext);
		sc.connect(serverAddress);
		console.start();
		while (!Thread.interrupted()) {
			try {
				selector.select(this::treatKey);
				synchronized (sc) {
					processCommands();
				}
			} catch (UncheckedIOException tunneled) {
				throw tunneled.getCause();
			}
		}
	}

	/**
	 * Client main method.
	 * 
	 * @param args Program arguments.
	 */
	public static void main(String[] args) {
		if (args.length != 4) {
			usage();
			return;
		}
		try {
			new ClientChatOS(args[0], args[1], new InetSocketAddress(args[2], Integer.parseInt(args[3]))).launch();
		} catch (IOException | CancelledKeyException e) {
			// Enregistrer dans log
		}

	}

	/**
	 * Prints client list.
	 */
	private void printClientList() {
		System.out.println("Liste des clients connectés : " + connectedUsers.values());
	}

	/**
	 * Prints private connections demands list.
	 */
	private void printDemandsList() {
		System.out.println("Liste des demandes de connexion privée : "
				+ pendingDemands.stream().map(id -> connectedUsers.get(id)).collect(Collectors.joining(", ")));
	}

	/**
	 * Prints private connections list.
	 */
	private void printPrivateConnections() {
		System.out.println("Liste des connexions privées : " + privateConnections.keySet().stream()
				.map(i -> connectedUsers.get(i)).collect(Collectors.joining(",")));
	}

	/**
	 * Connects pending connection HTTP to the server.
	 * 
	 * @param clientId Client who has a pending connection with client.
	 * @return The new ContextHTTP created.
	 */
	private Context connectContextHTTP(short clientId) {
		try {
			var socket = pendingConnections.remove(clientId);
			var context = connectHTTP(clientId, socket);
			privateConnections.put(clientId, context);
			return context;
		} catch (IOException ioe) {
			throw new UncheckedIOException(ioe);
		}
	}

	/**
	 * Console thread runnable for client requests.
	 */
	private void consoleRun() {
		try {
			try (var scan = new Scanner(System.in);) {
				while (!Thread.interrupted() && scan.hasNextLine()) {
					var msg = scan.nextLine();
					if (Thread.interrupted()) {
						break;
					}
					synchronized (sc) {
						sendCommand(msg);
					}
				}
			}
		} catch (InterruptedException e) {
			System.out.println("Exit");
		}
	}

	/**
	 * Sends command to the main Thread.
	 * 
	 * @param command Command to send.
	 * @throws InterruptedException If console threads is interrupted
	 */
	public void sendCommand(String command) throws InterruptedException {
		commandQueue.add(command);
		selector.wakeup();
	}

	/**
	 * Processes command from the queue.
	 */
	private void processCommands() {
		var msg = commandQueue.poll();
		if (msg != null) {
			if (msg.startsWith("$")) {
				printClientList();
			} else if (msg.startsWith("*")) {
				printPrivateConnections();
			} else if (msg.startsWith("#")) {
				printDemandsList();
			} else if (msg.startsWith("/")) {
				askPrivateTCPConnection(msg);
			} else if (msg.startsWith("@")) {
				sendMessageTo(msg);
			} else if (msg.startsWith("%")) {
				acceptRefuseTCPConnection(msg);
			} else {
				broadcastMessage(msg);
			}
		}
	}

	/**
	 * Checks if client login exists.
	 * 
	 * @param login Client login.
	 * @return The client ID if it exists, and -1 otherwise.
	 */
	private short checkLogin(String login) {
		var id = connectedUsersLogin.get(login);
		if (id == null) {
			System.out.println("Login invalide : " + login);
			return -1;
		}
		return id;
	}

	/**
	 * Checks and splits message in two slice.
	 * 
	 * @param msg     Message to split.
	 * @param nbSplit Number of split.
	 * @return The split message if it has a good syntax, and null otherwise.
	 */
	private String[] checkMessageSyntaxe(String msg, int nbSplit) {
		var splittedMsg = msg.split(" ", nbSplit);
		if (splittedMsg.length != 2) {
			System.out.println("Mauvaise syntaxe pour la commande : " + msg);
			return null;
		}
		return splittedMsg;
	}

	/**
	 * Creates a new SocketChannel for pending connections.
	 * 
	 * @param clientID Client ID for private connection.
	 * @return the socket port if no exception was thrown, and -1 otherwise
	 */
	private int createNewSocketChannel(short clientID) {
		int port;
		try {
			SocketChannel sc = SocketChannel.open().bind(new InetSocketAddress(0));
			sc.configureBlocking(false);
			port = ((InetSocketAddress) sc.getLocalAddress()).getPort();
			pendingConnections.put(clientID, sc);
		} catch (IOException e) {
			System.out.println("ERREUR : connexion TCP privée avortée");
			return -1;
		}
		return port;
	}

	/**
	 * Manages private TCP connection accepted or refused.
	 * 
	 * @param command Command received.
	 */
	private void acceptRefuseTCPConnection(String command) {
		var splittedMsg = checkMessageSyntaxe(command, -1);
		if (splittedMsg == null) {
			return;
		}
		var id = checkLogin(splittedMsg[0].substring(1));
		if (id == -1) {
			return;
		}
		if (pendingDemands.contains(id)) {
			if (splittedMsg[1].equals("0")) {
				TCPConnectionRefused(id);
			} else if (splittedMsg[1].equals("1")) {
				TCPConnectionAccepted(createNewSocketChannel(id), id);
			} else {
				System.out
						.println("Mauvaise syntaxe entrez \"%login 1\" si vous acceptez,\"%login 0\" si vous refusez");
			}
		} else {
			System.out.println(splittedMsg[0].substring(1) + " ne vous a pas fait de demande de connexion privée");
		}
	}

	/**
	 * Refuses the private connection.
	 * 
	 * @param clientA Client who sends the demand.
	 */
	private void TCPConnectionRefused(short clientA) {
		pendingDemands.remove(clientA);
		Sender.sendShort(uniqueContext, (byte) -1, clientA);
	}

	/**
	 * Accepts the private connection.
	 * 
	 * @param port    Socket port link with private connection.
	 * @param clientA Client who sends the demand.
	 */
	private void TCPConnectionAccepted(int port, short clientA) {
		if (port == -1) {
			return;
		}
		pendingDemands.remove(clientA);
		Sender.sendIntShort(uniqueContext, (byte) 3, port, clientA);
	}

	/**
	 * Sends specific message to a client.
	 * 
	 * @param msg The message with the recipient login.
	 */
	private void sendMessageTo(String msg) {
		var splittedMsg = checkMessageSyntaxe(msg, 2);
		if (splittedMsg == null) {
			return;
		}
		var id = checkLogin(splittedMsg[0].substring(1));
		if (id == -1) {
			return;
		}
		msg = splittedMsg[1];
		var encodedMsg = encodeMessage(msg);
		if (encodedMsg != null) {
			Sender.sendShortString(uniqueContext, (byte) (1), id, encodedMsg);
		}
	}

	/**
	 * Broadcast message to all clients.
	 * 
	 * @param msg The message.
	 */
	private void broadcastMessage(String msg) {
		var encodedMsg = encodeMessage(msg);
		if (encodedMsg != null) {
			Sender.sendString(uniqueContext, (byte) (0), ContextClient.cs.encode(msg));
		}
	}

	/**
	 * Encodes message and ensure it doesn't exceed max size.
	 * 
	 * @param msg The message to encode.
	 * @return The encoded message if it doesn't exceed max size, and null
	 *         otherwise.
	 */
	private ByteBuffer encodeMessage(String msg) {
		var encodedMsg = ContextClient.cs.encode(msg);
		if (encodedMsg.limit() >= Short.MAX_VALUE) {
			System.out.println("Message invalide il excède la taille maximale qui est de " + Short.MAX_VALUE);
			return null;
		}
		return encodedMsg;
	}

	/**
	 * Sends a private TCP connexion demand if the connexion doesn't already exists.
	 * 
	 * @param msg
	 */
	private void askPrivateTCPConnection(String msg) {
		var splittedMsg = checkMessageSyntaxe(msg, -1);
		if (splittedMsg == null) {
			return;
		}
		var clientID = checkLogin(splittedMsg[0].substring(1));
		if (clientID == -1) {
			return;
		}
		var path = splittedMsg[1];
		var context = privateConnections.get(clientID);
		if (context != null) {
			Sender.sendHTTPGET(context, path);
			return;
		}
		if (pendingConnections.containsKey(clientID)) {
			System.out.println("Demande de connexion à " + connectedUsers.get(clientID)
					+ " déjà réalisée, en attente d'une réponse");
			return;
		}
		pendingRequests.put(clientID, path);
		var port = createNewSocketChannel(clientID);
		Sender.sendIntShort(uniqueContext, (byte) (2), port, clientID);
	}

	/**
	 * Connects the socket as a ContextHTTP.
	 * 
	 * @param clientId Client ID.
	 * @param sc       The socket channel.
	 * @return The ContextHTTP created.
	 * @throws IOException If some other I/O error occurs
	 */
	private ContextHTTP connectHTTP(short clientId, SocketChannel sc) throws IOException {
		var key = sc.register(selector, SelectionKey.OP_CONNECT);
		var context = new ContextHTTP(key, clientId, this);
		key.attach(context);
		sc.connect(serverAddress);
		return context;
	}

	/**
	 * Treats the selection key.
	 * 
	 * @param key The SelectionKey.
	 */
	private void treatKey(SelectionKey key) {
		try {
			if (key.isValid() && key.isConnectable()) {
				((Context) key.attachment()).doConnect();
			}
			if (key.isValid() && key.isWritable()) {
				((Context) key.attachment()).doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				((Context) key.attachment()).doRead();
			}
		} catch (IOException ioe) {
			((ContextAbstract) key.attachment()).DoClose();
		}
	}

	/**
	 * Prints program arguments usage
	 */
	private static void usage() {
		System.out.println("Utilisation : ClientChatOS répertoire login nom-hôte port");
	}
}