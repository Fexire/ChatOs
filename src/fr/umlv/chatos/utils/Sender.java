package fr.umlv.chatos.utils;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import fr.umlv.chatos.context.Context;

/**
 * This class fills a context queue with bytebuffer fills with given argument.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class Sender {

	private static final Charset csASCII = StandardCharsets.US_ASCII;
	private static final Charset csUTF8 = StandardCharsets.UTF_8;

	/**
	 * Fills a bytebuffer with the given opcode and adds it to the context queue.
	 * 
	 * @param context Context to which we send data
	 * @param opcode  opcode to send
	 */
	public static void sendOpCode(Context context, byte opcode) {
		Objects.requireNonNull(context);
		context.queueData(ByteBuffer.allocate(1).put(opcode).flip());
	}

	/**
	 * Fills a bytebuffer with the given opcode and a short for adds it to the
	 * context queue.
	 * 
	 * @param context Context to which we send data
	 * @param opcode  opcode to send
	 * @param sh      short value to send
	 */
	public static void sendShort(Context context, byte opcode, short sh) {
		Objects.requireNonNull(context);
		context.queueData(ByteBuffer.allocate(1 + Short.BYTES).put(opcode).putShort(sh).flip());
	}

	/**
	 * Fills a bytebuffer with the given opcode, short, and encoded string to adds
	 * it to the context queue.
	 * 
	 * @param context        Context to which we send data
	 * @param opcode         opcode to send
	 * @param sh             short value to send
	 * @param encoded_string the encoded string to send
	 */
	public static void sendShortString(Context context, byte opcode, Short sh, ByteBuffer encoded_string) {
		Objects.requireNonNull(context);
		Objects.requireNonNull(encoded_string);
		context.queueData(ByteBuffer.allocate(1 + Short.BYTES * 2 + encoded_string.limit()).put(opcode).putShort(sh)
				.putShort((short) encoded_string.limit()).put(encoded_string).flip());
	}

	/**
	 * Fills a bytebuffer with the given opcode, and encoded string to adds it to
	 * the context queue.
	 * 
	 * @param context        Context to which we send data
	 * @param opcode         opcode to send
	 * @param encoded_string the encoded string to send
	 */
	public static void sendString(Context context, byte opcode, ByteBuffer encoded_string) {
		Objects.requireNonNull(context);
		Objects.requireNonNull(encoded_string);
		context.queueData(ByteBuffer.allocate(1 + Short.BYTES + encoded_string.limit()).put(opcode)
				.putShort((short) encoded_string.limit()).put(encoded_string).flip());
	}

	/**
	 * Fills a bytebuffer with the given opcode, integer and encoded string to adds
	 * it to the context queue.
	 * 
	 * @param context Context to which we send data
	 * @param opcode  opcode to send
	 * @param integer integer value to send
	 * @param sh      short value to send
	 */
	public static void sendIntShort(Context context, byte opcode, int integer, short sh) {
		Objects.requireNonNull(context);
		context.queueData(
				ByteBuffer.allocate(1 + Integer.BYTES + Short.BYTES).put(opcode).putInt(integer).putShort(sh).flip());
	}

	/**
	 * Fills a bytebuffer with the given encoded string to adds it to the context
	 * queue.
	 * 
	 * @param context        Context to which we send data
	 * @param encoded_string the encoded string to send
	 */
	public static void sendString(Context context, ByteBuffer encoded_string) {
		Objects.requireNonNull(context);
		Objects.requireNonNull(encoded_string);
		context.queueData(ByteBuffer.allocate(Short.BYTES + encoded_string.limit())
				.putShort((short) encoded_string.limit()).put(encoded_string).flip());
	}

	/**
	 * Fills a bytebuffer with a map of short and bytebuffer to adds it to the
	 * context queue.
	 * 
	 * @param context     Context to which we send data
	 * @param idPseudoMap id and logins to send
	 */
	public static void sendClientList(Context context, Map<Short, ByteBuffer> idPseudoMap) {
		Objects.requireNonNull(context);
		Objects.requireNonNull(idPseudoMap);
		var bb = ByteBuffer.allocate(1 + Short.BYTES + (Short.BYTES * 2 + 1024) * idPseudoMap.size());
		bb.put((byte) 0).putShort((short) idPseudoMap.size());
		idPseudoMap.forEach((i, buffer) -> bb.putShort(i).putShort((short) buffer.limit()).put(buffer));
		context.queueData(bb.flip());
	}

	/**
	 * Fills a bytebuffer with http header not found to adds it to the context
	 * queue.
	 * 
	 * @param context Context to which we send data
	 */
	public static void sendHTTPNotFound(Context context) {
		Objects.requireNonNull(context);
		context.queueData(ByteBuffer.allocate(16).put(csASCII.encode("HTTP/1.1 404")).put((byte) '\r').put((byte) '\n')
				.put((byte) '\r').put((byte) '\n').flip());
	}

	/**
	 * Fills a bytebuffer with http header OK and a list of string to adds it to the
	 * context queue.
	 * 
	 * @param context Context to which we send data
	 * @param path    Resource location
	 * @param file    file content
	 */
	public static void sendHTTPFile(Context context, String path, List<String> file) {
		Objects.requireNonNull(context);
		Objects.requireNonNull(path);
		Objects.requireNonNull(file);
		var bb = ByteBuffer.allocate(1024).put(csASCII.encode("HTTP/1.1 200 OK")).put((byte) '\r').put((byte) '\n')
				.put(csASCII.encode("Transfer-Encoding: chunked")).put((byte) '\r').put((byte) '\n')
				.put(csASCII.encode("Content-Type: text; charset=UTF-8")).put((byte) '\r').put((byte) '\n')
				.put(csASCII.encode("Content-Location: " + path)).put((byte) '\r').put((byte) '\n').put((byte) '\r')
				.put((byte) '\n');
		for (var line : file) {
			var encoded_line = csUTF8.encode(line);
			bb.putInt(encoded_line.limit()).put((byte) '\r').put((byte) '\n').put(encoded_line).put((byte) '\r')
					.put((byte) '\n');
		}
		bb.putInt(0).put((byte) '\r').put((byte) '\n').put((byte) '\r').put((byte) '\n');
		context.queueData(bb.flip());
	}

	/**
	 * Fills a bytebuffer with HTTP GET message and a path to adds it to the context
	 * queue.
	 * 
	 * @param context Context to which we send data
	 * @param path    resource path
	 */
	public static void sendHTTPGET(Context context, String path) {
		Objects.requireNonNull(context);
		Objects.requireNonNull(path);
		context.queueData(ByteBuffer.allocate(17 + path.length()).put(csASCII.encode("GET " + path + " HTTP/1.1"))
				.put((byte) '\r').put((byte) '\n').put((byte) '\r').put((byte) '\n').flip());
	}
}
