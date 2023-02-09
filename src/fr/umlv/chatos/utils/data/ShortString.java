package fr.umlv.chatos.utils.data;

import java.util.Objects;

/**
 * This class allows to store a short and a string.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class ShortString {
	private final short sh;
	private final String string;

	/**
	 * Class constructor.
	 * 
	 * @param sh     short value
	 * @param string string value
	 */
	public ShortString(short sh, String string) {
		Objects.requireNonNull(string);
		this.sh = sh;
		this.string = string;
	}

	/**
	 * 
	 * @return the short value.
	 */
	public short getShort() {
		return sh;
	}

	/**
	 * 
	 * @return the string value.
	 */
	public String getString() {
		return string;
	}

	@Override
	public String toString() {
		return sh + " " + string;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof ShortString)) {
			return false;
		}
		ShortString is = (ShortString) obj;
		return is.sh == sh && is.string.equals(string);
	}
}
