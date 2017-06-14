package io.xlate.edi.schema;

import io.xlate.edi.stream.EDIStreamConstants;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Properties;
import java.util.TreeMap;

public class SchemaUtils {

	static Properties controlIndex = new Properties();
	static NavigableMap<String, String> controlVersions = new TreeMap<>();
	static NavigableMap<String, Schema> controlSchemas = new TreeMap<>();

	static {
		try {
			controlIndex.load(getStream("staedi-control-index.properties"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		for (Map.Entry<Object, Object> entry : controlIndex.entrySet()) {
			final String entryKey = entry.getKey().toString();
			final String entryValue = entry.getValue().toString();

			controlVersions.put(entryKey, entryValue);
			controlSchemas.put(entryKey, null);
		}
	}

	public static InputStream getStream(String resource) {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		return loader.getResourceAsStream(resource);
	}

	public static URL getURL(String resource) {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		return loader.getResource(resource);
	}

	public static Schema getControlSchema(String standard, String version)
			throws EDISchemaException {

		String key = standard + '.' + version;
		Entry<String, Schema> controlEntry = controlSchemas.floorEntry(key);

		if (controlEntry != null && controlEntry.getValue() != null) {
			return controlEntry.getValue();
		}

		if (EDIStreamConstants.Standards.X12.equals(standard)) {
			Entry<String, String> pathEntry = controlVersions.floorEntry(key);
			if (pathEntry != null) {
				Schema created = getXmlSchema(pathEntry.getValue());
				controlSchemas.put(pathEntry.getKey(), created);
				return created;
			}

			return null;
		} else if (EDIStreamConstants.Standards.EDIFACT.equals(standard)) {
			Schema created = getMapSchema(standard, version, "INTERCHANGE");
			controlSchemas.putIfAbsent(key, created);
			return created;
		}

		throw new IllegalArgumentException(standard);
	}

	public static Schema getMapSchema(String standard, String version, String message)
			throws EDISchemaException {

		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		SchemaFactory schemaFactory = SchemaFactory.newFactory(StaEDIMapSchemaFactory.ID, loader);
		final URL location;
		final String stdDir;

		if (System.getProperties().containsKey("io.xlate.edi.standards")) {
			stdDir = System.getProperty("io.xlate.edi.standards");
		} else {
			stdDir = System.getenv("EDI_STANDARDS");
		}

		final URL context;

		if (stdDir != null) {
			File db = new File(stdDir + "/" + standard.toLowerCase() + ".db");

			try {
				context = db.toURI().toURL();
			} catch (MalformedURLException e) {
				throw new EDISchemaException(e);
			}
		} else {
			final String source = standard.toUpperCase() + "/";
			context = getURL(source + standard.toLowerCase() + ".db");
		}

		try {
			location = new URL(context, "#" + version + "/" + message);
		} catch (MalformedURLException e) {
			throw new EDISchemaException(e);
		}

		return schemaFactory.createSchema(location);
	}

	private static Schema getXmlSchema(String resource)
			throws EDISchemaException {

		SchemaFactory schemaFactory = SchemaFactory.newFactory();
		URL location = getURL(resource);
		return schemaFactory.createSchema(location);
	}
}