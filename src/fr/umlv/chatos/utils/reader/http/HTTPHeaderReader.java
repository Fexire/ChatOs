package fr.umlv.chatos.utils.reader.http;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import fr.umlv.chatos.utils.HTTPException;
import fr.umlv.chatos.utils.HTTPHeader;
import fr.umlv.chatos.utils.data.Data;
import fr.umlv.chatos.utils.reader.AbstractReader;

/**
 * Represents a HTTP header reader.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class HTTPHeaderReader extends AbstractReader<HTTPHeader> {

	private enum State {
		DONE, READING_RESPONSE, READING_HEADER, ERROR
	};

	private final StringCRLFReader stringCRLFReader = new StringCRLFReader(-1, StandardCharsets.US_ASCII);
	private State state = State.READING_RESPONSE;
	private String response;
	private String headerLine;
	private final Map<String, String> fields = new HashMap<>();

	/**
	 * Class constructor.
	 * 
	 * @param function - Function to process after read
	 */
	public HTTPHeaderReader(Consumer<HTTPHeader> function) {
		super(function);
	}

	/**
	 * Class constructor.
	 */
	public HTTPHeaderReader() {
		super();
	}

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		Objects.requireNonNull(bb);
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		if (state == State.READING_RESPONSE) {
			switch (stringCRLFReader.process(bb)) {
			case DONE: {
				response = stringCRLFReader.get().getData();
				stringCRLFReader.reset();
				state = State.READING_HEADER;
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

		if (state == State.READING_HEADER) {
			while (state != State.DONE) {
				switch (stringCRLFReader.process(bb)) {
				case DONE: {
					headerLine = stringCRLFReader.get().getData();
					if (headerLine.isEmpty()) {
						state = State.DONE;
						return ProcessStatus.DONE;
					}
					String[] field = headerLine.split(": ", 2);
					if (field.length < 2) {
						state = State.ERROR;
						return ProcessStatus.ERROR;
					}
					fields.merge(field[0], field[1], (l, n) -> l + n);
					stringCRLFReader.reset();
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
	public Data<HTTPHeader> get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		try {
			return newData(HTTPHeader.create(response, fields));
		} catch (HTTPException e) {
			throw new IllegalStateException();
		}
	}

	@Override
	public void reset() {
		stringCRLFReader.reset();
		state = State.READING_RESPONSE;
		fields.clear();
	}

}
