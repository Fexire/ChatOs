package fr.umlv.chatos.utils.reader;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

import fr.umlv.chatos.utils.data.Data;
import fr.umlv.chatos.utils.data.ShortString;

/**
 * Represents a reader which can read a short then a string.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class ShortStringReader extends AbstractReader<ShortString> {

	private enum State {
		DONE, READING_INT, READING_STRING, ERROR
	};

	private State state = State.READING_INT;
	private final ShortReader shortReader = new ShortReader();
	private final StringReader stringReader = new StringReader();

	/**
	 * Class constructor.
	 * 
	 * @param function Function to process after read
	 */
	public ShortStringReader(Consumer<ShortString> function) {
		super(function);
	}

	/**
	 * Class constructor.
	 * 
	 */
	public ShortStringReader() {
		super();
	}

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		Objects.requireNonNull(bb);
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		if (state == State.READING_INT) {
			switch (shortReader.process(bb)) {
			case DONE: {
				state = State.READING_STRING;
				break;
			}
			case ERROR: {
				state = State.ERROR;
				return ProcessStatus.ERROR;
			}
			case REFILL: {
				return ProcessStatus.REFILL;
			}
			default: {
				return ProcessStatus.ERROR;
			}
			}
		}
		if (state == State.READING_STRING) {
			switch (stringReader.process(bb)) {
			case DONE: {
				break;
			}
			case ERROR: {
				state = State.ERROR;
				return ProcessStatus.ERROR;
			}
			case REFILL: {
				return ProcessStatus.REFILL;
			}
			default: {
				return ProcessStatus.ERROR;
			}
			}
		}
		state = State.DONE;
		return ProcessStatus.DONE;
	}

	@Override
	public Data<ShortString> get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return newData(new ShortString(shortReader.get().getData(), stringReader.get().getData()));
	}

	@Override
	public void reset() {
		state = State.READING_INT;
		shortReader.reset();
		stringReader.reset();
	}
}
