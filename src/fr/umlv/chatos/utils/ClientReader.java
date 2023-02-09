package fr.umlv.chatos.utils;

import fr.umlv.chatos.client.ClientChatOS;
import fr.umlv.chatos.utils.reader.ClientListReader;
import fr.umlv.chatos.utils.reader.Reader;
import fr.umlv.chatos.utils.reader.ShortReader;
import fr.umlv.chatos.utils.reader.ShortStringReader;
import fr.umlv.chatos.utils.reader.VoidReader;

/**
 * Represents the client ChatOS reader.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class ClientReader {
	private final ClientChatOS clientChatOS;

	/**
	 * Class constructor with the client ChatOS reader.
	 * 
	 * @param clientChatOS Client object
	 */
	public ClientReader(ClientChatOS clientChatOS) {
		this.clientChatOS = clientChatOS;
	}

	/**
	 * 
	 * @return the reader to process a positive response to a tcp demand.
	 */
	public Reader<?> receiveTCPAcceptance() {
		return new ShortReader(clientB -> {
			clientChatOS.connectionTCPAccepted(clientB);
		});
	}

	/**
	 * 
	 * @return the reader to process a positive response to a tcp request.
	 */
	public Reader<?> receiveTCPValidation() {
		return new ShortReader(clientA -> {
			clientChatOS.connectionTCPValidated(clientA);
		});
	}

	/**
	 * 
	 * @return the reader to process a negative response to a tcp request.
	 */
	public Reader<?> receiveTCPRefusal() {
		return new ShortReader(clientB -> {
			clientChatOS.connectionTCPRefused(clientB);
		});
	}

	/**
	 * 
	 * @param setup If connection is already setup
	 * @return the reader to process when receive an error.
	 */
	public Reader<?> receiveError(boolean setup) {
		if (!setup) {
			return new VoidReader(voidData -> {
				clientChatOS.loginError();
			});
		} else {
			return new ShortReader(clientB -> clientChatOS.disconnectedError(clientB));
		}
	}

	/**
	 * 
	 * @return the reader to process when get a tcp request.
	 */
	public Reader<?> receiveTCPDemand() {
		return new ShortReader(clientA -> {
			clientChatOS.TCPDemand(clientA);
		});
	}

	/**
	 * 
	 * @param setup If connection is already setup
	 * @return the reader to process when get a list of clients.
	 */
	public Reader<?> receiveClientsListUpdate(boolean setup) {
		if (!setup) {
			return new ClientListReader(clientList -> {
				clientChatOS.updateClientList(clientList);
			});
		} else {
			return new ShortStringReader(client -> {
				clientChatOS.registerNewClient(client);
			});
		}
	}

	/**
	 * 
	 * @return the reader to process when get a disconnected client from server.
	 */
	public Reader<?> receiveClientDisconnection() {
		return new ShortReader(client -> {
			clientChatOS.removeDisconnectedClient(client);
		});
	}

	/**
	 * 
	 * @return the reader to process when get a broadcast message.
	 */
	public Reader<?> receiveBroadcastedMessage() {
		return new ShortStringReader(client -> {
			clientChatOS.broadcastedMessage(client);
		});
	}

	/**
	 * 
	 * @return the reader to process when get a private message.
	 */
	public Reader<?> receiveMessageFrom() {
		return new ShortStringReader(client -> {
			clientChatOS.specificMessage(client);
		});
	}

}
