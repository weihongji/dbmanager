package com.jesse.db;

import org.junit.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static org.junit.Assert.*;

public class PooledConnectionTest {

	@Test
	public void testToString() {
		PooledConnection connection = new PooledConnection(null);
		// Available, created at: 23:24:00, usage: 0
		assertEquals("Available, created at: " + connection.getCreatedTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + ", usage: 0", connection.toString());
	}

	@Test
	public void testToString1() {
		PooledConnection connection = new PooledConnection(null);
		LocalDateTime created = LocalDateTime.now().minusDays(5);
		try {
			Field createTimeField = PooledConnection.class.getDeclaredField("createdTime");
			createTimeField.setAccessible(true);
			createTimeField.set(connection, created);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// Available, created at: Feb 8 23:24:00, usage: 0
		assertEquals("Available, created at: " + created.format(DateTimeFormatter.ofPattern("MMM d HH:mm:ss")) + ", usage: 0", connection.toString());
	}

	@Test
	public void testToString2() {
		PooledConnection connection = new PooledConnection(null);
		LocalDate today = LocalDate.now();
		LocalDateTime created = today.atTime(9, 21, 34);
		LocalDateTime lastUsed = today.atTime(10, 21, 34);
		try {
			Field createTimeField = PooledConnection.class.getDeclaredField("createdTime");
			createTimeField.setAccessible(true);
			createTimeField.set(connection, created);

			Field lastUseTimeField = PooledConnection.class.getDeclaredField("lastUsedTime");
			lastUseTimeField.setAccessible(true);
			lastUseTimeField.set(connection, lastUsed);

			Field usedCountField = PooledConnection.class.getDeclaredField("usedCount");
			usedCountField.setAccessible(true);
			usedCountField.set(connection, 2);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// Engaged, created at: Feb 11 01:02:03, last used: 00:08:00, usage: 109
		assertEquals("Available, created at: 09:21:34, last used: 10:21:34, usage: 2", connection.toString());
	}

	@Test
	public void testToString3() {
		PooledConnection connection = new PooledConnection(null);
		LocalDate today = LocalDate.now();
		LocalDateTime created = today.minusDays(2).atTime(1, 2, 3);
		LocalDateTime lastUsed = today.atTime(0, 8, 0);
		try {
			Field createTimeField = PooledConnection.class.getDeclaredField("createdTime");
			createTimeField.setAccessible(true);
			createTimeField.set(connection, created);

			Field lastUseTimeField = PooledConnection.class.getDeclaredField("lastUsedTime");
			lastUseTimeField.setAccessible(true);
			lastUseTimeField.set(connection, lastUsed);

			Field usedCountField = PooledConnection.class.getDeclaredField("usedCount");
			usedCountField.setAccessible(true);
			usedCountField.set(connection, 109);

			Field isClosedField = PooledConnection.class.getDeclaredField("isClosed");
			isClosedField.setAccessible(true);
			isClosedField.set(connection, false);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// Engaged, created at: Feb 11 01:02:03, last used: 00:08:00, usage: 109
		assertEquals("Engaged, created at: " + created.format(DateTimeFormatter.ofPattern("MMM d")) + " 01:02:03, last used: 00:08:00, usage: 109", connection.toString());
	}

	@Test
	public void testToString4() {
		PooledConnection connection = new PooledConnection(null);
		LocalDate today = LocalDate.now();
		LocalDateTime created = today.minusDays(2).atTime(1, 2, 3);
		LocalDateTime lastUsed = today.minusDays(1).atTime(0, 8, 0);
		try {
			Field createTimeField = PooledConnection.class.getDeclaredField("createdTime");
			createTimeField.setAccessible(true);
			createTimeField.set(connection, created);

			Field lastUseTimeField = PooledConnection.class.getDeclaredField("lastUsedTime");
			lastUseTimeField.setAccessible(true);
			lastUseTimeField.set(connection, lastUsed);

			Field usedCountField = PooledConnection.class.getDeclaredField("usedCount");
			usedCountField.setAccessible(true);
			usedCountField.set(connection, 109);

			Field isClosedField = PooledConnection.class.getDeclaredField("isClosed");
			isClosedField.setAccessible(true);
			isClosedField.set(connection, false);
		} catch (Exception e) {
			e.printStackTrace();
		}
		// Engaged, created at: Feb 11 01:02:03, last used: Feb 12 00:08:00, usage: 109
		assertEquals("Engaged, created at: " + created.format(DateTimeFormatter.ofPattern("MMM d")) + " 01:02:03, last used: " + lastUsed.format(DateTimeFormatter.ofPattern("MMM d")) + " 00:08:00, usage: 109", connection.toString());
	}
}