package fr.umlv.chatos.utils.data;

/**
 * Store any type of data.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 * @param <T> type of data.
 */
public interface Data<T> {

	/**
	 * This method create the T data.
	 */
	abstract void process();

	/**
	 * 
	 * @return T data after using process.
	 */
	abstract T getData();

}
