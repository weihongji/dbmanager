package com.jesse.db;

import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ManagerTest {

	@Test
	public void getConnection() {
		Manager manager = new Manager(getConnector());
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
		manager.close();
	}

	@Test
	public void status() {
		Manager manager = new Manager(new Connector());
		assertEquals("No connection in the pool.", manager.status());
		manager.close();
	}

	@Test
	public void thread() throws InterruptedException {
		Manager manager = new Manager(getConnector());
		manager.setMaxSize(3);
		manager.setMaxWaitForConnection(2);
		manager.setRetireAfterIdle(4);

		try {
			// Initialize the pool before normal test. This job costs much time than others below.
			// To keep good order, we need to do this separately.
			Connection c = manager.getConnection();
			c.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		Thread.sleep(1000);

		List<Thread> list = new ArrayList<>();
		Thread t;
		for (int i = 1; i <= 3; i++) {
			t = new Thread(new MyThread(manager, i), "Thread #" + i);
			list.add(t);
			t.start();
			Thread.sleep(1000);
		}
		t = new Thread(new MyThread(manager, 4), "Thread #" + 4);
		list.add(t);
		t.start();
		Thread.sleep(1000);

		t = new Thread(new MyThread(manager, 3), "Thread #" + 5);
		list.add(t);
		t.start();
		Thread.sleep(1000);

		t = new Thread(new MyThread(manager, 3), "Thread #" + 6);
		list.add(t);
		t.start();
		Thread.sleep(1000);

		for (int i = 1; i <= 20; i++) {
			System.out.println(String.format("\n--- #%02d (%s) ---------------------------------------------------", i, LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))));
			System.out.println(manager.status());
			System.out.println("--------------------------------------------------------------------------\n");
			if (manager.size() == 0) {
				break;
			}
			Thread.sleep(30 * 1000);
		}

		for (Thread t1 : list) {
			t1.join();
		}

		manager.close();
		System.out.println("Thread test done!");
	}

	private Connector getConnector() {
		String driver, url, user, pwd;

		driver = "com.mysql.cj.jdbc.Driver";
		url = "jdbc:mysql://localhost:3306/test?serverTimezone=UTC&useLegacyDatetimeCode=false";
		user = "root";
		pwd = "@ctive123";

		/*
		driver = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
		url = "jdbc:sqlserver://localhost:1433;DatabaseName=Test";
		user = "recware";
		pwd = "safari";
		*/

		return new Connector(driver, url, user, pwd);
	}

	private class MyThread implements Runnable {
		private final Manager manager;
		private final int timeToKeep;

		public MyThread(Manager manager, int timeToKeep) {
			this.manager = manager;
			this.timeToKeep = timeToKeep;
		}

		@Override
		public void run() {
			try {
				System.out.println(Thread.currentThread().getName() + " starting.");
				Connection c = manager.getConnection();
				System.out.println(Thread.currentThread().getName() + " got connection.");
				Thread.sleep(this.timeToKeep * 60 * 1000);
				c.close();
				System.out.println(Thread.currentThread().getName() + " closed connection.");
			} catch (Exception e) {
				System.out.println(Thread.currentThread().getName() + " Failed to get a connection.");
				e.printStackTrace();
			} finally {
				System.out.println(Thread.currentThread().getName() + " exiting.");
			}
		}
	}
}