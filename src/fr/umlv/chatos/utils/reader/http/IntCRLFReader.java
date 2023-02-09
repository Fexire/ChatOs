package fr.umlv.chatos.utils.reader.http;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

import fr.umlv.chatos.utils.data.Data;
import fr.umlv.chatos.utils.reader.AbstractReader;

/**
 * Represents a IntCRLF reader.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class IntCRLFReader extends AbstractReader<Integer> {

	private enum State {
		DONE, WAITING, ERROR
	};

	private State state = State.WAITING;
	private final ByteBuffer internalbb = ByteBuffer.allocate(Integer.BYTES + 2);
	byte previousCharacter = ' ';

	/**
	 * Class constructor.
	 * 
	 * @param function - Function to process after read
	 */
	public IntCRLFReader(Consumer<Integer> function) {
		super(function);
	}

	/**
	 * Class constructor.
	 */
	public IntCRLFReader() {
		super();
	}

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		Objects.requireNonNull(bb);
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		bb.flip();
		try {
			while (bb.hasRemaining()) {
				byte newCharacter = bb.get();
				if (previousCharacter == '\r' && newCharacter == '\n') {
					internalbb.flip();
					internalbb.limit(internalbb.limit() - 1);
					state = State.DONE;
					break;
				}
				previousCharacter = newCharacter;
				internalbb.put(newCharacter);
			}
		} finally {
			bb.compact();
		}
		if (state == State.DONE) {
			return ProcessStatus.DONE;
		}
		return ProcessStatus.REFILL;
	}

	@Override
	public Data<Integer> get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return newData(internalbb.getInt());
	}

	@Override
	public void reset() {
		state = State.WAITING;
		internalbb.clear();
	}

}
