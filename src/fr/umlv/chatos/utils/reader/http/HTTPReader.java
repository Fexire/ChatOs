package fr.umlv.chatos.utils.reader.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

import fr.umlv.chatos.context.Context;
import fr.umlv.chatos.utils.HTTPException;
import fr.umlv.chatos.utils.HTTPHeader;
import fr.umlv.chatos.utils.Sender;
import fr.umlv.chatos.utils.data.Data;
import fr.umlv.chatos.utils.data.HTTPData;
import fr.umlv.chatos.utils.data.HTTPData.HTTP_TYPE;
import fr.umlv.chatos.utils.reader.AbstractReader;

/**
 * Represents a HTTP reader.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class HTTPReader extends AbstractReader<HTTPData> {

	private enum State {
		DONE, READING_HEADER, READING_DATA, ERROR
	};

	private State state = State.READING_HEADER;
	private final HTTPHeaderReader headerReader = new HTTPHeaderReader();
	private HTTPHeader header;
	private ChunksReader chunksReader;
	private BytesReader bytesReader;
	private HTTPData data;

	/**
	 * Class constructor.
	 * 
	 * @param function - Function to process after read
	 */
	public HTTPReader(Consumer<HTTPData> function) {
		super(function);
	}

	/**
	 * Class constructor.
	 * 
	 * @param folder  - where resources are sought
	 * @param context - the context that receives resources
	 */
	public HTTPReader(String folder, Context context) {
		super(httpData -> {
			switch (httpData.getHttpType()) {
			case REQUEST:
				var path = Path.of(folder, httpData.getResponse());
				try {
					Sender.sendHTTPFile(context, httpData.getResponse(), Files.readAllLines(path));
				} catch (IOException e) {
					Sender.sendHTTPNotFound(context);
				}
				break;
			case RESPONSE:
				var filePath = httpData.getPath();
				if (filePath.endsWith(".txt")) {
					System.out.println(httpData.getResponse());
				} else {
					try {
						Files.write(Path.of(folder, filePath), httpData.getResponse().getBytes());
						System.out.println("Fichier " + filePath + " sauvegard�");
					} catch (IOException e) {
						System.out.println("ERREUR : Sauvegarde du fichier interrompue");
					}
				}
				break;
			case ERROR:
				System.out.println(httpData.getResponse());
				break;
			default:
				throw new IllegalArgumentException("Unexpected value: " + httpData.getHttpType());
			}
		});
	}

	@Override
	public ProcessStatus process(ByteBuffer bb) {
		Objects.requireNonNull(bb);
		if (state == State.DONE || state == State.ERROR) {
			throw new IllegalStateException();
		}
		if (state == State.READING_HEADER) {
			switch (headerReader.process(bb)) {
			case DONE: {
				header = headerReader.get().getData();
				if (header.getCode() != 0) {
					if (header.getCode() == 404) {
						data = new HTTPData(HTTP_TYPE.ERROR, "404 Not Found");
						state = State.DONE;
						return ProcessStatus.DONE;
					}
					state = State.READING_DATA;
				} else {
					data = new HTTPData(HTTP_TYPE.REQUEST, header.getPath());
					state = State.DONE;
					return ProcessStatus.DONE;
				}
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

		if (state == State.READING_DATA) {
			if (header.isChunkedTransfer()) {
				chunksReader = new ChunksReader(header.getCharset());
				while (state != State.DONE) {
					switch (chunksReader.process(bb)) {
					case DONE: {
						data = new HTTPData(HTTP_TYPE.RESPONSE, chunksReader.get().getData(),
								header.getContentLocation());
						state = State.DONE;
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
			} else {
				try {
					bytesReader = new BytesReader(header.getContentLength(), header.getCharset());
				} catch (HTTPException e) {
					state = State.ERROR;
					return ProcessStatus.ERROR;
				}
				while (state != State.DONE) {
					switch (bytesReader.process(bb)) {
					case DONE: {
						data = new HTTPData(HTTP_TYPE.RESPONSE, bytesReader.get().getData(),
								header.getContentLocation());
						state = State.DONE;
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
		}
		state = State.DONE;
		return ProcessStatus.DONE;
	}

	@Override
	public Data<HTTPData> get() {
		if (state != State.DONE) {
			throw new IllegalStateException();
		}
		return newData(data);
	}

	@Override
	public void reset() {
		headerReader.reset();
		state = State.READING_HEADER;
	}

}
