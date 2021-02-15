package com.jesse.db;

import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

public class ManagerTest {

	@Test
	public void getConnection() {
		Manager manager = new Manager();
		Connection conn = null;
		try {
			conn = manager.getConnection();
			assertFalse(conn.isClosed());
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Test
	public void status() {
		Manager manager = new Manager();
		assertEquals("No connection in the pool.", manager.status());
	}

	@Test
	public void status1() throws SQLException, TimeoutException, ClassNotFoundException, InterruptedException {
		Manager manager = new Manager();
//		manager.setMinCount(6);
//		Connection conn = manager.getConnection();
//		Thread.sleep(1000);
//		conn.close();
//		Thread.sleep(1000);
//		Connection c1 = manager.getConnection();
//		Thread.sleep(1000);
//		Connection c2 = manager.getConnection();
//		Thread.sleep(1000);
//		c1.close();
//		Thread.sleep(1000);
//		manager.getConnection();
//		Thread.sleep(1000);
//		manager.getConnection();
//
//		Thread.sleep(1000);
//		manager.getConnection();
		assertEquals("No connection in the pool.", manager.status());
	}
}