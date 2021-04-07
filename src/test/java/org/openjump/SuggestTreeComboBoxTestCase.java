package org.openjump;

import com.vividsolutions.jump.workbench.ui.SuggestTreeComboBox;


import javax.swing.*;

/**
 * Created by Michaël on 24/11/14.
 */
public class SuggestTreeComboBoxTestCase {

    public static void main(String[] args) {
        SuggestTreeComboBoxTestCase.test1();
    }

    public static void test1() {
        String[] array = new String[]{
                "Lambert93",
                "Lambert 2 étendu",
                "Lambert zone 1 NTF",
                "Lambert zone 2 NTF",
                "Lambert zone 3 NTF",
                "Lambert zone 4",
                "Lambert93 RGF 93",
                "2150",
                "2151",
                "2152",
                "2153",
                "2154",
                "2155",
                "2156",
                "2157",
                "2158",
                "2159",
                "2160",
                "RGF93",
                "Web Mercator",
                "RGF93 UTM 30N",
                "RGF93 UTM 30N",
                "NTF UTM 30N",
                "NTF UTM 30N",
        };
        SuggestTreeComboBox stcb = new SuggestTreeComboBox(array, 6);
        stcb.setPrototypeDisplayValue("abcdefghijklmnopqrstuvwxyz");
        JFrame frame = new JFrame("Test SuggestTreeComboBox");
        frame.add(stcb);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }
}
