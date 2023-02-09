package fr.umlv.test;


import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.umlv.chatos.utils.HTTPHeader;
import fr.umlv.chatos.utils.data.ShortString;
import fr.umlv.chatos.utils.reader.ClientListReader;
import fr.umlv.chatos.utils.reader.IntReader;
import fr.umlv.chatos.utils.reader.IntShortReader;
import fr.umlv.chatos.utils.reader.ShortReader;
import fr.umlv.chatos.utils.reader.ShortStringReader;
import fr.umlv.chatos.utils.reader.StringReader;
import fr.umlv.chatos.utils.reader.http.ChunksReader;
import fr.umlv.chatos.utils.reader.http.HTTPHeaderReader;
import fr.umlv.chatos.utils.reader.http.HTTPReader;
import fr.umlv.chatos.utils.reader.http.IntCRLFReader;
import fr.umlv.chatos.utils.reader.http.StringCRLFReader;


class ReaderTest {
	private class TestList {
		private List<ShortString> list;

		public void setList(List<ShortString> list) {
			this.list = list;
		}

		public List<ShortString> getList() {
			return list;
		}
	}

	private class TestInt {
		private int integer;

		public int getInteger() {
			return integer;
		}

		public void setInteger(int integer) {
			this.integer = integer;
		}
	}

	private class TestShort {
		private short sh;

		public short getShort() {
			return sh;
		}

		public void setShort(short sh) {
			this.sh = sh;
		}
	}

	private class TestShortString {
		private short sh;
		private String string;

		public short getShort() {
			return sh;
		}

		public String getString() {
			return string;
		}

		public void setShort(short sh) {
			this.sh = sh;
		}

		public void setString(String string) {
			this.string = string;
		}
	}

	private class TestString {
		private String string;

		public String getString() {
			return string;
		}

		public void setString(String string) {
			this.string = string;
		}
	}

	private class TestIntShort {
		private int port;
		private short id;

		public short getId() {
			return id;
		}

		public int getPort() {
			return port;
		}

		public void setId(short id) {
			this.id = id;
		}

		public void setPort(int port) {
			this.port = port;
		}

	}

	private class TestHeader {
		private HTTPHeader header;

		public HTTPHeader getHeader() {
			return header;
		}

		public void setHeader(HTTPHeader header) {
			this.header = header;
		}
	}

	@Test
	void testIntShortReader() {
		TestIntShort t = new TestIntShort();
		IntShortReader reader = new IntShortReader(values -> {
			t.setPort(values.getInteger());
			t.setId(values.getShort());
		});
		ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES * 2 + Short.BYTES * 2).putInt(4).putShort((short) 0)
				.putInt(10).putShort((short) 32_760);
		reader.process(bb);
		var data = reader.get();
		data.process();
		assertEquals(4, t.getPort());
		assertEquals(0, t.getId());
		reader.reset();
		reader.process(bb);
		data = reader.get();
		data.process();
		assertEquals(10, t.getPort());
		assertEquals(32_760, t.getId());
	}

	@Test
	void testIntReader() {
		TestInt t = new TestInt();
		IntReader reader = new IntReader(i -> {
			t.setInteger(i);
		});
		ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES * 2).putInt(4).putInt(0);
		reader.process(bb);
		var data = reader.get();
		data.process();
		assertEquals(4, t.getInteger());
		reader.reset();
		reader.process(bb);
		data = reader.get();
		data.process();
		assertEquals(0, t.getInteger());
	}

	@Test
	void testShortReader() {
		TestShort t = new TestShort();
		ShortReader reader = new ShortReader(i -> {
			t.setShort(i);
		});
		ByteBuffer bb = ByteBuffer.allocate(Short.BYTES * 2).putShort((short) 4).putShort((short) 0);
		reader.process(bb);
		var data = reader.get();
		data.process();
		assertEquals(4, t.getShort());
		reader.reset();
		reader.process(bb);
		data = reader.get();
		data.process();
		assertEquals(0, t.getShort());
	}

	@Test
	void testStringReader() {
		TestString t = new TestString();
		StringReader reader = new StringReader(s -> {
			t.setString(s);
		});
		Charset cs = StandardCharsets.UTF_8;
		ByteBuffer bb = ByteBuffer.allocate(Short.BYTES * 2 + 8).putShort((short) 4).put(cs.encode("plop"))
				.putShort((short) 4).put(cs.encode("plip"));
		reader.process(bb);
		var data = reader.get();
		data.process();
		assertEquals("plop", t.getString());
		reader.reset();
		reader.process(bb);
		data = reader.get();
		data.process();
		assertEquals("plip", t.getString());
	}

	@Test
	void testShortStringReader() {
		TestShortString t = new TestShortString();
		ShortStringReader reader = new ShortStringReader(o -> {
			t.setShort(o.getShort());
			t.setString(o.getString());
		});
		Charset cs = StandardCharsets.UTF_8;
		ByteBuffer bb = ByteBuffer.allocate(Short.BYTES * 4 + 8).putShort((short) 4).putShort((short) 4)
				.put(cs.encode("plop")).putShort((short) 12).putShort((short) 4).put(cs.encode("plip"));
		reader.process(bb);
		var data = reader.get();
		data.process();
		assertEquals(4, t.getShort());
		assertEquals("plop", t.getString());
		reader.reset();
		reader.process(bb);
		data = reader.get();
		data.process();
		assertEquals(12, t.getShort());
		assertEquals("plip", t.getString());
	}

	@Test
	void testClientListReader() {
		TestList t = new TestList();
		ClientListReader reader = new ClientListReader(list -> {
			t.setList(list);
		});
		Charset cs = StandardCharsets.UTF_8;
		ByteBuffer bb = ByteBuffer.allocate(Short.BYTES + Short.BYTES * 4 + 8).putShort((short) 2).putShort((short) 1)
				.putShort((short) 4).put(cs.encode("plop")).putShort((short) 2).putShort((short) 4)
				.put(cs.encode("plip"));
		reader.process(bb);
		var data = reader.get();
		data.process();

		var list = new ArrayList<ShortString>();
		list.add(new ShortString((short) 1, "plop"));
		list.add(new ShortString((short) 2, "plip"));
		assertEquals(list, t.getList());
	}

	@Test
	void testIntCRLFReader() {
		TestInt t = new TestInt();
		IntCRLFReader reader = new IntCRLFReader(i -> {
			t.setInteger(i);
		});
		ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES * 2 + 4).putInt(12).put((byte) '\r').put((byte) '\n')
				.putInt(45).put((byte) '\r').put((byte) '\n');
		reader.process(bb);
		var data = reader.get();
		data.process();
		assertEquals(12, t.getInteger());
		reader.reset();
		reader.process(bb);
		data = reader.get();
		data.process();
		assertEquals(45, t.getInteger());
	}

	@Test
	void testStrinCRLFReader() {
		TestString t = new TestString();
		Charset cs = StandardCharsets.UTF_8;
		StringCRLFReader reader = new StringCRLFReader(s -> {
			t.setString(s);
		}, 5, cs);
		ByteBuffer bb = ByteBuffer.allocate(5 * 2 + 4).put(cs.encode("salut")).put((byte) '\r').put((byte) '\n')
				.put(cs.encode("tests")).put((byte) '\r').put((byte) '\n');
		reader.process(bb);
		var data = reader.get();
		data.process();
		assertEquals("salut", t.getString());
		reader.reset();
		reader.process(bb);
		data = reader.get();
		data.process();
		assertEquals("tests", t.getString());
	}

	@Test
	void testChunksReader() {
		TestString t = new TestString();
		Charset cs = StandardCharsets.US_ASCII;
		ChunksReader reader = new ChunksReader(s -> {
			t.setString(s);
		}, cs);
		ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES * 3 + 6 + 5 * 2 + 6).putInt(5).put((byte) '\r')
				.put((byte) '\n').put(cs.encode("salut")).put((byte) '\r').put((byte) '\n').putInt(5).put((byte) '\r')
				.put((byte) '\n').put(cs.encode("tests")).put((byte) '\r').put((byte) '\n').putInt(0).put((byte) '\r')
				.put((byte) '\n').put((byte) '\r').put((byte) '\n');
		;
		reader.process(bb);
		var data = reader.get();
		data.process();
		assertEquals("saluttests", t.getString());
	}

	@Test
	void testHeaderReader() {
		TestHeader httpheader = new TestHeader();
		HTTPHeaderReader reader = new HTTPHeaderReader(header -> {
			httpheader.setHeader(header);
		});
		Charset cs = StandardCharsets.US_ASCII;
		ByteBuffer bb = ByteBuffer.allocate(1024).put(cs.encode("HTTP/1.1 200 OK")).put((byte) '\r').put((byte) '\n')
				.put(cs.encode("Date: Thu, 01 Mar 2018 17:28:07 GMT")).put((byte) '\r').put((byte) '\n')
				.put(cs.encode("Server: Apache")).put((byte) '\r').put((byte) '\n').put((byte) '\r').put((byte) '\n');
		reader.process(bb);
		var data = reader.get();
		data.process();
		var header = httpheader.getHeader();
		assertEquals(200, header.getCode());
		assertEquals("Apache", header.getFields().get("server"));
	}

	@Test
	void testHTTPResponseReaderChunk() {
		TestString t = new TestString();
		HTTPReader reader = new HTTPReader(s -> {
			t.setString(s.getResponse());
		});
		Charset cs = StandardCharsets.UTF_8;
		ByteBuffer bb = ByteBuffer.allocate(1024).put(cs.encode("HTTP/1.1 200 OK")).put((byte) '\r').put((byte) '\n')
				.put(cs.encode("Transfer-Encoding: chunked")).put((byte) '\r').put((byte) '\n')
				.put(cs.encode("Content-Location: .")).put((byte) '\r').put((byte) '\n')
				.put(cs.encode("Content-Type: text; charset=UTF-8")).put((byte) '\r').put((byte) '\n').put((byte) '\r')
				.put((byte) '\n').putInt(5).put((byte) '\r').put((byte) '\n').put(cs.encode("salut")).put((byte) '\r')
				.put((byte) '\n').putInt(5).put((byte) '\r').put((byte) '\n').put(cs.encode("tésts")).put((byte) '\r')
				.put((byte) '\n').putInt(0).put((byte) '\r').put((byte) '\n').put((byte) '\r').put((byte) '\n');
		reader.process(bb);
		var data = reader.get();
		data.process();
		assertEquals("saluttésts", t.getString());
	}

	@Test
	void testHTTPResponseReaderNoChunk() {
		TestString t = new TestString();
		HTTPReader reader = new HTTPReader(s -> {
			t.setString(s.getResponse());
		});
		Charset cs = StandardCharsets.UTF_8;
		ByteBuffer bb = ByteBuffer.allocate(1024).put(cs.encode("HTTP/1.1 200 OK")).put((byte) '\r').put((byte) '\n')
				.put(cs.encode("Content-Length: 11")).put((byte) '\r').put((byte) '\n')
				.put(cs.encode("Content-Location: .")).put((byte) '\r').put((byte) '\n')
				.put(cs.encode("Content-Type: text; charset=UTF-8")).put((byte) '\r').put((byte) '\n').put((byte) '\r')
				.put((byte) '\n').put(cs.encode("salut")).put(cs.encode("tésts"));
		reader.process(bb);
		var data = reader.get();
		data.process();
		assertEquals("saluttésts", t.getString());
	}

	@Test
	void testHTTPRequestReader() {
		TestString t = new TestString();
		HTTPReader reader = new HTTPReader(s -> {
			t.setString(s.getResponse());
		});
		Charset cs = StandardCharsets.US_ASCII;
		ByteBuffer bb = ByteBuffer.allocate(1024).put(cs.encode("GET /~carayol/ HTTP/1.1")).put((byte) '\r')
				.put((byte) '\n').put(cs.encode("Host: igm.univ-mlv.fr")).put((byte) '\r').put((byte) '\n')
				.put((byte) '\r').put((byte) '\n');
		reader.process(bb);
		var data = reader.get();
		data.process();
		assertEquals("/~carayol/", t.getString());
	}

}
