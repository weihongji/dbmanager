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

//	@Test
//	public void status1() throws SQLException, TimeoutException, ClassNotFoundException, InterruptedException {
//		Manager manager = new Manager();
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
//		assertEquals("No connection in the pool.", manager.status());
//	}

	@Test
	public void thread() throws InterruptedException {
		Manager manager = new Manager();
		manager.setMaxCount(3);
		manager.setMaxWaitForConnection(2);
		manager.setRetireAfterIdle(4);
		Thread.sleep(1000);
		List<Thread> list = new ArrayList<>();
		for (int i = 1; i <= 3; i++) {
			Thread t = new Thread(new MyThread(manager, i), "Thread #" + i);
			list.add(t);
			t.start();
			Thread.sleep(1000);
		}
		Thread t = new Thread(new MyThread(manager, 4), "Thread #" + 4);
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
			if (manager.count() == 0) {
				break;
			}
			Thread.sleep(30 * 1000);
		}

		for (Thread t1 : list) {
			t1.join();
		}
	}

	private class MyThread implements Runnable {
		private Manager manager;
		private int timeToKeep;

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
				e.printStackTrace();
			} finally {
				System.out.println(Thread.currentThread().getName() + " exiting.");
			}
		}
	}
}