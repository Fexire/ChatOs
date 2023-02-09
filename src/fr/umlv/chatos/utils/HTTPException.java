package fr.umlv.chatos.utils;

import java.io.IOException;
import java.util.Objects;

/**
 * Class for exceptions with HTTP.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class HTTPException extends IOException {

	private static final long serialVersionUID = -1810727803680020453L;

	/**
	 * Class contructor.
	 */
	public HTTPException() {
		super();
	}

	/**
	 * Create a HTTP exception with the given argument.
	 * 
	 * @param s Exception message
	 */
	public HTTPException(String s) {
		super(s);
	}

	/**
	 * Throw an exception with the message given if boolean if true.
	 * 
	 * @param b      condition
	 * @param string Exception message
	 * @throws HTTPException If condition b is false
	 */
	public static void ensure(boolean b, String string) throws HTTPException {
		Objects.requireNonNull(string);
		if (!b)
			throw new HTTPException(string);
	}
}