package fr.umlv.chatos.utils.reader;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

import fr.umlv.chatos.utils.data.Data;

/**
 * Represent a void reader. Nothing is reading when process. This class is used
 * for performs a Void Consumer.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class VoidReader extends AbstractReader<Void> {

	/**
	 * Class contructor.
	 * 
	 * @param function Function to process after read
	 */
	public VoidReader(Consumer<Void> function) {
		super(function);
	}

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		Objects.requireNonNull(bb);
		return ProcessStatus.DONE;
	}

	@Override
	public Data<Void> get() {
		return newData();
	}

	@Override
	public void reset() {
	}

}
