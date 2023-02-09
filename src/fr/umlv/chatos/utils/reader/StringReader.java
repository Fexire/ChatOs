package fr.umlv.chatos.utils.reader;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

import fr.umlv.chatos.utils.data.Data;

/**
 * Represents a string reader.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class StringReader extends AbstractReader<String> {

	private enum State {
		DONE, READING_SHORT, READING_STRING, ERROR
	};

	private State state = State.READING_SHORT;
	private static final Charset cs = StandardCharsets.UTF_8;
	private ByteBuffer internalbb;
	private final ShortReader shortReader = new ShortReader();
	private short size;
	private String string;

	/**
	 * Class constructor.
	 * 
	 * @param function Function to process after read
	 */
	public StringReader(Consumer<String> function) {
		super(function);
	}

	/**
	 * Class constructor.
	 * 
	 */
	public StringReader() {
		super();
	}

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		Objects.requireNonNull(bb);
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		if (state == State.READING_SHORT) {
			switch (shortReader.process(bb)) {
			case DONE: {
				size = shortReader.get().getData();
				internalbb = ByteBuffer.allocate(size);
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
			if (internalbb.position() >= size) {
				internalbb.flip();
				var oldlimit = internalbb.limit();
				internalbb.limit(size);
				string = cs.decode(internalbb).toString();
				internalbb.limit(oldlimit);
				bb.put(internalbb);
			} else {
				return ProcessStatus.REFILL;
			}
		}
		state = State.DONE;
		return ProcessStatus.DONE;
	}

	@Override
	public Data<String> get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return newData(string);
	}

	@Override
	public void reset() {
		state = State.READING_SHORT;
		shortReader.reset();
	}

}
