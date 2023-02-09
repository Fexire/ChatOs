package fr.umlv.chatos.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import fr.umlv.chatos.context.Context;
import fr.umlv.chatos.context.Context.ContextAbstract;
import fr.umlv.chatos.utils.ReaderProcessor;
import fr.umlv.chatos.utils.Sender;
import fr.umlv.chatos.utils.ServerReader;
import fr.umlv.chatos.utils.data.IntShort;
import fr.umlv.chatos.utils.data.ShortString;
import fr.umlv.chatos.utils.reader.Reader;
import fr.umlv.chatos.utils.reader.StringReader;

/**
 * Represents a server ChatOS.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class ServerChatOS {

	private static abstract class ContextAbstractServer extends ContextAbstract {
		final ServerChatOS server;
		final short id;

		private ContextAbstractServer(ServerChatOS server, SelectionKey key, short id) {
			super(key);
			this.server = server;
			this.id = id;
		}

		/**
		 * 
		 * @return the id of this context.
		 */
		public short getId() {
			return id;
		}

	}

	private static class ContextTCP extends ContextAbstractServer {

		private ContextTCP(ServerChatOS server, short id) {
			super(server, null, id);
		}

		@Override
		protected void processIn() {
			processInTCP(server);
		}

		@Override
		public void DoClose() {
			server.disconnectedTCP(this);
		}
	}

	/**
	 * Represent the ChatOS context for delivering broadcast message, private
	 * message and manages tcp connection requests.
	 * 
	 * @author Benjamin JEDROCHA, Florian DURAND
	 *
	 */
	public static class ContextDefault extends ContextAbstractServer {

		private static final Charset UTF8 = Charset.forName("UTF8");

		private final ReaderProcessor readerProcessor;
		private String pseudonyme;

		private ContextDefault(ServerChatOS server, SelectionKey key, short id) {
			super(server, key, id);
			this.readerProcessor = new ReaderProcessor(() -> receivePseudo(), () -> silentlyClose());
			var serverReader = new ServerReader(server, this);
			readerProcessor.put(0, () -> serverReader.receiveBroadcastMessage());
			readerProcessor.put(1, () -> serverReader.receiveSpecificMessage());
			readerProcessor.put(2, () -> serverReader.receiveTCPAskMessage());
			readerProcessor.put(3, () -> serverReader.receiveTCPResponseMessage());
			readerProcessor.put(-1, () -> serverReader.receiveTCPResponseNOMessage());
		}

		@Override
		protected void processIn() {
			processInProcessor(readerProcessor);
		}

		private Optional<Reader<?>> receivePseudo() {
			if (server.mapId.containsKey(id)) { // si le pseudo a d�j� �t� setup
				return Optional.empty();
			}
			return Optional.of(new StringReader(s -> {
				if (server.pseudonymes.contains(s)) { // pseudo d�j� existant
					Sender.sendOpCode(this, (byte) -1);
					close();
					return;
				}
				server.pseudonymes.add(s);
				server.mapId.put(id, this);
				pseudonyme = s;
				Map<Short, ByteBuffer> idPseudoMap = server.mapId.entrySet().stream()
						.collect(Collectors.toMap(e -> e.getKey(), e -> UTF8.encode(e.getValue().pseudonyme)));
				Sender.sendClientList(this, idPseudoMap);
				server.newClient(new ShortString(id, pseudonyme));
			}));
		}

		@Override
		public void doConnect() throws IOException {
			throw new IllegalStateException("this method shouldn't be used");

		}

		@Override
		public void DoClose() {
			silentlyClose();
		}

		@Override
		public void silentlyClose() {
			server.disconnectedClient(id);
			super.silentlyClose();
		}

	}

	static private Logger logger = Logger.getLogger(ServerChatOS.class.getName());

	private final ServerSocketChannel serverSocketChannel;
	private final Selector selector;
	private final HashMap<Short, ContextDefault> mapId = new HashMap<>();
	private final HashMap<Short, HashSet<ContextTCP>> mapIdTCP = new HashMap<>();
	private final Set<String> pseudonymes = new HashSet<>();
	private short id = 0;

	/**
	 * Id client B, <Id Client A et son adresse + plus>
	 */
	private final HashMap<Short, HashMap<Short, InetSocketAddress>> privateTCPResponseWaiting = new HashMap<>();
	private final HashMap<InetSocketAddress, ContextTCP> privateTCPWaitingConnection = new HashMap<>();
	private final HashMap<ContextTCP, ContextTCP> privateTCP = new HashMap<>();

	/**
	 * Class constructor.
	 * 
	 * @param port Server port
	 * @throws IOException If some other I/O error occurs
	 */
	public ServerChatOS(int port) throws IOException {
		serverSocketChannel = ServerSocketChannel.open();
		serverSocketChannel.bind(new InetSocketAddress(port));
		selector = Selector.open();
	}

	/**
	 * Add a message to all connected clients queue.
	 *
	 * @param data - ShortString, short for the sender's id, and string for the
	 *             message.
	 */
	public void broadcast(ShortString data) { // pour chaque client
		Objects.requireNonNull(data);
		for (var key : selector.keys()) {
			if (key.isValid() && !key.isAcceptable()) {
				if (!privateTCP.containsKey(key.attachment())) {
					var client = (ContextDefault) key.attachment();
					if (data.getShort() != client.id) {
						var encoded_string = ContextDefault.UTF8.encode(data.getString());
						Sender.sendShortString(client, (byte) 2, data.getShort(), encoded_string);
					}
				}
			}
		}
	}

	/**
	 * Start running this server.
	 * 
	 * @throws IOException If some other I/O error occurs
	 */
	public void launch() throws IOException {
		serverSocketChannel.configureBlocking(false);
		serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
		while (!Thread.interrupted()) {
			printKeys(); // for debug
			System.out.println("Starting select");
			try {
				selector.select(this::treatKey);
			} catch (UncheckedIOException tunneled) {
				throw tunneled.getCause();
			}
			System.out.println("Select finished");
		}
	}

	/**
	 * Performs a TCP connection request.
	 * 
	 * @param intSh   - IntShort, short for the recipient's id, int for the sender's
	 *                port.
	 * @param context - sender context
	 */
	public void tcpAskMessage(IntShort intSh, ContextDefault context) {
		Objects.requireNonNull(intSh);
		Objects.requireNonNull(context);
		var receipId = intSh.getShort();
		var port = intSh.getInteger();
		if (ifIdDoesntExist(receipId, context)) {
			return;
		}
		var isaAForTCP = new InetSocketAddress(context.getInetAddress(), port);
		if (!privateTCPResponseWaiting.containsKey(receipId)) {
			privateTCPResponseWaiting.put(receipId, new HashMap<>());
		}
		privateTCPResponseWaiting.get(receipId).put(context.id, isaAForTCP);
		Sender.sendShort(mapId.get(receipId), (byte) 4, context.id);
	}

	/**
	 * Performs a TCP negative response.
	 * 
	 * @param idClientA - id of the customer who made the request
	 * @param context   - receipient's context
	 */
	public void tcpNegativResponseMessage(short idClientA, ContextDefault context) {
		Objects.requireNonNull(context);
		if (ifIdDoesntExist(idClientA, context)) {

			return;
		}
		if (!privateTCPResponseWaiting.containsKey(context.id)
				&& !privateTCPResponseWaiting.get(context.id).containsKey(idClientA)) {
			return;
		}
		Sender.sendShort(mapId.get(idClientA), (byte) -2, context.id);

	}

	/**
	 * Performs a TCP positive response.
	 * 
	 * @param intSh   - IntShort, int for receipient's port, short for id of the
	 *                customer who made the request
	 * @param context - receipient's context
	 */
	public void tcpResponseMessage(IntShort intSh, ContextDefault context) {
		Objects.requireNonNull(intSh);
		Objects.requireNonNull(context);
		var port = intSh.getInteger();
		var clientA = intSh.getShort();
		if (ifIdDoesntExist(clientA, context)) {
			return;
		}
		if (!privateTCPResponseWaiting.containsKey(context.id)
				&& !privateTCPResponseWaiting.get(context.id).containsKey(clientA)) {
			return;
		}
		var isaClientA = privateTCPResponseWaiting.get(context.id).get(clientA);
		var isaClientB = new InetSocketAddress(context.getInetAddress(), port);
		var contextTCPA = new ContextTCP(this, clientA);
		var contextTCPB = new ContextTCP(this, context.id);
		privateTCPWaitingConnection.put(isaClientA, contextTCPA);
		privateTCPWaitingConnection.put(isaClientB, contextTCPB);
		privateTCP.put(contextTCPA, contextTCPB);
		privateTCP.put(contextTCPB, contextTCPA);
		privateTCPResponseWaiting.get(context.id).remove(clientA);
		var hsA = new HashSet<ContextTCP>();
		hsA.add(contextTCPA);
		mapIdTCP.merge(clientA, hsA, (old, current) -> {
			old.addAll(current);
			return old;
		});
		var hsB = new HashSet<ContextTCP>();
		hsB.add(contextTCPB);
		mapIdTCP.merge(context.id, hsB, (old, current) -> {
			old.addAll(current);
			return old;
		});
		Sender.sendShort(mapId.get(clientA), (byte) 5, context.id);
		Sender.sendShort(context, (byte) 6, clientA);
	}

	/**
	 * Send a message to someone in particular.
	 * 
	 * @param data     - ShortString, short is receipient's id, string the message
	 *                 to send
	 * @param idSender - client ID who send the message
	 * @param context  - sender's context
	 */
	public void specificMessage(ShortString data, short idSender, Context context) {
		Objects.requireNonNull(data);
		Objects.requireNonNull(context);
		var idReceip = data.getShort();
		var message = data.getString();
		if (ifIdDoesntExist(idReceip, context)) {
			return;
		}
		var encodedString = ContextDefault.UTF8.encode(message);
		Sender.sendShortString(mapId.get(idReceip), (byte) 3, idSender, encodedString);
	}

	/**
	 * Transfers the bytebuffer to simulate a tcp connection.
	 * 
	 * @param bb      - ByteBuffer containing data to transfer
	 * @param context - context of incoming bytebuffer
	 */
	public void transferDataTCP(ByteBuffer bb, Context context) {
		Objects.requireNonNull(bb);
		Objects.requireNonNull(context);
		bb.flip();
		var data = ByteBuffer.allocate(bb.remaining()).put(bb);
		bb.compact();
		privateTCP.get(context).queueData(data.flip());
	}

	/**
	 * Runs the server
	 * 
	 * @param args - command line arguments
	 * @throws NumberFormatException - If the string does not contain a parsable
	 *                               integer.
	 * @throws IOException           - If some other I/O error occurs
	 */
	public static void main(String[] args) throws NumberFormatException, IOException {
		if (args.length != 1) {
			usage();
			return;
		}
		new ServerChatOS(Integer.parseInt(args[0])).launch();
	}

	private boolean ifIdDoesntExist(short id, Context context) {
		if (!mapId.containsKey(id)) {
			Sender.sendOpCode(context, (byte) -1);
			return true;
		}
		return false;
	}

	private void deletePrivateTCPResponseWaiting(short id) {
		if (privateTCPResponseWaiting.containsKey(id)) {
			privateTCPResponseWaiting.remove(id);
		}
	}

	private void disconnectedClient(short id) {
		var clientContext = mapId.get(id);
		if (clientContext != null) {
			var pseudo = clientContext.pseudonyme;
			mapId.remove(id);
			pseudonymes.remove(pseudo);
			var bb = ByteBuffer.allocate(Byte.BYTES + Short.BYTES);
			bb.put((byte) 1).putShort(id);
			mapId.forEach((k, context) -> {
				context.queueData(bb.flip());
			});
			if (mapIdTCP.containsKey(id)) {
				var set = mapIdTCP.get(id);
				set.forEach(element -> {
					disconnectedTCP(element);
				});
				mapIdTCP.remove(id);
			}
			deletePrivateTCPResponseWaiting(id);
		}
	}

	private void disconnectedTCP(ContextTCP context) {
		var context2 = privateTCP.remove(context);
		if (context2 != null) {
			context2.silentlyClose();
			privateTCP.remove(context2);
		}
		context.silentlyClose();
	}

	private void doAccept(SelectionKey key) throws IOException {
		ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
		SocketChannel sc = ssc.accept();
		if (sc == null) {
			return;
		}
		sc.configureBlocking(false);
		var k = sc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		var isa = new InetSocketAddress(sc.socket().getInetAddress(), sc.socket().getPort());
		if (privateTCPWaitingConnection.containsKey(isa)) {
			var contextTCP = privateTCPWaitingConnection.get(isa);
			contextTCP.setKey(k);
			k.attach(contextTCP);
			privateTCPWaitingConnection.remove(isa);
			return;
		}
		var context = new ContextDefault(this, k, id);
		k.attach(context);
		id++;
	}

	private void newClient(ShortString data) {
		Objects.requireNonNull(data);
		var pseudo = data.getString();
		var id = data.getShort();
		pseudonymes.add(pseudo);
		var bbPseudo = ContextDefault.UTF8.encode(pseudo);
		var bb = ByteBuffer.allocate(Byte.BYTES + Short.BYTES * 2 + bbPseudo.remaining());
		bb.put((byte) 0).putShort(id).putShort((short) bbPseudo.remaining()).put(bbPseudo);
		mapId.forEach((k, context) -> {
			if (k == id) {
				return;
			}
			context.queueData(bb.flip());
		});
	}

	private void silentlyClose(SelectionKey key) {
		Channel sc = (Channel) key.channel();
		try {
			sc.close();
		} catch (IOException e) {
			// ignore exception
		}
	}

	private void treatKey(SelectionKey key) {
		printSelectedKey(key); // for debug
		printHashMaps(); // For debug
		try {
			if (key.isValid() && key.isAcceptable()) {
				doAccept(key);
			}
		} catch (IOException ioe) {
			// lambda call in select requires to tunnel IOException
			throw new UncheckedIOException(ioe);
		}
		try {
			if (key.isValid() && key.isWritable()) {
				((Context) key.attachment()).doWrite();
			}
			if (key.isValid() && key.isReadable()) {
				((Context) key.attachment()).doRead();
			}
		} catch (IOException e) {
			logger.log(Level.INFO, "Connection closed with client due to IOException", e);
			((ContextAbstractServer) key.attachment()).DoClose();
			silentlyClose(key);
		}
	}

	private static void usage() {
		System.out.println("Usage : ServerChatOS port");
	}

	/***
	 * Theses methods are here to help understanding the behavior of the selector
	 ***/

	private void printKeys() {
		Set<SelectionKey> selectionKeySet = selector.keys();
		if (selectionKeySet.isEmpty()) {
			System.out.println("The selector contains no key : this should not happen!");
			return;
		}
		System.out.println("The selector contains:");
		for (SelectionKey key : selectionKeySet) {
			SelectableChannel channel = key.channel();
			if (channel instanceof ServerSocketChannel) {
				System.out.println("\tKey for ServerSocketChannel : " + interestOpsToString(key));
			} else {
				SocketChannel sc = (SocketChannel) channel;
				System.out.println("\tKey for Client " + remoteAddressToString(sc) + " : " + interestOpsToString(key));
			}
		}
	}

	private void printSelectedKey(SelectionKey key) {
		SelectableChannel channel = key.channel();
		if (channel instanceof ServerSocketChannel) {
			System.out.println("\tServerSocketChannel can perform : " + possibleActionsToString(key));
		} else {
			SocketChannel sc = (SocketChannel) channel;
			System.out.println(
					"\tClient " + remoteAddressToString(sc) + " can perform : " + possibleActionsToString(key));
		}
	}

	private String remoteAddressToString(SocketChannel sc) {
		try {
			return sc.getRemoteAddress().toString();
		} catch (IOException e) {
			return "???";
		}
	}

	private String interestOpsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		int interestOps = key.interestOps();
		ArrayList<String> list = new ArrayList<>();
		if ((interestOps & SelectionKey.OP_ACCEPT) != 0)
			list.add("OP_ACCEPT");
		if ((interestOps & SelectionKey.OP_READ) != 0)
			list.add("OP_READ");
		if ((interestOps & SelectionKey.OP_WRITE) != 0)
			list.add("OP_WRITE");
		return String.join("|", list);
	}

	private void printHashMaps() {
		var builder = new StringBuilder();
		builder.append("Private TCP Response Waiting :\n");
		privateTCPResponseWaiting.entrySet().forEach(entry -> {
			builder.append("\tTo id : " + entry.getKey() + ", ");
			entry.getValue().entrySet().forEach(entry2 -> {
				builder.append("From id : " + entry2.getKey() + " " + entry2.getValue());
			});
			builder.append('\n');
		});
		builder.append("\nPrivate TCP Waiting Connection : \n");
		privateTCPWaitingConnection.entrySet().forEach(entry -> {
			builder.append('\t').append(entry.getKey()).append('\n');
		});
		builder.append("\nPrivate TCP : \n");
		privateTCP.entrySet().forEach(entry -> {
			builder.append('\t').append(entry.getKey().id + " " + entry.getValue().id).append('\n');
		});
		System.out.println(builder.toString());
	}

	private String possibleActionsToString(SelectionKey key) {
		if (!key.isValid()) {
			return "CANCELLED";
		}
		ArrayList<String> list = new ArrayList<>();
		if (key.isAcceptable())
			list.add("ACCEPT");
		if (key.isReadable())
			list.add("READ");
		if (key.isWritable())
			list.add("WRITE");
		return String.join(" and ", list);
	}
}
