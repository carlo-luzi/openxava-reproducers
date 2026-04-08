package com.example.ordcol.run;

import org.openxava.util.*;

public class DBManager {

    public static void main(String[] args) throws Exception {
        DBServer.start("ordcol-db");
    }
}
