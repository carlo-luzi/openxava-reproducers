package com.example.elemcol.run;

import org.openxava.util.*;

/**
 * Execute this class to start the application.
 */

public class elemcol {

	public static void main(String[] args) throws Exception {
		DBServer.start("elemcol-db"); // To use your own database comment this line and configure src/main/webapp/META-INF/context.xml
		AppServer.run("elemcol"); // Use AppServer.run("") to run in root context
	}

}
