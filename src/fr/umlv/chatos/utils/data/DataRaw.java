package fr.umlv.chatos.utils.data;

import java.util.Objects;

/**
 * Data without process.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 * @param <T> type of data.
 */
public class DataRaw<T> implements Data<T> {
	private final T data;

	/**
	 * Class contructor.
	 * 
	 * @param data Data
	 */
	public DataRaw(T data) {
		Objects.requireNonNull(data);
		this.data = data;
	}

	@Override
	public void process() {
		throw new IllegalStateException("Raw data can't be processed");
	}

	@Override
	public T getData() {
		return data;
	}

}
