package com.jesse.db;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;

public class PropertyUtil {
	private static final long serialVersionUID = 1L;
	private final Properties properties = new Properties();
	private String propertyPath;

	private PropertyUtil() {
		try {
			Path path = Paths.get(this.getClass().getClassLoader().getResource("").toURI()).resolve("application.properties");
			propertyPath = path.normalize().toString();
			InputStream stream = new FileInputStream(propertyPath);
			properties.load(stream);
		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();
		}
	}

	private static class InstanceHolder {
		public static final PropertyUtil INSTANCE = new PropertyUtil();
	}

	public static PropertyUtil getInstance() {
		return InstanceHolder.INSTANCE;
	}

	public String getProperty(String key) {
		return properties.getProperty(key);
	}

	public String getProperty(String key, String defaultValue) {
		return properties.getProperty(key, defaultValue);
	}

	public int getPropertyInt(String key, int defaultValue) {
		String setting = properties.getProperty(key);
		if (setting == null) {
			return defaultValue;
		}
		try {
			int value = Integer.parseInt(setting.trim());
			return value;
		} catch (Exception e) {
			return defaultValue;
		}
	}

	public Set<String> getPropertyNames() {
		return properties.stringPropertyNames();
	}

	public boolean containsKey(String key) {
		return properties.containsKey(key);
	}

	public void setProperty(String key, String value) {
		properties.setProperty(key, value);
	}

	public void flush() throws IOException {
		OutputStream stream = new FileOutputStream(propertyPath);
		properties.store(stream, null);
		stream.close();
	}

	protected Object readResolve() {
		return getInstance();
	}

	public Properties getWrappedProperties() {
		return properties;
	}

	public String getPropertyPath() {
		return propertyPath;
	}
}
