package fr.umlv.chatos.utils.reader;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

import fr.umlv.chatos.utils.data.Data;

/**
 * Represents a int reader.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class IntReader extends AbstractReader<Integer> {

	private enum State {
		DONE, WAITING, ERROR
	};

	private State state = State.WAITING;
	private final ByteBuffer internalbb = ByteBuffer.allocate(Integer.BYTES);

	/**
	 * Class constructor.
	 *  
	 * @param function Function to process after read
	 */
	public IntReader(Consumer<Integer> function) {
		super(function);
	}

	/**
	 * Class constructor without function.
	 */
	public IntReader() {
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
			if (bb.remaining() <= internalbb.remaining()) {
				internalbb.put(bb);
			} else {
				var oldLimit = bb.limit();
				bb.limit(internalbb.remaining());
				internalbb.put(bb);
				bb.limit(oldLimit);
			}
		} finally {
			bb.compact();
		}
		if (internalbb.hasRemaining()) {
			return ProcessStatus.REFILL;
		}
		state = State.DONE;
		internalbb.flip();
		return ProcessStatus.DONE;
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
