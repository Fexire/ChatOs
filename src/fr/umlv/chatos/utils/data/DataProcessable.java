package fr.umlv.chatos.utils.data;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Store any type of data and can perform a consumer on it.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 * @param <T> type of data
 */
public class DataProcessable<T> implements Data<T> {

	private final T data;
	private final Consumer<T> function;

	/**
	 * Class constructor
	 * 
	 * @param data     Data
	 * @param function Function to process on data
	 */
	public DataProcessable(T data, Consumer<T> function) {
		Objects.requireNonNull(data);
		Objects.requireNonNull(function);
		this.data = data;
		this.function = function;
	}

	/**
	 * Class constructor. For Void Data.
	 * 
	 * @param function Function to process on data
	 */
	public DataProcessable(Consumer<T> function) {
		Objects.requireNonNull(function);
		this.function = function;
		data = null;
	}

	@Override
	public void process() {

		function.accept(data);
	}

	@Override
	public T getData() {
		return data;
	}

}
