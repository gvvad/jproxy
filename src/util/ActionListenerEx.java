package util;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ActionListenerEx implements ActionListener {
    private Runnable func;

    public ActionListenerEx(Runnable func) {
        this.func = func;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        func.run();
    }
}
