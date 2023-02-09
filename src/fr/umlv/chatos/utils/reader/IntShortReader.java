package fr.umlv.chatos.utils.reader;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

import fr.umlv.chatos.utils.data.Data;
import fr.umlv.chatos.utils.data.IntShort;

/**
 * Represents a reader which can read an int then a short.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class IntShortReader extends AbstractReader<IntShort> {

	private enum State {
		DONE, READING_PORT, READING_ID_RECIPIENT, ERROR
	};

	private final IntReader intReader = new IntReader();
	private final ShortReader shortReader = new ShortReader();

	private State state = State.READING_PORT;
	private short id;
	private int port;

	/**
	 * Class constructor.
	 * 
	 * @param function Function to process after read
	 */
	public IntShortReader(Consumer<IntShort> function) {
		super(function);
	}

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		Objects.requireNonNull(bb);
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		while (state != State.DONE) {
			if (state == State.READING_PORT) {
				var result = intReader.process(bb);
				if (result != ProcessStatus.DONE) {
					return result;
				}
				port = intReader.get().getData();
				state = State.READING_ID_RECIPIENT;
			} else {
				var result = shortReader.process(bb);
				if (result != ProcessStatus.DONE) {
					return result;
				}
				id = shortReader.get().getData();
				state = State.DONE;
			}
		}
		return ProcessStatus.DONE;
	}

	@Override
	public Data<IntShort> get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return newData(new IntShort(port, id));
	}

	@Override
	public void reset() {
		state = State.READING_PORT;
		intReader.reset();
		shortReader.reset();
	}

}
