package fr.umlv.chatos.utils.reader;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import fr.umlv.chatos.utils.data.Data;
import fr.umlv.chatos.utils.data.ShortString;

/**
 * Reader for clients list.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class ClientListReader extends AbstractReader<List<ShortString>> {

	private enum State {
		DONE, READING_NB_CLIENTS, READING_CLIENTS, ERROR
	};

	private State state = State.READING_NB_CLIENTS;
	private final ShortReader shortReader = new ShortReader();
	private final ShortStringReader shortStringReader = new ShortStringReader();
	private int nb_clients;
	private final List<ShortString> list = new ArrayList<>();

	/**
	 * Class constructor.
	 * 
	 * @param function Function to process after read
	 */
	public ClientListReader(Consumer<List<ShortString>> function) {
		super(function);
	}

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		Objects.requireNonNull(bb);
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		if (state == State.READING_NB_CLIENTS) {
			switch (shortReader.process(bb)) {
			case DONE: {
				nb_clients = shortReader.get().getData();
				state = State.READING_CLIENTS;
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
		while (state == State.READING_CLIENTS && nb_clients > 0) {

			switch (shortStringReader.process(bb)) {
			case DONE: {
				var data = shortStringReader.get().getData();
				list.add(data);
				shortStringReader.reset();
				nb_clients--;
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
	public Data<List<ShortString>> get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return newData(list);
	}

	@Override
	public void reset() {
		state = State.READING_NB_CLIENTS;
		shortReader.reset();
		shortStringReader.reset();
	}

}
