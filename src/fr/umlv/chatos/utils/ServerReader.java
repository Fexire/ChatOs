package fr.umlv.chatos.utils;

import java.util.Objects;

import fr.umlv.chatos.server.ServerChatOS;
import fr.umlv.chatos.server.ServerChatOS.ContextDefault;
import fr.umlv.chatos.utils.data.ShortString;
import fr.umlv.chatos.utils.reader.IntShortReader;
import fr.umlv.chatos.utils.reader.Reader;
import fr.umlv.chatos.utils.reader.ShortReader;
import fr.umlv.chatos.utils.reader.ShortStringReader;
import fr.umlv.chatos.utils.reader.StringReader;

/**
 * Represents a server reader.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class ServerReader {

	private final ServerChatOS server;
	private final ContextDefault context;
	private final short id;

	/**
	 * Class constructor.
	 * 
	 * @param server  Server object
	 * @param context client context
	 */
	public ServerReader(ServerChatOS server, ContextDefault context) {
		Objects.requireNonNull(server);
		this.server = server;
		this.context = context;
		id = context.getId();
	}

	/**
	 * 
	 * @return a reader which will be executed when the server receives a specific
	 *         message.
	 */
	public Reader<?> receiveSpecificMessage() {
		return new ShortStringReader(intString -> {
			server.specificMessage(intString, id, context);
		});
	}

	/**
	 * 
	 * @return a reader which will be executed when the server receives a broadcast
	 *         message.
	 */
	public Reader<?> receiveBroadcastMessage() {
		return new StringReader(s -> {
			server.broadcast(new ShortString(id, s));
		});
	}

	/**
	 * 
	 * @return a reader which will be executed when the server receives a TCP
	 *         connection request.
	 */
	public Reader<?> receiveTCPAskMessage() {
		return new IntShortReader(intInt -> {
			server.tcpAskMessage(intInt, context);
		});
	}

	/**
	 * 
	 * @return a reader which will be executed when the server receives a TCP
	 *         positive response.
	 */
	public Reader<?> receiveTCPResponseMessage() {
		return new IntShortReader(intInt -> {
			server.tcpResponseMessage(intInt, context);
		});
	}

	/**
	 * 
	 * @return a reader which will be executed when the server receives a TCP
	 *         negative response.
	 */
	public Reader<?> receiveTCPResponseNOMessage() {
		return new ShortReader(idClientA -> {
			server.tcpNegativResponseMessage(idClientA, context);
		});
	}
}
