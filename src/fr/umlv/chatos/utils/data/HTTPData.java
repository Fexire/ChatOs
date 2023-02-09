package fr.umlv.chatos.utils.data;

import java.util.Objects;

/**
 * Represents data for HTTP.
 * 
 * @author Benjamin JEDROCHA, Florian DURAND
 *
 */
public class HTTPData {
	/**
	 * Represents different types status of http.
	 * 
	 * @author Benjamin JEDROCHA, Florian DURAND
	 *
	 */
	public enum HTTP_TYPE {
		/**
		 * HTTP request
		 */
		REQUEST,
		/**
		 * HTTP response
		 */
		RESPONSE,
		/**
		 * HTTP error
		 */
		ERROR;
	}

	private final HTTP_TYPE httpType;
	private final String response;
	private final String path;

	/**
	 * Class constructor.
	 * 
	 * @param httpType HTTP type
	 * @param string   Message
	 * @param path     file path
	 */
	public HTTPData(HTTP_TYPE httpType, String string, String path) {
		Objects.requireNonNull(httpType);
		Objects.requireNonNull(string);
		Objects.requireNonNull(path);
		this.httpType = httpType;
		this.response = string;
		this.path = path;
	}

	/**
	 * Class constructor.
	 * 
	 * @param httpType HTTP type
	 * @param string   Message
	 */
	public HTTPData(HTTP_TYPE httpType, String string) {
		Objects.requireNonNull(httpType);
		Objects.requireNonNull(string);
		this.httpType = httpType;
		this.response = string;
		this.path = null;
	}

	/**
	 * 
	 * @return the http type.
	 */
	public HTTP_TYPE getHttpType() {
		return httpType;
	}

	/**
	 * 
	 * @return the response.
	 */
	public String getResponse() {
		return response;
	}

	/**
	 * 
	 * @return the path.
	 */
	public String getPath() {
		return path;
	}

}
