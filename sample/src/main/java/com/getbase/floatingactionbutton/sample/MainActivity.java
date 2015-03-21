package com.getbase.floatingactionbutton.sample;

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.widget.Toast;
import com.getbase.floatingactionbutton.FloatingActionsMenu;

public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final FloatingActionsMenu menu = (FloatingActionsMenu) findViewById(R.id.multiple_actions);
        menu.setOnActionsMenuItemClickListener(new FloatingActionsMenu.OnActionsMenuItemClickListener() {

            @Override
            public void onMainItemClick() {
                Toast.makeText(getApplicationContext(), "Main button clicked", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onSecondaryItemClick(int itemId) {
                String msg;
                switch (itemId) {
                    case R.id.action_a:
                        msg = "Action A clicked";
                        break;

                    case R.id.action_b:
                        msg = "Action B clicked";
                        break;

                    case R.id.action_c:
                        msg = "Action C clicked";
                        break;

                    default:
                        msg = "Unknown button clicked";
                        break;
                }
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
