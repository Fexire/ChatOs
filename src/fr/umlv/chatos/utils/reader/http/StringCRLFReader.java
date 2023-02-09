package fr.umlv.chatos.utils.reader.http;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.Consumer;

import fr.umlv.chatos.utils.data.Data;
import fr.umlv.chatos.utils.reader.AbstractReader;

/**
 * Represents a string CRLFR reader.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class StringCRLFReader extends AbstractReader<String> {
	private enum State {
		DONE, WAITING, ERROR
	};

	private State state = State.WAITING;
	private final ByteBuffer internalbb;
	private final Charset cs;
	byte previousCharacter = ' ';
	private final int size;

	/**
	 * Class contructor.
	 * 
	 * @param function - Function to process after read
	 * @param size     - string size
	 * @param cs       - charset encoding
	 */
	public StringCRLFReader(Consumer<String> function, int size, Charset cs) {
		super(function);
		checkSize(size);
		Objects.requireNonNull(cs);
		this.size = size;
		this.cs = cs;
		if (size == -1) {
			internalbb = ByteBuffer.allocate(1024);
		} else {
			internalbb = ByteBuffer.allocate(size + 2);
		}
	}

	/**
	 * Class contructor.
	 * 
	 * @param size - string size
	 * @param cs   - charset encoding
	 */
	public StringCRLFReader(int size, Charset cs) {
		super();
		checkSize(size);
		Objects.requireNonNull(cs);
		this.size = size;
		this.cs = cs;
		if (size == -1) {
			internalbb = ByteBuffer.allocate(1024);
		} else {
			internalbb = ByteBuffer.allocate(size + 2);
		}
	}

	private void checkSize(int size) {
		if (size < -1) {
			throw new IllegalArgumentException("size should be greater or equal than -1, current : " + size);
		}
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
				if (internalbb.position() >= size) {
					if (previousCharacter == '\r' && newCharacter == '\n') {
						internalbb.flip();
						internalbb.limit(internalbb.limit() - 1);
						state = State.DONE;
						break;
					}
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
	public Data<String> get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return newData(cs.decode(internalbb).toString());
	}

	@Override
	public void reset() {
		state = State.WAITING;
		internalbb.clear();
	}

}
