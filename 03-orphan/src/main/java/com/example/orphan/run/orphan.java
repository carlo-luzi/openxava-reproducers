package com.example.orphan.run;

import org.openxava.util.*;

/**
 * Execute this class to start the application.
 */

public class orphan {

	public static void main(String[] args) throws Exception {
		DBServer.start("orphan-db"); // To use your own database comment this line and configure src/main/webapp/META-INF/context.xml
		AppServer.run("orphan"); // Use AppServer.run("") to run in root context
	}

}
