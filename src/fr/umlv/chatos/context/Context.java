package fr.umlv.chatos.context;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

import fr.umlv.chatos.server.ServerChatOS;
import fr.umlv.chatos.utils.ReaderProcessor;
import fr.umlv.chatos.utils.reader.Reader;

/**
 * Represents a context.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public interface Context {

	/**
	 * Performs the read action on sc
	 *
	 * The convention is that both buffers are in write-mode before the call to
	 * doRead and after the call
	 *
	 * @throws IOException If an IOException occurs
	 */
	void doRead() throws IOException;

	/**
	 * Performs the connection.
	 * 
	 * @throws IOException If an IOException occurs
	 */
	void doConnect() throws IOException;

	/**
	 * Performs the write action on sc
	 *
	 * The convention is that both buffers are in write-mode before the call to
	 * doWrite and after the call
	 *
	 * @throws IOException If an IOException occurs
	 */
	void doWrite() throws IOException;

	/**
	 * Add a bytebuffer to the queue.
	 * 
	 * @param bb ByteBuffer add to the queue
	 */
	void queueData(ByteBuffer bb);

	/**
	 * Represents a context with its own bytebuffer, SelectionKey, SocketChannel and
	 * a queue.
	 * 
	 * 
	 * @author Benjamin JEDROCHA, Florian DURAND
	 *
	 */
	public static abstract class ContextAbstract implements Context {

		/**
		 * Default bytebuffer size.
		 */
		public static int BUFFER_SIZE = 1_024;

		private SelectionKey key;
		private SocketChannel sc;
		private final ByteBuffer bbin = ByteBuffer.allocate(BUFFER_SIZE);
		private final ByteBuffer bbout = ByteBuffer.allocate(BUFFER_SIZE * 2 + Integer.BYTES * 2);
		private final Queue<ByteBuffer> queue = new LinkedList<>();
		private boolean closed = false;

		/**
		 * Class constructor specifying the SelectionKey.
		 * 
		 * @param key Context SelectionKey
		 */
		public ContextAbstract(SelectionKey key) {
			this.key = key;
			if (key != null) {
				this.sc = (SocketChannel) key.channel();
			}
		}

		/**
		 * This method is called after reading the socket
		 * 
		 */
		protected abstract void processIn();

		/**
		 * 
		 */
		public abstract void DoClose();

		/**
		 * Try to fill bbout from the message queue
		 *
		 */
		private void processOut() {
			while (!queue.isEmpty() && bbout.remaining() >= Integer.BYTES) {
				var bb = queue.peek();
				if (bb.remaining() <= bbout.remaining()) {
					queue.remove();
					bbout.put(bb);
				} else {
					break;
				}
			}
		}

		@Override
		public void doRead() throws IOException {
			if (sc.read(bbin) == -1) {
				closed = true;
			}
			processIn();
			updateInterestOps();
		}

		@Override
		public void doWrite() throws IOException {
			bbout.flip();
			sc.write(bbout);
			bbout.compact();
			updateInterestOps();
		}

		private void updateInterestOps() {
			if (key == null || key.interestOps() == SelectionKey.OP_CONNECT)
				return;
			var interesOps = 0;
			if (!closed && bbin.hasRemaining()) {
				interesOps = interesOps | SelectionKey.OP_READ;
			}
			if (bbout.position() != 0) {
				interesOps |= SelectionKey.OP_WRITE;
			}
			if (interesOps == 0) {
				silentlyClose();
				return;
			}
			key.interestOps(interesOps);
		}

		/**
		 * Close a SocketChannel while ignoring IOExecption
		 */
		public void silentlyClose() {
			try {
				sc.close();
			} catch (IOException e) {
				// ignore exception
			}
		}

		/**
		 * Informs the context that it must end without abruptly interrupting it.
		 * 
		 */
		public void close() {
			closed = true;
		}

		@Override
		public void doConnect() throws IOException {
			if (!sc.finishConnect())
				return;
			key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
		}

		/**
		 * Registers a unique key.
		 * 
		 * @param key Context SelectionKey
		 */
		public void setKey(SelectionKey key) {
			Objects.requireNonNull(key);
			this.key = key;
			sc = (SocketChannel) key.channel();
			updateInterestOps();
		}

		/**
		 * 
		 * @return the InetAddress
		 */
		public InetAddress getInetAddress() {
			if (sc == null) {
				throw new IllegalStateException("Need to use setKey() before using this method !");
			}
			return sc.socket().getInetAddress();
		}

		/**
		 * Transfers to the receiving context
		 * 
		 * @param server Server object
		 */
		protected void processInTCP(ServerChatOS server) {
			server.transferDataTCP(bbin, this);
		}

		/**
		 * Process the ReaderProcessor
		 * 
		 * @param readerProcessor ReaderProcessor mapping opcodes
		 */
		protected void processInProcessor(ReaderProcessor readerProcessor) {
			readerProcessor.process(bbin);
		}

		/**
		 * Process with the given reader.
		 * 
		 * @param reader Reader to process
		 */
		protected void processInReader(Reader<?> reader) {
			for (;;) {
				Reader.ProcessStatus status = reader.process(bbin);
				switch (status) {
				case DONE:
					reader.get().process();
					reader.reset();
					break;
				case REFILL:
					return;
				case ERROR:
					silentlyClose();
					return;
				}
			}
		}

		@Override
		public void queueData(ByteBuffer data) {
			Objects.requireNonNull(data);
			queue.add(data);
			processOut();
			updateInterestOps();
		}
	}

}
