package fr.umlv.chatos.utils.reader;

import java.nio.ByteBuffer;

import fr.umlv.chatos.utils.data.Data;

/**
 * Represents a reader. After process on a bytebuffer create a Data.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 * @param <T> Data type
 */
public interface Reader<T> {

	/**
	 * Represents the status of process.
	 * 
	 * @author Benjamin JEDROCHA, Florian DURAND
	 * 
	 * 
	 */
	public enum ProcessStatus {
		/**
		 * when process encountered an error
		 */
		ERROR,
		/**
		 * when process finished
		 */
		DONE,
		/**
		 * when the bytebuffer wasn't enough fill for terminate process
		 */
		REFILL;
	}

	/**
	 * 
	 * @param bb - must be in write mode.
	 * @return the process status.
	 */
	ProcessStatus process(ByteBuffer bb);

	/**
	 * 
	 * @return the data
	 */
	Data<T> get();

	/**
	 * Reset the reader
	 */
	void reset();
}
