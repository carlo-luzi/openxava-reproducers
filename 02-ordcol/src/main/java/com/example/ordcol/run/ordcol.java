package com.example.ordcol.run;

import org.openxava.util.*;

public class ordcol {

    public static void main(String[] args) throws Exception {
        DBServer.start("ordcol-db");
        AppServer.run("ordcol");
    }
}
