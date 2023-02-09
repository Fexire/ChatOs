package fr.umlv.chatos.utils.reader.http;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.Consumer;

import fr.umlv.chatos.utils.data.Data;
import fr.umlv.chatos.utils.reader.AbstractReader;

/**
 * Represents a bytes reader. This class allows to read n bytes on a bytebuffer
 * and give a string result depending of a charset given.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class BytesReader extends AbstractReader<String> {

	private enum State {
		DONE, WAITING, ERROR
	};

	private State state = State.WAITING;
	private final ByteBuffer internalbb;
	private final Charset cs;

	/**
	 * Class constructor.
	 * 
	 * @param function - Function to process after read
	 * @param size     - the number of bytes that will be read
	 * @param cs       - the Charset using for create the string after reading the n
	 *                 bytes
	 */
	public BytesReader(Consumer<String> function, int size, Charset cs) {
		super(function);
		if (size < 0) {
			throw new IllegalArgumentException("size should be positiv, current : " + size);
		}
		Objects.requireNonNull(cs);
		internalbb = ByteBuffer.allocate(size);
		this.cs = cs;
	}

	/**
	 * Class constructor without function.
	 * 
	 * @param size - the number of bytes that will be read
	 * @param cs   - the Charset using for create the string after reading the n
	 *             bytes
	 */
	public BytesReader(int size, Charset cs) {
		super();
		if (size < 0) {
			throw new IllegalArgumentException("size should be positiv, current : " + size);
		}
		Objects.requireNonNull(cs);
		internalbb = ByteBuffer.allocate(size);
		this.cs = cs;
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
