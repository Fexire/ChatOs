package fr.umlv.chatos.utils.reader.http;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.function.Consumer;

import fr.umlv.chatos.utils.data.Data;
import fr.umlv.chatos.utils.reader.AbstractReader;

/**
 * Represents a Chunks reader.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class ChunksReader extends AbstractReader<String> {
	private enum State {
		DONE, READING_INT, READING_STRING, ERROR
	};

	private State state = State.READING_INT;
	private final StringBuilder stringBuilder = new StringBuilder();
	private final IntCRLFReader intReader = new IntCRLFReader();
	private StringCRLFReader stringReader;
	private final Charset cs;

	/**
	 * Class constructor.
	 * 
	 * @param function - Function to process after read
	 * @param cs       - the Charset using for create the string after reading the
	 *                 chunks
	 */
	public ChunksReader(Consumer<String> function, Charset cs) {
		super(function);
		Objects.requireNonNull(cs);
		this.cs = cs;
	}

	/**
	 * Class constructor.
	 * 
	 * @param cs - the Charset using for create the string after reading the chunks
	 */
	public ChunksReader(Charset cs) {
		super();
		Objects.requireNonNull(cs);
		this.cs = cs;
	}

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		Objects.requireNonNull(bb);
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		while (state != State.DONE) {
			if (state == State.READING_INT) {
				switch (intReader.process(bb)) {
				case DONE: {
					stringReader = new StringCRLFReader(intReader.get().getData(), cs);
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
					var str = stringReader.get().getData();
					if (str.isEmpty()) {
						state = State.DONE;
						return ProcessStatus.DONE;
					}
					stringBuilder.append(str);
					state = State.READING_INT;
					intReader.reset();
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
		}
		state = State.DONE;
		return ProcessStatus.DONE;
	}

	@Override
	public Data<String> get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return newData(stringBuilder.toString());
	}

	@Override
	public void reset() {
		state = State.READING_INT;
	}

}
