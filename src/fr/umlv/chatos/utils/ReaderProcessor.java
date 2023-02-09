package fr.umlv.chatos.utils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import fr.umlv.chatos.utils.reader.Reader;

/**
 * Allows you to link an opcode to a reader.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class ReaderProcessor {
	private final Map<Integer, Supplier<Reader<?>>> packets = new HashMap<>();
	private Optional<Reader<?>> reader = Optional.empty();
	private final Supplier<Optional<Reader<?>>> setupReader;
	private final Runnable onError;

	/**
	 * Class constructor. There is a setup reader which will only be run once at the
	 * start.
	 * 
	 * @param setupReader - this is executed one time
	 * @param onError     - this runnable is executed on error.
	 */
	public ReaderProcessor(Supplier<Optional<Reader<?>>> setupReader, Runnable onError) {
		Objects.requireNonNull(setupReader);
		Objects.requireNonNull(onError);
		this.setupReader = setupReader;
		this.onError = onError;
	}

	/**
	 * Class constructor.
	 * 
	 * @param onError - this runnable is executed on error.
	 */
	public ReaderProcessor(Runnable onError) {
		Objects.requireNonNull(onError);
		this.setupReader = null;
		this.onError = onError;
	}

	/**
	 * Put a new Supplier which will be executed when reading the given opcode.
	 * 
	 * If a supplier already exist, he is crushed.
	 * 
	 * @param opcode     opcode to map
	 * @param new_reader reader supplier to map with the opcode
	 */
	public void put(int opcode, Supplier<Reader<?>> new_reader) {
		Objects.requireNonNull(new_reader);
		packets.put(opcode, new_reader);
	}

	/**
	 * Process the bytebuffer with the right reader according to the opcode and the
	 * setup reader.
	 * 
	 * @param bbin ByteBuffer to process
	 */
	public void process(ByteBuffer bbin) {
		if (reader.isEmpty()) {
			if (setupReader != null) {
				reader = setupReader.get();
			}
			if (reader.isEmpty()) {
				if (bbin.position() > 0) {
					var opcode = bbin.flip().get();
					bbin.compact();
					packets.computeIfPresent((int) opcode, (i, r) -> {
						reader = Optional.of(r.get());
						return r;
					});
				} else {
					return;
				}
			}
		}
		Reader.ProcessStatus status = reader.get().process(bbin);
		switch (status) {
		case DONE:
			reader.get().get().process();
			reader = Optional.empty();
		case REFILL:
			return;
		case ERROR:
			onError.run();
			return;
		}
	}

}
