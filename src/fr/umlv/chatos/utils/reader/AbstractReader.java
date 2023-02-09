package fr.umlv.chatos.utils.reader;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

import fr.umlv.chatos.utils.data.Data;
import fr.umlv.chatos.utils.data.DataProcessable;
import fr.umlv.chatos.utils.data.DataRaw;

/**
 * Reader code factorization
 * 
 * @see Reader<T>
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 * @param <T> Data type
 */
public abstract class AbstractReader<T> implements Reader<T> {

	private final Consumer<T> function;

	/**
	 * Class constructor.
	 * 
	 * @param function Function to process after data read
	 */
	public AbstractReader(Consumer<T> function) {
		Objects.requireNonNull(function);
		this.function = function;
	}

	/**
	 * 
	 * Class constructor.
	 * 
	 */
	public AbstractReader() {
		function = null;
	}

	@Override
	public abstract ProcessStatus process(ByteBuffer bb);

	@Override
	public abstract Data<T> get();

	@Override
	public abstract void reset();

	/**
	 * 
	 * @param data Data
	 * @return a new DataProcessable
	 */
	public Data<T> newData(T data) {
		Objects.requireNonNull(data);
		if (function == null) {
			return new DataRaw<T>(data);
		}
		return new DataProcessable<T>(data, function);
	}

	/**
	 * 
	 * @return a new DataProcessable
	 */
	public Data<T> newData() {
		return new DataProcessable<T>(function);
	}

}
