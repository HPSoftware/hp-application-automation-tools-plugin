package com.hpe.application.automation.tools.octane;

import com.hpe.application.automation.tools.octane.events.EventsTest;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Purpose of this class is to provide Octane Server Mock for the tests of the Octane Plugin
 * The server will run on port that will be taken from configuration or default
 * The server will serve ALL of the tests, so each suite should carefully configure it to respond ot itself an not mess up with other suites
 */

public class OctaneServerMock {
	private static final Logger logger = Logger.getLogger(EventsTest.class.getName());
	private static final OctaneServerMock INSTANCE = new OctaneServerMock();

	private static final int DEFAULT_TESTING_SERVER_PORT = 9999;
	private static int testingServerPort = DEFAULT_TESTING_SERVER_PORT;

	private OctaneServerMock() {
		logger.log(Level.INFO, "starting initialization...");
		String p = System.getProperty("testingServerPort");
		try {
			if (p != null) {
				testingServerPort = Integer.parseInt(p);
			}
		} catch (NumberFormatException nfe) {
			logger.log(Level.WARNING, "bad port number found in the system properties, default port will be used");
		}

		try {
			Server server = new Server(testingServerPort);
			server.setHandler(new OctaneServerMockHandler());
			server.start();
			logger.log(Level.INFO, "SUCCESSFULLY started, listening on port " + testingServerPort);
		} catch (Throwable t) {
			logger.log(Level.SEVERE, "FAILED to start", t);
		}
	}

	private static final class OctaneServerMockHandler extends AbstractHandler {

		@Override
		public void handle(String s, Request request, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {

		}
	}
}
