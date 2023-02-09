package fr.umlv.chatos.utils;

import static fr.umlv.chatos.utils.HTTPException.ensure;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * This class represents a http header. Help to create an http header.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class HTTPHeader {

	/**
	 * Supported versions of the HTTP Protocol
	 */

	private static final String[] LIST_SUPPORTED_VERSIONS = new String[] { "HTTP/1.0", "HTTP/1.1", "HTTP/2.0" };
	/**
	 * Set of Supported versions of the HTTP Protocol
	 */
	public static final Set<String> SUPPORTED_VERSIONS = Collections
			.unmodifiableSet(new HashSet<>(Arrays.asList(LIST_SUPPORTED_VERSIONS)));

	private final String version;
	private final String path;
	private final int code;
	private final Map<String, String> fields;

	private HTTPHeader(String version, int code, Map<String, String> fields, String path) throws HTTPException {
		this.version = version;
		this.code = code;
		this.fields = Collections.unmodifiableMap(fields);
		this.path = path;
	}

	/**
	 * Create an HTTPHeader with a response code and a map for fill him.
	 * 
	 * @param response first CRLF line response
	 * @param fields   Header fields
	 * @return new HTTPHeader correctly parsed
	 * @throws HTTPException If something was not correct
	 */
	public static HTTPHeader create(String response, Map<String, String> fields) throws HTTPException {
		Objects.requireNonNull(response);
		Objects.requireNonNull(fields);
		String[] tokens = response.split(" ");
		// Treatment of the response line
		ensure(tokens.length >= 2, "Badly formed response:\n" + response);
		String version;
		String path = null;
		int code = 0;
		if (tokens[0].equals("GET")) {
			path = tokens[1];
			version = tokens[2];
		} else {
			version = tokens[0];
			try {
				code = Integer.valueOf(tokens[1]);
				ensure(code >= 100 && code < 600, "Invalid code in response:\n" + response);
			} catch (NumberFormatException e) {
				ensure(false, "Invalid response:\n" + response);
			}
		}
		ensure(HTTPHeader.SUPPORTED_VERSIONS.contains(version), "Unsupported version in response:\n" + response);
		Map<String, String> fieldsCopied = new HashMap<>();
		for (String s : fields.keySet())
			fieldsCopied.put(s.toLowerCase(), fields.get(s).trim());
		return new HTTPHeader(version, code, fieldsCopied, path);
	}

	/**
	 * 
	 * @return the header version
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * 
	 * @return the header code
	 */
	public int getCode() {
		return code;
	}

	/**
	 * 
	 * @return the header fields
	 */
	public Map<String, String> getFields() {
		return fields;
	}

	/**
	 * 
	 * @return the header path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * @return the value of the Content-Length field in the header -1 if the field
	 *         does not exists
	 * @throws HTTPException when the value of Content-Length is not a number
	 */
	public int getContentLength() throws HTTPException {
		String s = fields.get("content-length");
		if (s == null)
			return -1;
		else {
			try {
				return Integer.valueOf(s.trim());
			} catch (NumberFormatException e) {
				throw new HTTPException("Invalid Content-Length field value :\n" + s);
			}
		}
	}

	/**
	 * @return the Content-Type null if there is no Content-Type field
	 */
	public String getContentType() {
		String s = fields.get("content-type");
		if (s != null) {
			return s.split(";")[0].trim();
		} else
			return null;
	}

	/**
	 * 
	 * @return the header location, null if there is nothing
	 */
	public String getContentLocation() {
		String s = fields.get("content-location");
		if (s != null) {
			return s.trim();
		} else
			return null;
	}

	/**
	 * @return the charset corresponding to the Content-Type field null if charset
	 *         is unknown or unavailable on the JVM
	 */
	public Charset getCharset() {
		Charset cs = null;
		String s = fields.get("content-type");
		if (s == null)
			return cs;
		for (String t : s.split(";")) {
			if (t.contains("charset=")) {
				try {
					cs = Charset.forName(t.split("=")[1].trim());
				} catch (Exception e) {
					// If the Charset is unknown or unavailable we turn null
				}
				return cs;
			}
		}
		return cs;
	}

	/**
	 * @return true if the header correspond to a chunked response
	 */
	public boolean isChunkedTransfer() {
		return fields.containsKey("transfer-encoding") && fields.get("transfer-encoding").trim().equals("chunked");
	}

}
