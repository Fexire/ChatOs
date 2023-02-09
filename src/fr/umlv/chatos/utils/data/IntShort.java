package fr.umlv.chatos.utils.data;

/**
 * This class allows to store an integer and a short and to read them.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class IntShort {
	private final short id;
	private final int port;

	/**
	 * Class constructor.
	 * 
	 * @param port integer port
	 * @param id   short ID
	 */
	public IntShort(int port, short id) {
		checkParams(port, id);
		this.id = id;
		this.port = port;
	}

	/**
	 * 
	 * @return the short.
	 */
	public short getShort() {
		return id;
	}

	/**
	 * 
	 * @return the integer.
	 */
	public int getInteger() {
		return port;
	}

	private void checkParams(int port, short id) {
		if (id < 0) {
			throw new IllegalArgumentException("id must be positiv, current : " + id);
		}
		if (port < 0 || port > 65_535) {
			throw new IllegalArgumentException("port must be between 0 and 65_535 (both included), current : " + port);
		}
	}
}
